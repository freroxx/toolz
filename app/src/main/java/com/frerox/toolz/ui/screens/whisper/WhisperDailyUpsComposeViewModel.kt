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
import com.frerox.toolz.data.whisper.WhisperDailyUpsPreferences
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.WhisperStoryAudience
import com.frerox.toolz.data.whisper.applyWhisperImageExifOrientation
import com.frerox.toolz.data.whisper.dailyUpErrorText
import com.frerox.toolz.data.whisper.dailyUpsPostedTodayOrNull
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.publishDailyUp
import com.frerox.toolz.data.whisper.quotaAfterRead
import com.frerox.toolz.data.whisper.saveDailyUpToGallery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    TooManyPeople,
    NoQuota,
    Busy,
}

/** How long a friend sync stays fresh while the composer stays alive. */
private const val FRIENDS_REFRESH_MS = 60_000L

internal fun dailyUpShareBlock(
    hasPhoto: Boolean,
    remaining: Int,
    audienceSize: Int,
    publishing: Boolean,
    published: Boolean,
    maxAudience: Int = WhisperDailyUpsConfig.MAX_RECIPIENTS,
): ShareBlock = when {
    publishing || published -> ShareBlock.Busy
    !hasPhoto -> ShareBlock.NoPhoto
    audienceSize <= 0 -> ShareBlock.NoAudience
    // The server hard-caps the audience; surface it BEFORE the expensive
    // preprocess/upload instead of failing after the photo was prepared.
    audienceSize > maxAudience -> ShareBlock.TooManyPeople
    remaining <= 0 -> ShareBlock.NoQuota
    else -> ShareBlock.None
}

/**
 * State for composing ONE Daily Up: photo draft → audience → share.
 *
 * The photo draft lives ONLY in memory; nothing touches disk until the
 * repository pipeline (which strips metadata before sealing). Quota reads are
 * refreshed right before publish so a stale "3 left" can never double-post —
 * the server is the ceiling regardless.
 *
 * Audience is expressed as an explicit recipient snapshot, so "everyone",
 * "close friends" and a hand-picked "custom" list all publish through the same
 * fail-closed path. The close-friends list itself never leaves the device.
 */
