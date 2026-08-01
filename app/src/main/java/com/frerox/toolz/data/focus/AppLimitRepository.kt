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

    suspend fun deleteAllLimits() = appLimitDao.deleteAllLimits()
}
