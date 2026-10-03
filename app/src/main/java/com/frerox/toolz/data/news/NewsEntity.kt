package com.frerox.toolz.data.news

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "news_items")
data class NewsEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val actionLabel: String?,
    val actionUrl: String?,
    val priority: String,
    val status: String,
    val pinned: Boolean,
    val publishAt: Long,
    val expiresAt: Long?,
    val minAppVersion: String?,
    val maxAppVersion: String?,
    val onlyVersionsCsv: String,
    val excludedVersionsCsv: String,
    val delaySeconds: Int,
    val frequency: String,
    val intervalHours: Int?,
    val maxImpressions: Int?,
    val dismissible: Boolean,
    val showInHistory: Boolean,
    val requiresAction: Boolean,
    val notify: Boolean,
    val disappearing: Boolean,
    val receivedAt: Long
)

fun NewsDto.toEntity(now: Long = System.currentTimeMillis()): NewsEntity {
    fun epoch(iso: String?): Long? = iso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
    return NewsEntity(
        id = id,
        title = title.take(120),
        body = body.take(2000),
        imageUrl = imageUrl?.take(500),
        actionLabel = actionLabel?.take(50),
        actionUrl = actionUrl?.take(500),
        priority = priority,
        status = status,
        pinned = pinned,
        publishAt = epoch(publishAt) ?: now,
        expiresAt = epoch(expiresAt),
        minAppVersion = minAppVersion,
        maxAppVersion = maxAppVersion,
        onlyVersionsCsv = onlyVersions.joinToString(","),
        excludedVersionsCsv = excludedVersions.joinToString(","),
        delaySeconds = delaySeconds.coerceIn(0, 3600),
        frequency = frequency,
        intervalHours = intervalHours,
        maxImpressions = maxImpressions,
        dismissible = dismissible,
        showInHistory = showInHistory,
        requiresAction = requiresAction && priority == "critical",
        notify = notify,
        disappearing = disappearing,
        receivedAt = now
    )
}

@Dao
interface NewsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<NewsEntity>)

    @Query("SELECT * FROM news_items ORDER BY pinned DESC, publishAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<NewsEntity>

    @Query("SELECT COUNT(*) FROM news_items")
    suspend fun count(): Int

    @Query("SELECT * FROM news_items WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): NewsEntity?

    @Query("SELECT * FROM news_items WHERE status = 'published' ORDER BY pinned DESC, publishAt DESC")
    suspend fun publishedOrdered(): List<NewsEntity>

    @Query("UPDATE news_items SET status = 'archived' WHERE id IN (:ids)")
    suspend fun markArchived(ids: List<String>)

    @Query("DELETE FROM news_items WHERE (showInHistory = 0 AND expiresAt IS NOT NULL AND expiresAt <= :now) OR (disappearing = 1 AND expiresAt IS NOT NULL AND expiresAt <= :now) OR (expiresAt IS NOT NULL AND expiresAt <= :pruneBefore)")
    suspend fun prune(now: Long, pruneBefore: Long): Int
}
