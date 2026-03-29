package com.frerox.toolz.data.password

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY name ASC")
    fun getAllPasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE id = :id")
    suspend fun getPasswordById(id: Int): PasswordEntity?

    @Query("SELECT * FROM passwords WHERE url LIKE '%' || :domain || '%' OR name LIKE '%' || :domain || '%'")
    suspend fun getPasswordsByDomain(domain: String): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE name LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%'")
    suspend fun searchPasswords(query: String): List<PasswordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordEntity)

    @Update
    suspend fun updatePassword(password: PasswordEntity)

    @Query("UPDATE passwords SET pwnedCount = :count WHERE id = :id")
    suspend fun updatePwnedCount(id: Int, count: Int)

    @Delete
    suspend fun deletePassword(password: PasswordEntity)
}
