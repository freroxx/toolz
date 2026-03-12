package com.frerox.toolz.data.focus

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLimitRepository @Inject constructor(
    private val appLimitDao: AppLimitDao
) {
    val allLimits: Flow<List<AppLimit>> = appLimitDao.getAllLimits()

    suspend fun getLimitForApp(packageName: String): AppLimit? = appLimitDao.getLimitForApp(packageName)

    suspend fun setLimit(limit: AppLimit) = appLimitDao.insertLimit(limit)

    suspend fun removeLimit(limit: AppLimit) = appLimitDao.deleteLimit(limit)
}
