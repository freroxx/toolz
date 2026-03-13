package com.frerox.toolz.data.pdf

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations WHERE fileUri = :fileUri")
    fun getAnnotationsForFile(fileUri: String): Flow<List<PdfAnnotation>>

    @Query("SELECT * FROM pdf_annotations WHERE fileUri = :fileUri AND pageIndex IN (:pageIndices)")
    fun getAnnotationsForPages(fileUri: String, pageIndices: List<Int>): Flow<List<PdfAnnotation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: PdfAnnotation)

    @Delete
    suspend fun deleteAnnotation(annotation: PdfAnnotation)

    @Query("DELETE FROM pdf_annotations WHERE fileUri = :fileUri")
    suspend fun clearAnnotations(fileUri: String)
}
