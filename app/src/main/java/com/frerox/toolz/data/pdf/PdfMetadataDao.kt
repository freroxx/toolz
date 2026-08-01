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

    @Query("UPDATE pdf_metadata SET structuredOcrData = :structuredData WHERE uri = :uri")
    suspend fun updateStructuredOcrData(uri: String, structuredData: String)

    @Query("SELECT * FROM pdf_metadata")
    suspend fun getAllMetadataSync(): List<PdfMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataList(entries: List<PdfMetadata>)
}
