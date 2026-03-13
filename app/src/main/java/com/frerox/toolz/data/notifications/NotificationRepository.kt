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
