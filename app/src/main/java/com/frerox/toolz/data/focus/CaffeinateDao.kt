package com.frerox.toolz.data.focus

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "caffeinate_apps")
data class CaffeinateApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val isAutoEnabled: Boolean = false
)

@Dao
interface CaffeinateDao {
    @Query("SELECT * FROM caffeinate_apps")
    fun getAllApps(): Flow<List<CaffeinateApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<CaffeinateApp>)

    @Update
    suspend fun updateApp(app: CaffeinateApp)

    @Query("SELECT * FROM caffeinate_apps WHERE isAutoEnabled = 1")
    suspend fun getAutoEnabledApps(): List<CaffeinateApp>
    
    @Query("DELETE FROM caffeinate_apps")
    suspend fun deleteAll()
}
