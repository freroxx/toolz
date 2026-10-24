package com.frerox.toolz.news

import com.frerox.toolz.data.news.NewsDao
import com.frerox.toolz.data.news.NewsDto
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.data.news.toEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsReconciliationTest {

    private class FakeNewsDao : NewsDao {
        val items = mutableMapOf<String, NewsEntity>()

        override suspend fun upsertAll(items: List<NewsEntity>) {
            for (item in items) this.items[item.id] = item
        }

        override suspend fun page(limit: Int, offset: Int): List<NewsEntity> {
            return items.values
                .filter { it.status == "published" }
                .sortedWith(compareByDescending<NewsEntity> { it.pinned }.thenByDescending { it.publishAt })
                .drop(offset)
                .take(limit)
        }

        override suspend fun count(): Int = items.values.count { it.status == "published" }

        override suspend fun byId(id: String): NewsEntity? = items[id]

        override suspend fun publishedOrdered(): List<NewsEntity> {
            return items.values
                .filter { it.status == "published" }
                .sortedWith(compareByDescending<NewsEntity> { it.pinned }.thenByDescending { it.publishAt })
        }

        override suspend fun searchRaw(q: String, limit: Int): List<NewsEntity> {
            return items.values
                .filter { it.status == "published" && (it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true)) }
                .take(limit)
        }

        override suspend fun publishedIds(): List<String> = items.values.filter { it.status == "published" }.map { it.id }

        override suspend fun markArchived(ids: List<String>) {
            for (id in ids) {
                items[id]?.let { items[id] = it.copy(status = "archived") }
            }
        }

        override suspend fun deleteByIds(ids: List<String>) {
            for (id in ids) items.remove(id)
        }

        override suspend fun deleteStaleNonPublished(): Int {
            val toRemove = items.values.filter { it.status != "published" }.map { it.id }
            for (id in toRemove) items.remove(id)
            return toRemove.size
        }

        override suspend fun deleteAll(): Int {
            val count = items.size
            items.clear()
            return count
        }

        override suspend fun prune(now: Long, pruneBefore: Long): Int = 0
    }

    private fun sampleDto(id: String, title: String = "Title", priority: String = "info") = NewsDto(
        id = id,
        title = title,
        body = "Body",
        priority = priority,
        status = "published",
        pinned = false
    )

    @Test
    fun deleted_server_side_items_are_removed_from_cache_and_page() = runTest {
        val dao = FakeNewsDao()
        val now = System.currentTimeMillis()

        // 1. Initial sync: 2 items received from server
        val item1 = sampleDto("item1", "News 1").toEntity(now)
        val item2 = sampleDto("item2", "News 2").toEntity(now)
        dao.items[item1.id] = item1
        dao.items[item2.id] = item2
        assertEquals(2, dao.items.size)
        assertEquals(2, dao.page(10, 0).size)

        // 2. Server deletes item2: feed now only contains item1
        val feedIds = setOf("item1")
        val stale = dao.publishedOrdered().filter { it.id !in feedIds }.map { it.id }
        assertEquals(listOf("item2"), stale)

        // Reconciliation deletes stale items from Room
        dao.deleteByIds(stale)

        // Verify item2 is completely gone from DB and page()
        assertFalse(dao.items.containsKey("item2"))
        assertEquals(1, dao.page(10, 0).size)
        assertEquals("item1", dao.page(10, 0).first().id)
    }

    @Test
    fun all_deleted_server_side_clears_local_cache() = runTest {
        val dao = FakeNewsDao()
        val now = System.currentTimeMillis()

        val item1 = sampleDto("item1").toEntity(now)
        dao.items[item1.id] = item1
        assertEquals(1, dao.page(10, 0).size)

        // Server deleted all news: feedIds is empty
        val feedIds = emptySet<String>()
        val stale = dao.publishedOrdered().filter { it.id !in feedIds }.map { it.id }
        dao.deleteByIds(stale)

        assertTrue(dao.items.isEmpty())
        assertEquals(0, dao.page(10, 0).size)
    }

    @Test
    fun stale_archived_items_are_purged_by_deleteStaleNonPublished() = runTest {
        val dao = FakeNewsDao()
        val now = System.currentTimeMillis()

        val publishedItem = sampleDto("live").toEntity(now)
        val archivedItem = sampleDto("old").toEntity(now).copy(status = "archived")
        dao.items[publishedItem.id] = publishedItem
        dao.items[archivedItem.id] = archivedItem

        // page() and count() should only return published items
        assertEquals(1, dao.page(10, 0).size)
        assertEquals(1, dao.count())

        // Purge removes the archived item from database completely
        val removedCount = dao.deleteStaleNonPublished()
        assertEquals(1, removedCount)
        assertFalse(dao.items.containsKey("old"))
        assertTrue(dao.items.containsKey("live"))
    }
}
