/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.applyWhisperImageExifOrientation
import com.frerox.toolz.data.whisper.dailyUpErrorText
import com.frerox.toolz.data.whisper.dailyUpsPostedToday
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.publishDailyUp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Why the pinned Share button cannot fire. Priority is deliberate and pinned
 * by unit tests: an in-flight/successful publish is busy regardless of the
 * draft; a missing photo is the first thing the reader must fix; then an empty
 * audience; quota is last because it is the only server-owned ceiling.
 */
internal enum class ShareBlock {
    None,
    NoPhoto,
    NoAudience,
    NoQuota,
    Busy,
}

internal fun dailyUpShareBlock(
    hasPhoto: Boolean,
    remaining: Int,
    audienceSize: Int,
    publishing: Boolean,
    published: Boolean,
): ShareBlock = when {
    publishing || published -> ShareBlock.Busy
    !hasPhoto -> ShareBlock.NoPhoto
    audienceSize <= 0 -> ShareBlock.NoAudience
    remaining <= 0 -> ShareBlock.NoQuota
    else -> ShareBlock.None
}

/**
 * State for composing ONE Daily Up: photo draft → audience trim → share.
 *
 * The photo draft lives ONLY in memory; nothing touches disk until the
 * repository pipeline (which strips metadata before sealing). Quota reads are
 * refreshed right before publish so a stale "3 left" can never double-post —
 * the server is the ceiling regardless.
 */
@HiltViewModel
class WhisperDailyUpsComposeViewModel @Inject constructor(
    private val repository: WhisperRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    /** In-memory photo draft (bytes + chosen mime + display preview). */
    data class PhotoDraft(
        val bytes: ByteArray,
        val mime: String,
        val preview: ImageBitmap,
    )

    data class ComposeUiState(
        val friends: List<WhisperProfile> = emptyList(),
        val friendsLoading: Boolean = true,
        /** Distinguishes "no friends yet" from "the load failed" (retry row). */
        val friendsFailed: Boolean = false,
        val photo: PhotoDraft? = null,
        val receiptsOn: Boolean = false,
        /** Friend ids explicitly trimmed OUT of the all-friends audience. */
        val excluded: Set<String> = emptySet(),
        val quotaUsed: Int = 0,
        val publishing: Boolean = false,
        /** One-shot success marker; the screen navigates back on it. */
        val published: Boolean = false,
        val error: String? = null,
    ) {
        val remaining: Int get() = (WhisperDailyUpsConfig.DAILY_LIMIT - quotaUsed).coerceAtLeast(0)
        val audienceSize: Int get() = maxOf(friends.size - excluded.size, 0)

        /** A live draft worth protecting from an accidental back gesture. */
        val hasDraft: Boolean get() = photo != null && !published

        internal val shareBlock: ShareBlock
            get() = dailyUpShareBlock(
                hasPhoto = photo != null,
                remaining = remaining,
                audienceSize = audienceSize,
                publishing = publishing,
                published = published,
            )
    }

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadFriends() }
    }

    private suspend fun loadFriends() {
        repository.getFriends()
            .onSuccess { friends ->
                _state.update {
                    it.copy(
                        friends = friends,
                        friendsLoading = false,
                        friendsFailed = false,
                        quotaUsed = repository.dailyUpsPostedToday(),
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        friendsLoading = false,
                        friendsFailed = true,
                        error = dailyUpErrorText(appContext, e),
                    )
                }
            }
    }

    /** Manual retry for the friends load (surfaced next to the audience). */
    fun retryFriends() {
        viewModelScope.launch {
            _state.update { it.copy(friendsLoading = true) }
            loadFriends()
        }
    }

    fun refreshQuota() {
        viewModelScope.launch {
            _state.update { it.copy(quotaUsed = repository.dailyUpsPostedToday()) }
        }
    }

    private var photoDecodeJob: Job? = null

    /** Reads the picker's photo into an in-memory draft + downsampled preview. */
    fun setPhoto(bytes: ByteArray, mime: String) {
        // Cancel any decode in flight: picking twice in quick succession must
        // NEVER let the slower FIRST decode overwrite the later pick.
        photoDecodeJob?.cancel()
        photoDecodeJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { decodePreview(bytes) }
            if (preview == null) {
                _state.update {
                    it.copy(error = appContext.getString(R.string.st_Whisper_DailyUp_PickFailed))
                }
                return@launch
            }
            _state.update {
                it.copy(photo = PhotoDraft(bytes = bytes, mime = mime, preview = preview))
            }
        }
    }

    fun clearPhoto() {
        _state.update { it.copy(photo = null) }
    }

    fun setReceipts(on: Boolean) {
        _state.update { it.copy(receiptsOn = on) }
    }

    fun toggleAudience(friendId: String) {
        _state.update {
            it.copy(
                excluded = if (friendId in it.excluded) {
                    it.excluded - friendId
                } else {
                    it.excluded + friendId
                },
            )
        }
    }

    /** Restore the all-friends audience from the sheet's Select all action. */
    fun selectAllAudience() {
        _state.update { it.copy(excluded = emptySet()) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Publishes through the repository fail-closed pipeline. On success the
     * screen closes via [ComposeUiState.published]; on failure the draft stays
     * (photo + toggle + exclusions all survive so nothing is retyped).
     */
    fun publish() {
        val current = _state.value
        val photo = current.photo ?: return
        if (current.publishing || current.published) return
        if (current.remaining <= 0 || current.audienceSize <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(publishing = true, error = null) }
            repository.publishDailyUp(
                photoBytes = photo.bytes,
                mimeType = photo.mime,
                receiptsEnabled = current.receiptsOn,
                excludedFriendIds = current.excluded,
            )
                .onSuccess {
                    _state.update { it.copy(publishing = false, published = true) }
                }
                .onFailure { e ->
                    // Server quota may have moved while composing — re-read it.
                    _state.update {
                        it.copy(
                            publishing = false,
                            error = dailyUpErrorText(appContext, e),
                            quotaUsed = repository.dailyUpsPostedToday(),
                        )
                    }
                }
        }
    }

    /**
     * Preview decode bounded to ~720px long edge — the publish pipeline
     * re-encodes anyway. The preview is ORIENTED exactly like the pipeline
     * ([applyWhisperImageExifOrientation] on the same bytes): a plain bitmap decode
     * ignores EXIF rotation, so compose used to show landscape camera shots
     * sideways while recipients saw them upright.
     */
    private fun decodePreview(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 720 || bounds.outHeight / (sample * 2) >= 720) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        val oriented = applyWhisperImageExifOrientation(bytes, bmp)
        if (oriented !== bmp) bmp.recycle()
        return oriented.asImageBitmap()
    }
}
