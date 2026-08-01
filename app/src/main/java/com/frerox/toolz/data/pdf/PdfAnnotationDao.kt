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
    @Query("SELECT * FROM pdf_annotations")
    suspend fun getAllAnnotationsSync(): List<PdfAnnotation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(entries: List<PdfAnnotation>)
}
