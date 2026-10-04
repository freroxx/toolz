/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Phase-2 groups: LOCAL cache of the server event log + derived membership.
 *
 * These tables are a pure client-side convenience cache — the server log
 * (`whisper_group_events`) is the authority, and [applyEvents] in
 * WhisperRepositoryGroups.kt re-derives membership from verified events on
 * every sync. A stale or wiped cache only costs a re-sync, never trust:
 * rows are overwritten from verified events, never read blindly.
 *
 * Table names carry the `_local` suffix so they can never collide with a
 * server-table name in shared SQL or logs.
 */
@Entity(
    tableName = "whisper_groups_local",
)
data class WhisperGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val epoch: Long,
    val createdBy: String,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "whisper_group_members_local",
    primaryKeys = ["groupId", "memberId"],
    indices = [
        Index(value = ["groupId"]),
    ],
)
data class WhisperGroupMemberEntity(
    val groupId: String,
    val memberId: String,
    val role: String,
    val invitedBy: String?,
)

@Entity(
    tableName = "whisper_group_events_local",
    indices = [
        Index(value = ["groupId", "seq"]),
    ],
)
data class WhisperGroupEventEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val seq: Long,
    val epoch: Long,
    val type: String,
    val actor: String,
    val payload: String,
    val adminSig: String,
    val createdAtMs: Long,
)

@Dao
interface WhisperGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: WhisperGroupEntity)

    @Query("SELECT * FROM whisper_groups_local WHERE id = :groupId")
    suspend fun group(groupId: String): WhisperGroupEntity?

    @Query("SELECT * FROM whisper_groups_local ORDER BY updatedAtMs DESC")
    suspend fun allGroups(): List<WhisperGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<WhisperGroupMemberEntity>)

    @Query("SELECT * FROM whisper_group_members_local WHERE groupId = :groupId")
    suspend fun members(groupId: String): List<WhisperGroupMemberEntity>

    @Query("DELETE FROM whisper_group_members_local WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<WhisperGroupEventEntity>)

    @Query("SELECT * FROM whisper_group_events_local WHERE groupId = :groupId ORDER BY seq ASC")
    suspend fun events(groupId: String): List<WhisperGroupEventEntity>

    @Query("DELETE FROM whisper_groups_local WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM whisper_group_events_local WHERE groupId = :groupId")
    suspend fun clearEvents(groupId: String)

    /** Full cache wipe (account-delete/sign-out): all three group tables. */
    @Query("DELETE FROM whisper_groups_local")
    suspend fun clearAllGroups()

    @Query("DELETE FROM whisper_group_members_local")
    suspend fun clearAllMembers()

    @Query("DELETE FROM whisper_group_events_local")
    suspend fun clearAllEvents()
}
