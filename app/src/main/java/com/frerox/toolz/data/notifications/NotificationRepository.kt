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

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao
) {
    val allNotifications: Flow<List<NotificationEntry>> = notificationDao.getNonSpamNotifications()

    suspend fun insert(notification: NotificationEntry) = notificationDao.insert(notification)

    suspend fun deleteById(id: Long) = notificationDao.deleteById(id)

    suspend fun deleteAll() = notificationDao.deleteAll()

    suspend fun clearOldNotifications(days: Int) {
        val threshold = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        notificationDao.deleteOldNotifications(threshold)
    }

    suspend fun getNotificationCountForPackage(packageName: String): Int = notificationDao.getNotificationCountForPackage(packageName)

    suspend fun getLastNotificationForPackage(packageName: String): NotificationEntry? = notificationDao.getLastNotificationForPackage(packageName)

    fun getDistinctPackages(): Flow<List<String>> = notificationDao.getDistinctPackages()
}