@HiltViewModel
class WhisperDailyUpsComposeViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val preferences: WhisperDailyUpsPreferences,
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
        val audienceKind: WhisperStoryAudience = WhisperStoryAudience.EVERYONE,
        /** Friend ids explicitly trimmed OUT of the everyone audience. */
        val excluded: Set<String> = emptySet(),
        /** Hand-picked ids for [WhisperStoryAudience.CUSTOM]. */
        val customSelected: Set<String> = emptySet(),
        /** Device-local close-friends list (ids may outlive a friendship). */
        val closeFriends: Set<String> = emptySet(),
        val receiptsOn: Boolean = true,
        val saveToDevice: Boolean = false,
        val quotaUsed: Int = 0,
        val publishing: Boolean = false,
        /** One-shot success marker; the screen dismisses on it. */
        val published: Boolean = false,
        val error: String? = null,
    ) {
        val remaining: Int get() = (WhisperDailyUpsConfig.DAILY_LIMIT - quotaUsed).coerceAtLeast(0)

        /** Ids that will actually receive this up (friends ∩ chosen kind). */
        val audienceIds: Set<String>
            get() = when (audienceKind) {
                WhisperStoryAudience.EVERYONE ->
                    friends.asSequence()
                        .map { it.id }
                        .filterNot { it in excluded }
                        .take(WhisperDailyUpsConfig.MAX_RECIPIENTS)
                        .toSet()

                WhisperStoryAudience.CLOSE_FRIENDS ->
                    friends.asSequence()
                        .map { it.id }
                        .filter { it in closeFriends }
                        .take(WhisperDailyUpsConfig.MAX_RECIPIENTS)
                        .toSet()

                WhisperStoryAudience.CUSTOM ->
                    friends.asSequence()
                        .map { it.id }
                        .filter { it in customSelected }
                        .take(WhisperDailyUpsConfig.MAX_RECIPIENTS)
                        .toSet()
            }

        val audienceSize: Int get() = audienceIds.size

        val closeAudienceSize: Int get() = friends.count { it.id in closeFriends }

        /** True when the friend list is longer than one Daily Up may reach. */
        val audienceCapped: Boolean get() = friends.size > WhisperDailyUpsConfig.MAX_RECIPIENTS

        /** True while the selection is above the server's hard cap. */
        val audienceOverLimit: Boolean
            get() = audienceSize > WhisperDailyUpsConfig.MAX_RECIPIENTS

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

    private val _state = MutableStateFlow(
        ComposeUiState(
            receiptsOn = preferences.showViewers.value,
            saveToDevice = preferences.saveToDevice.value,
            closeFriends = preferences.closeFriends.value,
        ),
    )
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    init {
        // These preferences are keyed per account; bind before the collectors
        // below emit, so an account switch never shows the previous list.
        preferences.bindCurrentAccount()
        viewModelScope.launch { loadFriends() }
        viewModelScope.launch {
            preferences.closeFriends.collect { ids ->
                _state.update { it.copy(closeFriends = ids) }
            }
        }
        viewModelScope.launch {
            preferences.showViewers.collect { on ->
                _state.update { it.copy(receiptsOn = on) }
            }
        }
        viewModelScope.launch {
            preferences.saveToDevice.collect { on ->
                _state.update { it.copy(saveToDevice = on) }
            }
        }
    }

    private suspend fun loadFriends(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(friendsLoading = it.friends.isEmpty()) }
        repository.getFriends()
            .onSuccess { loaded ->
                // Stable order so the capped default audience is deterministic
                // across devices and runs (the DB row order is not).
                val friends = loaded.sortedWith(
                    compareBy({ it.effectiveName.lowercase() }, { it.id }),
                )
                // A Daily Up may reach at most MAX_RECIPIENTS people. Rather
                // than letting Share fail after the upload, default to the
                // first capped slice and say so in the audience row; Edit lets
                // the user swap people in/out. Never silently drop anyone:
                // [audienceCapped] drives an explicit note.
                val cap = WhisperDailyUpsConfig.MAX_RECIPIENTS
                val capExclusions = if (friends.size > cap) {
                    friends.drop(cap).mapTo(mutableSetOf()) { it.id }
                } else {
                    emptySet()
                }
                val usedToday = repository.dailyUpsPostedTodayOrNull()
                friendsLoadedAtMs = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        friends = friends,
                        friendsLoading = false,
                        friendsFailed = false,
                        excluded = it.excluded + capExclusions,
                        // Null = read failed: keep whatever we showed before.
                        quotaUsed = quotaAfterRead(it.quotaUsed, usedToday),
                    )
                }
            }
            .onFailure { e ->
                // A background refresh that fails must not replace a usable
                // list (or the audience the user already picked) with an error.
                _state.update {
                    if (it.friends.isNotEmpty()) {
                        it.copy(friendsLoading = false)
                    } else {
                        it.copy(
                            friendsLoading = false,
                            friendsFailed = true,
                            error = dailyUpErrorText(appContext, e),
                        )
                    }
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
            // Failed read must not look like "nothing posted today".
            val used = repository.dailyUpsPostedTodayOrNull()
            _state.update { it.copy(quotaUsed = quotaAfterRead(it.quotaUsed, used)) }
        }
    }

    private var photoDecodeJob: Job? = null

    /** Epoch ms of the last successful friend sync; 0 = never. */
    private var friendsLoadedAtMs = 0L

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

    fun setAudienceKind(kind: WhisperStoryAudience) {
        _state.update {
            when (kind) {
                // Entering custom with nothing picked starts from the close
                // friends list when there is one; otherwise the picker opens
                // empty and the Share button explains why.
                WhisperStoryAudience.CUSTOM -> it.copy(
                    audienceKind = kind,
                    customSelected = if (it.customSelected.isEmpty()) {
                        it.closeFriends.intersect(it.friends.map { f -> f.id }.toSet())
                    } else {
                        it.customSelected
                    },
                )

                else -> it.copy(audienceKind = kind)
            }
        }
    }

    fun setReceipts(on: Boolean) {
        preferences.setShowViewers(on)
        _state.update { it.copy(receiptsOn = on) }
    }

    fun setSaveToDevice(on: Boolean) {
        preferences.setSaveToDevice(on)
        _state.update { it.copy(saveToDevice = on) }
    }

    fun setCloseFriend(friendId: String, close: Boolean) {
        preferences.setCloseFriend(friendId, close)
    }

    fun toggleCloseFriend(friendId: String) {
        preferences.toggleCloseFriend(friendId)
    }

    /** Toggle one hand-picked recipient in the custom audience. */
    fun toggleAudience(friendId: String) {
        _state.update {
            val included = friendId in it.customSelected
            when {
                // Re-including: refuse past the server cap and say why.
                !included && it.customSelected.size >= WhisperDailyUpsConfig.MAX_RECIPIENTS ->
                    it.copy(
                        error = appContext.getString(
                            R.string.st_Whisper_DailyUp_AudienceLimitReached,
                            WhisperDailyUpsConfig.MAX_RECIPIENTS,
                        ),
                    )
                included -> it.copy(customSelected = it.customSelected - friendId)
                else -> it.copy(customSelected = it.customSelected + friendId)
            }
        }
    }

    /** Select as many friends as the server allows, never more. */
    fun selectAllAudience() {
        _state.update {
            val cap = WhisperDailyUpsConfig.MAX_RECIPIENTS
            it.copy(customSelected = it.friends.take(cap).map { friend -> friend.id }.toSet())
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Called when the composer opens: a previous publish must not re-fire. */
    fun begin() {
        // Re-bind here as well: a cold start can open the composer before the
        // session restore finishes, and the id must not stay "anonymous".
        preferences.bindCurrentAccount()
        _state.update { it.copy(published = false, error = null) }
        refreshQuota()
        // The composer view model outlives a single opening (it is scoped to
        // the hub entry), so a friend added or removed since the last open
        // would otherwise stay invisible until process death.
        val current = _state.value
        if (current.friendsFailed ||
            System.currentTimeMillis() - friendsLoadedAtMs > FRIENDS_REFRESH_MS
        ) {
            viewModelScope.launch { loadFriends(silent = current.friends.isNotEmpty()) }
        }
    }

    /** Called after dismiss: drop the photo but keep audience/settings. */
    fun resetDraft() {
        photoDecodeJob?.cancel()
        _state.update {
            it.copy(
                photo = null,
                publishing = false,
                published = false,
                error = null,
            )
        }
    }

    /**
     * Publishes through the repository fail-closed pipeline. On success the
     * composer closes via [ComposeUiState.published]; on failure the draft
     * stays (photo + audience + settings all survive so nothing is retyped).
     */
    fun publish() {
        val current = _state.value
        val photo = current.photo ?: return
        if (current.publishing || current.published) return
        if (current.remaining <= 0 || current.audienceSize <= 0) return
        val audience = current.audienceIds
        val kind = current.audienceKind
        viewModelScope.launch {
            _state.update { it.copy(publishing = true, error = null) }
            repository.publishDailyUp(
                photoBytes = photo.bytes,
                mimeType = photo.mime,
                receiptsEnabled = current.receiptsOn,
                audienceIds = audience,
                audienceKind = kind,
            )
                .onSuccess {
                    if (current.saveToDevice) {
                        // Best effort: a gallery write must never turn a
                        // successful publish into an error.
                        withContext(Dispatchers.IO) {
                            runCatching {
                                saveDailyUpToGallery(appContext, photo.bytes, photo.mime)
                            }.onFailure { e ->
                                android.util.Log.w(
                                    "WhisperDailyUps",
                                    "could not save daily up to the gallery: ${e.message}",
                                )
                            }
                        }
                    }
                    _state.update { it.copy(publishing = false, published = true) }
                }
                .onFailure { e ->
                    // Server quota may have moved while composing — re-read it,
                    // but never downgrade a known count to "unknown as zero".
                    val usedToday = repository.dailyUpsPostedTodayOrNull()
                    _state.update {
                        it.copy(
                            publishing = false,
                            error = dailyUpErrorText(appContext, e),
                            quotaUsed = quotaAfterRead(it.quotaUsed, usedToday),
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
