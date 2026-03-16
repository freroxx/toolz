package com.frerox.toolz.data.todo

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class TodoConverters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val subTaskListType = Types.newParameterizedType(List::class.java, SubTask::class.java)
    private val adapter = moshi.adapter<List<SubTask>>(subTaskListType)

    @TypeConverter
    fun fromSubTaskList(value: List<SubTask>): String {
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toSubTaskList(value: String): List<SubTask>? {
        return adapter.fromJson(value)
    }
}
