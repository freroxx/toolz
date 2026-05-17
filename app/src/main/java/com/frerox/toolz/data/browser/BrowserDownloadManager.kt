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
