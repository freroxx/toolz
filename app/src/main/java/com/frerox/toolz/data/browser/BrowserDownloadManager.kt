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

package com.frerox.toolz.data.browser

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadItem(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val progress: Float,
    val status: Int, // DownloadManager.STATUS_*
    val totalSize: Long,
    val mimeType: String?
)

@Singleton
class BrowserDownloadManager @Inject constructor(
    application: Application
) {
    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .setTitle(fileName)
            .setDescription("Downloading file...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Toolz/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadManager.enqueue(request)
        refreshDownloads()
    }

    fun refreshDownloads() {
        val toolzDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Toolz")
        if (!toolzDir.exists()) toolzDir.mkdirs()

        val files = toolzDir.listFiles() ?: emptyArray()
        val items = files.mapIndexed { index, file ->
            DownloadItem(
                id = index.toLong(),
                fileName = file.name,
                filePath = file.absolutePath,
                progress = 1f,
                status = DownloadManager.STATUS_SUCCESSFUL,
                totalSize = file.length(),
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            )
        }.sortedByDescending { File(it.filePath).lastModified() }
        
        _downloads.value = items
    }

    fun deleteDownload(item: DownloadItem) {
        val file = File(item.filePath)
        if (file.exists()) {
            file.delete()
        }
        refreshDownloads()
    }
}
