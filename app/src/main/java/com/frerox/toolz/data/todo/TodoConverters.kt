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

package com.frerox.toolz.data.todo

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class TodoConverters {
    @TypeConverter
    fun fromSubTaskList(value: List<SubTask>): String {
        return try {
            sharedAdapter.toJson(value)
        } catch (e: Exception) {
            android.util.Log.e("TodoConverters", "subtask serialize failed, storing []", e)
            "[]"
        }
    }

    @TypeConverter
    fun toSubTaskList(value: String?): List<SubTask> {
        // T-P0-04: nullable + throwing (Room null -> NPE at count/isNotEmpty,
        // corrupt JSON crashed the whole list). Never throw: log + [].
        if (value.isNullOrBlank()) return emptyList()
        return try {
            sharedAdapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("TodoConverters", "corrupt subtask JSON, row shows no subtasks", e)
            emptyList()
        }
    }

    companion object {
        // Singleton adapter (was: new Moshi per converter instance).
        private val sharedAdapter: com.squareup.moshi.JsonAdapter<List<SubTask>> by lazy(
            LazyThreadSafetyMode.SYNCHRONIZED
        ) {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, SubTask::class.java)
            moshi.adapter<List<SubTask>>(type)
        }
    }
}
