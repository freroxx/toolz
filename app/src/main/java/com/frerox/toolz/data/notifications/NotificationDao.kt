package com.frerox.toolz.data.notifications

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntry>>

    @Query("SELECT * FROM notifications WHERE isSpam = 0 ORDER BY timestamp DESC")
    fun getNonSpamNotifications(): Flow<List<NotificationEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntry)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM notifications WHERE timestamp < :threshold")
    suspend fun deleteOldNotifications(threshold: Long)
    
    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND title = :title AND text = :text ORDER BY timestamp DESC LIMIT 1")
    suspend fun findDuplicate(packageName: String, title: String?, text: String?): NotificationEntry?

    @Query("SELECT COUNT(*) FROM notifications WHERE packageName = :packageName")
    suspend fun getNotificationCountForPackage(packageName: String): Int

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastNotificationForPackage(packageName: String): NotificationEntry?

    @Query("SELECT DISTINCT packageName FROM notifications")
    fun getDistinctPackages(): Flow<List<String>>

    @Query("SELECT * FROM notifications")
    suspend fun getAllNotificationsSync(): List<NotificationEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(entries: List<NotificationEntry>)
}
