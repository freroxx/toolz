package com.frerox.toolz.data.pdf

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfMetadataDao {
    @Query("SELECT * FROM pdf_metadata WHERE uri = :uri")
    suspend fun getMetadata(uri: String): PdfMetadata?

    @Query("SELECT * FROM pdf_metadata WHERE uri = :uri")
    fun getMetadataFlow(uri: String): Flow<PdfMetadata?>

    @Query("SELECT * FROM pdf_metadata")
    fun getAllMetadata(): Flow<List<PdfMetadata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: PdfMetadata)

    @Query("UPDATE pdf_metadata SET isPinned = :isPinned WHERE uri = :uri")
    suspend fun updatePinned(uri: String, isPinned: Boolean)

    @Query("UPDATE pdf_metadata SET ocrContent = :ocrContent WHERE uri = :uri")
    suspend fun updateOcrContent(uri: String, ocrContent: String)
}
