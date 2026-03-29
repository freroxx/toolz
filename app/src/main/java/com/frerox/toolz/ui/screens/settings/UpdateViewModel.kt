package com.frerox.toolz.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    val preferredAbi = settingsRepository.preferredAbi.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO"
    )

    fun setPreferredAbi(abi: String) {
        viewModelScope.launch {
            settingsRepository.setPreferredAbi(abi)
        }
    }

    fun getDeviceAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            try {
                // Fetch latest release from GitHub
                val response = updateService.getLatestRelease(
                    UpdateConstants.GITHUB_OWNER,
                    UpdateConstants.GITHUB_REPO
                )
                
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        // Filter APKs that match our supported ABIs or are universal
                        val hasCompatibleApk = release.assets.any { asset ->
                            val name = asset.name.lowercase()
                            name.endsWith(".apk") && (
                                UpdateHelper.ABI_FILTERS.any { name.contains(it) } || 
                                name.contains("universal")
                            )
                        }
                        
                        if (hasCompatibleApk) {
                            _uiState.value = UpdateUiState.Success(release)
                            return@launch
                        }
                    }
                }

                // Fallback to manifest if GitHub Release API fails or has no compatible APKs
                val manifestResponse = updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
                if (manifestResponse.isSuccessful) {
                    val manifest = manifestResponse.body()
                    if (manifest != null && !manifest.releases.isNullOrEmpty()) {
                        _uiState.value = UpdateUiState.ManifestSuccess(manifest)
                        return@launch
                    }
                }

                _uiState.value = UpdateUiState.Error("No compatible update APKs found in the latest release")
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
