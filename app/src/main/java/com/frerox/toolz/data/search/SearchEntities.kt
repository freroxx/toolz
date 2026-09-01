/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.search

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A search query the user has run. [query] is unique — repeating a search
 * bumps [timestamp] on the existing row via [SearchDao.recordHistory] rather
 * than inserting a duplicate, so the recent-history list doesn't fill up with
 * repeats of the same term.
 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)],
)
data class SearchHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
                              val query: String,
                              val timestamp: Long = System.currentTimeMillis(),
)

/** A saved page. [url] is unique so the same page can't be bookmarked twice. */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)],
)
data class BookmarkEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
                         val title: String,
                         val url: String,
                         val timestamp: Long = System.currentTimeMillis(),
)

/** A user-pinned shortcut, ordered by [sortOrder] then recency. */
@Entity(tableName = "quick_links")
data class QuickLinkEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
                          val title: String,
                          val url: String,
                          val timestamp: Long = System.currentTimeMillis(),
                          val sortOrder: Int = 0,
)

@Dao
interface SearchDao {

    // ─── History ────────────────────────────────────────────────────────────

    /**
     * Records a search. If [query] was already searched before, this bumps its
     * timestamp to now instead of inserting a duplicate row — use this rather
     * than [insertHistory] directly for user-initiated searches.
     */
    @Query("""
    INSERT INTO search_history (query, timestamp) VALUES (:query, :timestamp)
    ON CONFLICT(query) DO UPDATE SET timestamp = :timestamp
    """)
    suspend fun recordHistory(query: String, timestamp: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: SearchHistoryEntry)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<SearchHistoryEntry>>

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM search_history")
    suspend fun getAllHistorySync(): List<SearchHistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(entries: List<SearchHistoryEntry>)

    // ─── Bookmarks ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntry)

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getBookmarks(): Flow<List<BookmarkEntry>>

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean

    @Query("UPDATE bookmarks SET title = :title, url = :url WHERE id = :id")
    suspend fun updateBookmark(id: Long, title: String, url: String)

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksSync(): List<BookmarkEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(entries: List<BookmarkEntry>)

    // ─── Quick links ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickLink(quickLink: QuickLinkEntry)

    @Query("SELECT * FROM quick_links ORDER BY sortOrder ASC, timestamp DESC")
    fun getQuickLinks(): Flow<List<QuickLinkEntry>>

    @Query("DELETE FROM quick_links WHERE id = :id")
    suspend fun deleteQuickLink(id: Long)

    @Query("UPDATE quick_links SET title = :title, url = :url WHERE id = :id")
    suspend fun updateQuickLink(id: Long, title: String, url: String)

    @Update
    suspend fun updateQuickLinks(entries: List<QuickLinkEntry>)

    @Query("SELECT * FROM quick_links")
    suspend fun getAllQuickLinksSync(): List<QuickLinkEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickLinks(entries: List<QuickLinkEntry>)
}
