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
