package com.frerox.toolz.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.update.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateService: UpdateService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            try {
                // Try fetching manifest first
                val manifestResponse = updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
                if (manifestResponse.isSuccessful) {
                    val manifest = manifestResponse.body()
                    if (manifest != null && !manifest.apkUrl.isNullOrEmpty()) {
                        _uiState.value = UpdateUiState.ManifestSuccess(manifest)
                        return@launch
                    }
                }

                // Fallback to GitHub Release API
                val response = updateService.getLatestRelease(
                    UpdateConstants.GITHUB_OWNER,
                    UpdateConstants.GITHUB_REPO
                )
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        val hasApk = release.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                        if (hasApk) {
                            _uiState.value = UpdateUiState.Success(release)
                        } else {
                            _uiState.value = UpdateUiState.Error("No APK identified in the latest release")
                        }
                    } else {
                        _uiState.value = UpdateUiState.Error("No release information found")
                    }
                } else {
                    _uiState.value = UpdateUiState.Error("Update check failed (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(e.message ?: "Network error occurred")
            }
        }
    }

    private var downloadJob: Job? = null

    fun startDownload(url: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Toolz Update")
            .setDescription("Downloading latest build...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "toolz_update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // Delete existing file if present
        val existingFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "toolz_update.apk")
        if (existingFile.exists()) existingFile.delete()

        val downloadId = downloadManager.enqueue(request)
        
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            var isDownloading = true
            while (isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val totalBytes = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        _uiState.value = UpdateUiState.Downloading(100f)
                        isDownloading = false
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        _uiState.value = UpdateUiState.Error("Download failed")
                        isDownloading = false
                    } else if (totalBytes > 0) {
                        val progress = (bytesDownloaded.toFloat() / totalBytes.toFloat()) * 100f
                        _uiState.value = UpdateUiState.Downloading(progress)
                    }
                }
                cursor.close()
                delay(500)
            }
        }
    }
    
    fun resetState() {
        downloadJob?.cancel()
        _uiState.value = UpdateUiState.Idle
    }
}

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class Downloading(val progress: Float) : UpdateUiState()
    data class Success(val release: GitHubRelease) : UpdateUiState()
    data class ManifestSuccess(val manifest: UpdateManifest) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
