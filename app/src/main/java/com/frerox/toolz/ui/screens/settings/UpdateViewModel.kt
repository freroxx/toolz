package com.frerox.toolz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.update.GitHubRelease
import com.frerox.toolz.data.update.UpdateConstants
import com.frerox.toolz.data.update.UpdateManifest
import com.frerox.toolz.data.update.UpdateService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateService: UpdateService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            try {
                // Try fetching manifest first as it's the preferred method provided by the user
                val manifestResponse = updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
                if (manifestResponse.isSuccessful) {
                    val manifest = manifestResponse.body()
                    if (manifest != null) {
                        _uiState.value = UpdateUiState.ManifestSuccess(manifest)
                        return@launch
                    }
                }

                // Fallback to GitHub Release API if manifest fails (with fixed owner freroxx)
                val response = updateService.getLatestRelease(
                    UpdateConstants.GITHUB_OWNER,
                    UpdateConstants.GITHUB_REPO
                )
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        _uiState.value = UpdateUiState.Success(release)
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
    
    fun resetState() {
        _uiState.value = UpdateUiState.Idle
    }
}

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class Success(val release: GitHubRelease) : UpdateUiState()
    data class ManifestSuccess(val manifest: UpdateManifest) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
