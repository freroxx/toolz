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

package com.frerox.toolz.ui.screens.whisper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

import com.frerox.toolz.data.settings.SettingsRepository

@HiltViewModel
class WhisperChatViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val crypto: WhisperCrypto,
    private val notificationManager: WhisperNotificationManager,
    private val mutePrefs: WhisperMutePreferences,
    private val hiddenChatsStore: WhisperHiddenChatsStore,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val otherUserId: String = checkNotNull(savedStateHandle["otherUserId"])
    val myUserId: String get() = authManager.currentUserId ?: ""

    val screenshotBypassEnabled: StateFlow<Boolean> = settingsRepository.whisperScreenshotBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setScreenshotBypass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWhisperScreenshotBypass(enabled)
        }
    }

    private val _uiState = MutableStateFlow(WhisperChatUiState())
    val uiState: StateFlow<WhisperChatUiState> = _uiState.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _undoState = MutableStateFlow(WhisperUndoUiState())
    val undoState: StateFlow<WhisperUndoUiState> = _undoState.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    private var partnerPublicKey: String? = null
    private var realtimeJob: Job? = null
    private var typingSubscriptionJob: Job? = null
    private var typingDebounceJob: Job? = null
    private var presenceJob: Job? = null
    private var undoTimerJob: Job? = null
    private val reactionSyncJobs = mutableMapOf<String, Job>()
    private var messagesCollectionJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var isCurrentlyTyping = false

    private val deletedMessagesUndoBuffer = mutableListOf<WhisperMessage>()
    private var pendingIdCounter = 0L
    // Per-message in-flight reactions prevent double-tap races against the server.
    private val pendingReactions = mutableMapOf<String, MutableSet<String>>()

    companion object {
        private const val MAX_DECRYPTED_IMAGES = 20
    }

    init {
        notificationManager.currentChatId = otherUserId
        notificationManager.cancelMessageNotification(otherUserId)
        hiddenChatsStore.unhideChat(otherUserId)
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(otherUserId)) }
        // Keep the mute flag in sync when a timed mute expires while the chat is open.
        viewModelScope.launch {
            mutePrefs.mutedUsers.collect { muted ->
                _uiState.update { it.copy(isMuted = otherUserId in muted) }
            }
        }
        loadInitialData()
        subscribeToChat()
        subscribeToTyping()
        subscribeToPresence()
        sendPresenceSignal(true)
    }

    private fun handleError(err: Throwable, context: String) {
        val mapped = WhisperErrorMapper.map(err, context)
        if (WhisperErrorMapper.isSessionExpired(err)) {
            viewModelScope.launch {
                authManager.signOut()
                _sessionExpired.emit(Unit)
            }
        } else {
            _uiState.update { it.copy(error = mapped) }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Load other user's profile
            repository.getProfile(otherUserId, forceRefresh = true)
                .onSuccess { profile ->
                    partnerPublicKey = profile.publicKey
                    _uiState.update { it.copy(otherUser = profile) }
                }
                .onFailure { err ->
                    handleError(err, "getProfile")
                }

            // 2. Load friendship status
            repository.getFriendshipStatus(otherUserId)
                .onSuccess { (status, friendship) ->
                    _uiState.update {
                        it.copy(
                            friendStatus = status,
                            iAmRequester = friendship?.iRequested(myUserId) ?: false,
                            isFriendStatusLoaded = true,
                        )
                    }
                }
                .onFailure { err ->
                    handleError(err, "getFriendshipStatus")
                    _uiState.update { it.copy(isFriendStatusLoaded = true) }
                }

            // 3. Load block status
            val (blockedByMe, blockedByOther) = repository.getBlockStatus(otherUserId)
            _uiState.update { it.copy(isBlockedByMe = blockedByMe, isBlockedByOther = blockedByOther) }

            // 4. Load key trust status (fingerprints + key-change detection)
            loadKeyTrust()

            // 5. Load messages
            loadMessages()
        }
    }

    fun loadKeyTrust() {
        viewModelScope.launch {
            val info = repository.getKeyTrustInfo(otherUserId)
            _uiState.update { it.copy(keyTrust = info) }
        }
    }

    fun verifyKey() {
        viewModelScope.launch {
            repository.verifyUserKey(otherUserId)
            loadKeyTrust()
        }
    }

    fun acceptNewKey() {
        viewModelScope.launch {
            repository.acceptNewKey(otherUserId)
            loadKeyTrust()
        }
    }

    fun loadMessages() {
        // 1. Instant loading from Room cache (ciphertext is decrypted by the repository).
        // The live collector must be a SINGLE permanent subscription: re-invoking
        // loadMessages() from failure handlers / undo must never stack a second collector,
        // while the immediate server fetch below still runs on every call.
        if (messagesCollectionJob?.isActive != true) {
            messagesCollectionJob = viewModelScope.launch {
                repository.getMessagesFlow(otherUserId).collect { newMessages ->
                    _uiState.update { state ->
                        // CRITICAL: Preserve transient metadata (decrypted content, reactions,
                        // enriched reply data) that isn't persisted in the basic message entity.
                        // A tombstone must never be reverted to stale plaintext, so deletions
                        // always win over any cached content.
                        val existingById = state.messages.associateBy { it.id }
                        val newIds = newMessages.mapTo(mutableSetOf()) { it.id }
                        val merged = newMessages.map { newMsg ->
                            val existing = existingById[newMsg.id]
                            if (existing != null) {
                                newMsg.copy(
                                    content = when {
                                        existing.isDeletedForEveryone -> existing.content
                                        newMsg.isDeletedForEveryone -> newMsg.content
                                        else -> existing.content
                                    },
                                    reactions = if (pendingReactions[newMsg.id].isNullOrEmpty()) newMsg.reactions else existing.reactions,
                                    replyToContent = (newMsg.replyToContent ?: existing.replyToContent)?.normalizeReplySnippet(),
                                    replyToSenderName = newMsg.replyToSenderName ?: existing.replyToSenderName,
                                    isPending = existing.isPending || newMsg.isPending
                                )
                            } else newMsg
                        } + state.messages.filter { it.isDeletedForEveryone && it.id !in newIds }

                        // Strict chronological order (ISO timestamps sort lexicographically),
                        // with unsent pending messages pinned to the bottom of the list.
                        val sorted = merged.sortedWith(compareBy({ it.createdAt }, { if (it.isPending) 1 else 0 }))

                        state.copy(
                            messages = sorted,
                            isLoading = false,
                            // Keep the same filter as updateSearchQuery: deleted-for-everyone
                            // tombstones must never count as search matches.
                            matchingMessageIds = if (state.searchQuery.isNotBlank()) {
                                sorted.filter { !it.isDeletedForEveryone && it.content.contains(state.searchQuery, ignoreCase = true) }.map { it.id }.toSet()
                            } else emptySet()
                        )
                    }
                    if (newMessages.any { it.senderId == otherUserId && !it.isRead }) {
                        repository.markMessagesAsRead(otherUserId)
                    }
                }
            }
        }

        // 2. Background sync with Supabase (runs on every call for the immediate path)
        viewModelScope.launch {
            repository.getMessages(otherUserId)
                .onFailure { err ->
                    handleError(err, "getMessagesSync")
                }
        }
    }

    /** Reply snippets for image targets are normalized to the attachment prefix so the UI
     *  can detect them model-robustly instead of matching display strings. */
    private fun String?.normalizeReplySnippet(): String? = when (this) {
        "Image", "📷 Image" -> WhisperImageAttachment.MESSAGE_PREFIX
        else -> this
    }

    // ── REPLY-TO MESSAGE ──
    fun setReplyTarget(message: WhisperMessage) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun clearReplyTarget() {
        _uiState.update { it.copy(replyingToMessage = null) }
    }

    // ── EMOJI REACTIONS ──
    fun toggleReaction(message: WhisperMessage, emoji: String) {
        if (message.id.isBlank() || message.isPending) return
        // Guard against double-taps: only one in-flight toggle per message/emoji.
        val inFlight = pendingReactions.getOrPut(message.id) { mutableSetOf() }
        if (!inFlight.add(emoji)) return
        // Optimistic local state update
        _uiState.update { state ->
            val updatedMsgs = state.messages.map { msg ->
                if (msg.id == message.id) {
                    val currentReactions = msg.reactions.toMutableList()
                    val existingIndex = currentReactions.indexOfFirst { it.emoji == emoji }
                    if (existingIndex >= 0) {
                        val existing = currentReactions[existingIndex]
                        if (existing.reactedByMe) {
                            if (existing.count <= 1) {
                                currentReactions.removeAt(existingIndex)
                            } else {
                                currentReactions[existingIndex] = existing.copy(
                                    count = existing.count - 1,
                                    reactedByMe = false,
                                    userIds = existing.userIds.filter { it != myUserId }
                                )
                            }
                        } else {
                            currentReactions[existingIndex] = existing.copy(
                                count = existing.count + 1,
                                reactedByMe = true,
                                userIds = existing.userIds + myUserId
                            )
                        }
                    } else {
                        currentReactions.add(
                            WhisperReactionSummary(
                                emoji = emoji,
                                count = 1,
                                userIds = listOf(myUserId),
                                reactedByMe = true
                            )
                        )
                    }
                    msg.copy(reactions = currentReactions)
                } else msg
            }
            state.copy(messages = updatedMsgs)
        }

        viewModelScope.launch {
            repository.toggleReaction(message.id, emoji, otherUserId = otherUserId)
                .onFailure { err ->
                    pendingReactions[message.id]?.remove(emoji)
                    handleError(err, "toggleReaction")
                    loadMessages()
                }
                .onSuccess {
                    pendingReactions[message.id]?.remove(emoji)
                    // Reflect the server-confirmed state once the optimistic update is no
                    // longer in flight (own ReactionEvent echoes are skipped by the UI).
                    scheduleReactionSync(message.id, delayMs = 600)
                }
        }
    }

    /**
     * Debounced authoritative reaction fetch. While any of MY pending toggles for a message
     * is still in flight, the server snapshot would clobber the optimistic state — defer
     * instead of overwriting, until the pending set drains.
     */
    private fun scheduleReactionSync(messageId: String, delayMs: Long = 400) {
        reactionSyncJobs[messageId]?.cancel()
        reactionSyncJobs[messageId] = viewModelScope.launch {
            delay(delayMs)
            if (pendingReactions[messageId].isNullOrEmpty()) {
                val reactionMap = repository.getReactionsForMessages(listOf(messageId)).getOrNull()
                if (reactionMap != null) {
                    val updatedList = reactionMap[messageId] ?: emptyList()
                    _uiState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == messageId) msg.copy(reactions = updatedList) else msg
                        }
                        state.copy(messages = updated)
                    }
                }
            } else {
                // My optimistic toggle is still in flight — re-schedule instead of clobbering.
                scheduleReactionSync(messageId, delayMs = 800)
            }
        }
    }

    // ── IN-CHAT MESSAGE SEARCH ──
    fun toggleSearch(active: Boolean? = null) {
        _uiState.update { state ->
            val newActive = active ?: !state.isSearchActive
            state.copy(
                isSearchActive = newActive,
                searchQuery = if (newActive) state.searchQuery else "",
                matchingMessageIds = if (newActive) state.matchingMessageIds else emptySet(),
                activeSearchMatchIndex = if (newActive) state.activeSearchMatchIndex else -1
            )
        }
    }

    fun updateSearchQuery(query: String) {
        // The text field updates immediately; the O(n) match scan is debounced so typing
        // never runs a full-list scan per keystroke on the main thread.
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(250)
            _uiState.update { state ->
                val matchedIds = if (state.searchQuery.isBlank()) {
                    emptySet()
                } else {
                    state.messages
                        .filter { !it.isDeletedForEveryone && it.content.contains(state.searchQuery, ignoreCase = true) }
                        .map { it.id }
                        .toSet()
                }
                state.copy(
                    matchingMessageIds = matchedIds,
                    activeSearchMatchIndex = if (matchedIds.isNotEmpty()) 0 else -1
                )
            }
        }
    }

    fun navigateSearchMatch(direction: Int) {
        _uiState.update { state ->
            val size = state.matchingMessageIds.size
            if (size == 0) return@update state.copy(activeSearchMatchIndex = -1)
            val next = (state.activeSearchMatchIndex + direction).mod(size)
            state.copy(activeSearchMatchIndex = next)
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (uiState.value.isBlockedByOther) {
            _uiState.update { it.copy(error = UiText.StringResource(R.string.st_Whisper_Chat_BlockedByOther)) }
            return
        }
        if (uiState.value.isBlockedByMe) {
            _uiState.update { it.copy(error = UiText.StringResource(R.string.st_Whisper_Chat_InputUnblock)) }
            return
        }

        val originalText = content
        val replyTarget = uiState.value.replyingToMessage
        _draftText.value = ""
        clearReplyTarget()
        sendTypingSignal(false)
        val trimmedContent = content.trim()
        val replySnippet = replyTarget?.let { target ->
            // Image targets use the attachment prefix marker so the UI can detect them
            // model-robustly (normalized back to display labels at render time).
            if (target.content.startsWith("whisper:image:")) {
                WhisperImageAttachment.MESSAGE_PREFIX
            } else {
                target.content.take(100)
            }
        }

        val optimisticMsg = WhisperMessage(
            id = "pending_${System.currentTimeMillis()}_${pendingIdCounter++}",
            senderId = myUserId,
            receiverId = otherUserId,
            content = trimmedContent,
            replyToId = replyTarget?.id,
            replyToContent = replySnippet,
            replyToSenderName = if (replyTarget?.senderId == myUserId) "You" else uiState.value.otherUser?.effectiveName ?: "User",
            isPending = true,
            createdAt = java.time.Instant.now().toString()
        )

        _uiState.update { state ->
            state.copy(messages = state.messages + optimisticMsg)
        }

        viewModelScope.launch {
            repository.sendMessage(otherUserId, trimmedContent, replyTarget?.id)
                .onSuccess { newMsg ->
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id && it.id != newMsg.id }
                        val enrichedMsg = newMsg.copy(
                            replyToContent = replySnippet,
                            replyToSenderName = if (replyTarget?.senderId == myUserId) "You" else uiState.value.otherUser?.effectiveName ?: "User"
                        )
                        state.copy(messages = sortedMessages(filtered + enrichedMsg))
                    }
                }
                .onFailure { err ->
                    // Only restore the draft if the user hasn't already typed something
                    // newer; the reply target likewise survives only if still unset.
                    if (_draftText.value.isBlank()) _draftText.value = originalText
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id }
                        state.copy(
                            messages = filtered,
                            replyingToMessage = if (state.replyingToMessage == null) replyTarget else state.replyingToMessage
                        )
                    }
                    handleError(err, "sendMessage")
                }
        }
    }

    fun sendImage(imageBytes: ByteArray, mimeType: String, expiresAfterSeconds: Long?) {
        if (uiState.value.isBlockedByMe || uiState.value.isBlockedByOther) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAttachment = true) }
            repository.sendEncryptedImage(otherUserId, imageBytes, mimeType, expiresAfterSeconds)
                .onSuccess { message ->
                    _uiState.update { state ->
                        state.copy(
                            messages = sortedMessages(state.messages.filterNot { it.id == message.id } + message),
                            isUploadingAttachment = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isUploadingAttachment = false) }
                    handleError(error, "sendEncryptedImage")
                }
        }
    }

    /** Reads + compresses a picked image and sends it, all in viewModelScope so leaving the
     *  screen mid-upload no longer silently cancels the send (unlike a composition scope). */
    fun sendImageFromUri(context: Context, uri: android.net.Uri, expiresAfterSeconds: Long?) {
        viewModelScope.launch {
            // Bounded read off the main thread; the spinner stays up until sendImage resolves.
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { readBoundedImageBytes(it, context) }
                }.getOrNull()
            }
            if (bytes == null) {
                _uiState.update { it.copy(error = UiText.StringResource(R.string.st_Whisper_Error_ReadImage)) }
                return@launch
            }
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            // Compress before encrypt to stay within the edge-function body limit.
            val compressed = compressImageForUpload(bytes, mimeType)
            // If compression succeeded the bytes are JPEG; otherwise keep the source mime.
            val outMime = if (compressed === bytes) mimeType else "image/jpeg"
            sendImage(compressed, outMime, expiresAfterSeconds)
        }
    }

    fun loadEncryptedImage(message: WhisperMessage) {
        if (_uiState.value.decryptedImageBytes.containsKey(message.id)) return
        val attachment = WhisperImageAttachment.fromMessageContent(message.content) ?: return
        // Never cache bytes for a disappearing image that has already expired.
        if (attachment.expiresAtEpochSeconds != null &&
            java.time.Instant.now().epochSecond >= attachment.expiresAtEpochSeconds
        ) {
            _uiState.update { it.copy(decryptedImageBytes = it.decryptedImageBytes - message.id) }
            return
        }
        viewModelScope.launch {
            // Ensure we have a public key for decryption. If it's a message from 'me', 
            // we need the receiver's key (since we encrypted it for them). 
            // If it's from someone else, we need the sender's key.
            val peerId = if (message.senderId == myUserId) message.receiverId else message.senderId
            
            val key = partnerPublicKey.takeIf { peerId == otherUserId }
                ?: repository.getProfile(peerId).getOrNull()?.publicKey
                // Fall back to the key this device last accepted for that peer so
                // cached ciphertext stays decryptable after a process restart.
                ?: repository.getDecryptionKey(peerId)
            
            if (key != null && peerId == otherUserId) {
                partnerPublicKey = key
            }
            
            repository.downloadEncryptedImage(attachment, key)
                .onSuccess { bytes ->
                    _uiState.update { state ->
                        // Bound the in-memory image cache so long chats can't exhaust memory.
                        val cache = state.decryptedImageBytes.toMutableMap()
                        while (cache.size >= MAX_DECRYPTED_IMAGES) {
                            val oldest = cache.keys.firstOrNull() ?: break
                            cache.remove(oldest)
                        }
                        cache[message.id] = bytes
                        state.copy(decryptedImageBytes = cache)
                    }
                }
                .onFailure { error -> handleError(error, "downloadEncryptedImage") }
        }
    }


    // ── MESSAGE DELETION ──
    fun deleteMessageForEveryone(message: WhisperMessage) {
        // Optimistic local update. WhisperMessage.isDeletedForEveryone is a computed getter
        // derived from content, so mirroring the server's exact tombstone text both marks the
        // message deleted and keeps local and remote state identical until the next reload.
        val tombstone = "This message has been deleted"
        _uiState.update { state ->
            val updated = state.messages.map {
                if (it.id == message.id) it.copy(content = tombstone, contentIv = null) else it
            }
            state.copy(messages = updated)
        }

        viewModelScope.launch {
            // senderDisplayName is unused by the server write; pass "" rather than a dead
            // "You" constant so the tombstone stays server-authoritative.
            repository.deleteMessageForEveryone(message.id, otherUserId, "")
                .onFailure { err ->
                    handleError(err, "deleteMessage")
                    loadMessages()
                }
        }
    }

    fun deleteMessageForMe(message: WhisperMessage) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != message.id })
        }

        viewModelScope.launch {
            repository.deleteMessageForMe(message.id)
                .onFailure { loadMessages() }
        }
    }

    fun sendFriendRequest() {
        viewModelScope.launch {
            repository.sendFriendRequest(otherUserId)
                .onSuccess {
                    repository.getFriendshipStatus(otherUserId).onSuccess { (status, friendship) ->
                        _uiState.update {
                            it.copy(
                                friendStatus = status,
                                iAmRequester = friendship?.iRequested(myUserId) ?: false,
                                isFriendStatusLoaded = true
                            )
                        }
                    }
                }
                .onFailure { handleError(it, "sendFriendRequest") }
        }
    }

    fun acceptFriendRequest() {
        viewModelScope.launch {
            repository.getFriendshipStatus(otherUserId)
                .onSuccess { (_, friendship) ->
                    val recordId = friendship?.id ?: run {
                        handleError(Exception("No friendship record found"), "acceptFriendRequest")
                        return@launch
                    }
                    repository.acceptFriendRequest(recordId)
                        .onSuccess {
                            _uiState.update { it.copy(friendStatus = FriendStatus.ACCEPTED, iAmRequester = false) }
                        }
                        .onFailure { handleError(it, "acceptFriendRequest") }
                }
                .onFailure { handleError(it, "acceptFriendRequest") }
        }
    }

    // ── CLEAR CHAT WITH 30s LIVE COUNTDOWN UNDO ──
    fun clearChat(range: ClearChatTimeRange, customStartIso: String? = null, customEndIso: String? = null) {
        viewModelScope.launch {
            val now = Instant.now()
            val (fromIso, toIso) = when (range) {
                ClearChatTimeRange.PAST_24_HOURS -> Pair(now.minus(24, ChronoUnit.HOURS).toString(), now.toString())
                ClearChatTimeRange.PAST_7_DAYS   -> Pair(now.minus(7, ChronoUnit.DAYS).toString(), now.toString())
                ClearChatTimeRange.PAST_30_DAYS  -> Pair(now.minus(30, ChronoUnit.DAYS).toString(), now.toString())
                ClearChatTimeRange.ALL_TIME      -> Pair(null, null)
                ClearChatTimeRange.CUSTOM        -> Pair(customStartIso, customEndIso)
            }

            repository.clearMessagesForRange(otherUserId, fromIso, toIso)
                .onSuccess { deletedList ->
                    // Accumulate: a second clear while the first undo window is open must
                    // not destroy the earlier batch — undo restores everything together.
                    deletedMessagesUndoBuffer.addAll(deletedList)
                    _uiState.update { state ->
                        val deletedIds = deletedList.map { it.id }.toSet()
                        state.copy(messages = state.messages.filter { it.id !in deletedIds })
                    }
                    _undoState.value = WhisperUndoUiState(clearedCount = deletedMessagesUndoBuffer.size, secondsRemaining = 30)

                    undoTimerJob?.cancel()
                    undoTimerJob = viewModelScope.launch {
                        for (sec in 30 downTo 1) {
                            _undoState.update { it.copy(secondsRemaining = sec) }
                            delay(1_000)
                        }
                        deletedMessagesUndoBuffer.clear()
                        _undoState.value = WhisperUndoUiState()
                    }
                }
                .onFailure { err ->
                    handleError(err, "clearChat")
                }
        }
    }

    fun undoClearChat() {
        if (deletedMessagesUndoBuffer.isEmpty()) return
        val toRestore = deletedMessagesUndoBuffer.toList()
        undoTimerJob?.cancel()
        _undoState.value = WhisperUndoUiState()

        viewModelScope.launch {
            repository.restoreMessages(toRestore)
                .onSuccess {
                    deletedMessagesUndoBuffer.clear()
                    loadMessages()
                }
                .onFailure { err ->
                    handleError(err, "restoreMessages")
                    // Keep the undo bar alive so the user can retry within the window.
                    _undoState.value = WhisperUndoUiState(clearedCount = deletedMessagesUndoBuffer.size, secondsRemaining = 30)
                    undoTimerJob = viewModelScope.launch {
                        for (sec in 30 downTo 1) {
                            _undoState.update { it.copy(secondsRemaining = sec) }
                            delay(1_000)
                        }
                        deletedMessagesUndoBuffer.clear()
                        _undoState.value = WhisperUndoUiState()
                    }
                }
        }
    }

    // ── MUTE / UNMUTE ──
    fun toggleMute(durationMs: Long = Long.MAX_VALUE) {
        val currentlyMuted = uiState.value.isMuted
        if (currentlyMuted) {
            mutePrefs.unmuteUser(otherUserId)
            _uiState.update { it.copy(isMuted = false) }
        } else {
            val until = if (durationMs == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + durationMs
            mutePrefs.muteUser(otherUserId, until)
            _uiState.update { it.copy(isMuted = true) }
        }
    }

    // ── BLOCK / UNBLOCK ──
    fun toggleBlock() {
        val isBlocked = uiState.value.isBlockedByMe
        viewModelScope.launch {
            if (isBlocked) {
                repository.unblockUser(otherUserId)
                    .onSuccess {
                        _uiState.update { it.copy(isBlockedByMe = false) }
                    }
                    .onFailure { err ->
                        handleError(err, "unblockUser")
                    }
            } else {
                repository.blockUser(otherUserId)
                    .onSuccess {
                        _uiState.update { it.copy(isBlockedByMe = true) }
                    }
                    .onFailure { err ->
                        handleError(err, "blockUser")
                    }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateDraft(text: String) {
        _draftText.value = text
        if (text.isNotBlank()) {
            if (!isCurrentlyTyping) {
                isCurrentlyTyping = true
                sendTypingSignal(true)
            }
            typingDebounceJob?.cancel()
            typingDebounceJob = viewModelScope.launch {
                delay(2500)
                isCurrentlyTyping = false
                sendTypingSignal(false)
            }
        } else {
            if (isCurrentlyTyping) {
                isCurrentlyTyping = false
                sendTypingSignal(false)
            }
            typingDebounceJob?.cancel()
        }
    }

    private fun sendTypingSignal(isTyping: Boolean) {
        viewModelScope.launch {
            repository.sendTypingStatus(otherUserId, isTyping)
        }
    }

    /** Chronological order (ISO timestamps sort lexicographically), pending pinned last. */
    private fun sortedMessages(messages: List<WhisperMessage>): List<WhisperMessage> =
        messages.sortedWith(compareBy({ it.createdAt }, { if (it.isPending) 1 else 0 }))

    private fun sendPresenceSignal(isOnline: Boolean) {
        viewModelScope.launch {
            repository.sendPresence(otherUserId, isOnline)
        }
    }

    private fun subscribeToPresence() {
        presenceJob = viewModelScope.launch {
            // Transient subscription failures (offline blips, channel teardown) must not
            // kill presence permanently: re-subscribe every 3s, capped at 10 consecutive
            // failures. Cancellation is always rethrown.
            var retries = 0
            while (isActive) {
                try {
                    repository.subscribeToPresence(otherUserId).collect { (isOnline, ts) ->
                        _uiState.update { it.copy(isPartnerOnline = isOnline, partnerLastSeen = ts) }
                    }
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    retries++
                    if (retries > 10) {
                        android.util.Log.w("WhisperChatVM", "Presence subscription gave up after $retries failures")
                        break
                    }
                    delay(3000)
                }
            }
        }
    }

    private fun subscribeToChat() {
        val myId = myUserId
        if (myId.isEmpty()) return

        realtimeJob = viewModelScope.launch {
            // Consecutive-failure cap prevents an endless retry loop after e.g. a revoked
            // session or a permanently broken channel; each successful event resets it.
            var consecutiveFailures = 0
            repository.subscribeToChat(otherUserId)
                .retry { cause ->
                    if (authManager.currentUserId == null) {
                        // Session is gone: the screen will navigate back; retrying would
                        // just spam failed auth requests forever.
                        android.util.Log.w("WhisperChatVM", "Realtime subscription stopped: session expired (${cause.message})")
                        false
                    } else if (consecutiveFailures >= 10) {
                        android.util.Log.w("WhisperChatVM", "Realtime subscription gave up after 10 consecutive failures: ${cause.message}")
                        _uiState.update { it.copy(isRealtimeDisconnected = true) }
                        false
                    } else {
                        consecutiveFailures++
                        android.util.Log.e("WhisperVM", "Realtime subscription error: ${cause.message}. Retrying in 3s...")
                        delay(3000)
                        true // Retry while the screen is active (capped above)
                    }
                }
                .collect { event ->
                    consecutiveFailures = 0
                    if (_uiState.value.isRealtimeDisconnected) {
                        _uiState.update { it.copy(isRealtimeDisconnected = false) }
                    }
                    when (event) {
                        is WhisperChatEvent.MessageEvent -> {
                            val newMsg = event.message
                            _uiState.update { state ->
                                val existingIndex = state.messages.indexOfFirst { it.id == newMsg.id }
                                if (existingIndex >= 0) {
                                    val mutableList = state.messages.toMutableList()
                                    val current = mutableList[existingIndex]
                                    mutableList[existingIndex] = current.copy(
                                        content = newMsg.content,
                                        contentIv = newMsg.contentIv,
                                        reactions = if (newMsg.reactions.isNotEmpty()) newMsg.reactions else current.reactions,
                                        isRead = newMsg.isRead || current.isRead
                                    )
                                    state.copy(messages = mutableList)
                                } else {
                                    // Enrich reply metadata for live message
                                    val enrichedMsg = if (newMsg.replyToId != null && (newMsg.replyToContent == null || newMsg.replyToContent.startsWith("whisper:image:"))) {
                                        val replyTarget = state.messages.find { it.id == newMsg.replyToId }
                                        if (replyTarget != null) {
                                            // Image targets use the attachment prefix marker so the UI can
                                            // detect them model-robustly (rendered as a localized label).
                                            val content = if (replyTarget.content.startsWith("whisper:image:")) {
                                                WhisperImageAttachment.MESSAGE_PREFIX
                                            } else {
                                                replyTarget.content.take(100)
                                            }
                                            newMsg.copy(
                                                replyToContent = content,
                                                replyToSenderName = if (replyTarget.senderId == myId) "You" else state.otherUser?.effectiveName ?: "User"
                                            )
                                        } else newMsg
                                    } else newMsg

                                    // Deduplicate: remove pending messages that likely match this incoming one
                                    // Use a stricter match or just let Room handle the cleanup if repository persists it.
                                    // Repository DOES persist it, so Room will eventually emit the cleaned list.
                                    // We add it here for ultra-low-latency UI updates.
                                    val filtered = state.messages.toMutableList()
                                    if (enrichedMsg.senderId == myUserId) {
                                        // Remove only the FIRST pending message with identical content:
                                        // two identical pending sends must not both be dropped by one echo.
                                        val echoIndex = filtered.indexOfFirst {
                                            it.id.startsWith("pending_") && it.content.trim() == enrichedMsg.content.trim()
                                        }
                                        if (echoIndex >= 0) {
                                            filtered.removeAt(echoIndex)
                                        } else {
                                            filtered.removeAll { it.id == enrichedMsg.id }
                                        }
                                    } else {
                                        filtered.removeAll { it.id == enrichedMsg.id }
                                    }
                                    state.copy(messages = sortedMessages(filtered + enrichedMsg))
                                }
                            }
                            if (newMsg.senderId == otherUserId) {
                                repository.markMessagesAsRead(otherUserId)
                                notificationManager.cancelMessageNotification(otherUserId)
                            }
                        }
                        is WhisperChatEvent.ReactionEvent -> {
                            // Skip echoes of my own toggles: the optimistic UI update already
                            // applied this change, and re-applying would double-flip it.
                            if (event.userId != myUserId) {
                                _uiState.update { state ->
                                    val updated = state.messages.map { msg ->
                                        if (msg.id == event.messageId) {
                                            val curReactions = msg.reactions.toMutableList()
                                            val idx = curReactions.indexOfFirst { it.emoji == event.emoji }
                                            if (idx >= 0) {
                                                val existing = curReactions[idx]
                                                val containsUser = existing.userIds.contains(event.userId)
                                                if (containsUser) {
                                                    val newUserIds = existing.userIds.filter { it != event.userId }
                                                    if (newUserIds.isEmpty()) {
                                                        curReactions.removeAt(idx)
                                                    } else {
                                                        curReactions[idx] = existing.copy(
                                                            count = newUserIds.size,
                                                            userIds = newUserIds,
                                                            reactedByMe = if (event.userId == myUserId) false else existing.reactedByMe
                                                        )
                                                    }
                                                } else {
                                                    val newUserIds = existing.userIds + event.userId
                                                    curReactions[idx] = existing.copy(
                                                        count = newUserIds.size,
                                                        userIds = newUserIds,
                                                        reactedByMe = if (event.userId == myUserId) true else existing.reactedByMe
                                                    )
                                                }
                                            } else {
                                                curReactions.add(
                                                    WhisperReactionSummary(
                                                        emoji = event.emoji,
                                                        count = 1,
                                                        userIds = listOf(event.userId),
                                                        reactedByMe = event.userId == myUserId
                                                    )
                                                )
                                            }
                                            msg.copy(reactions = curReactions)
                                        } else msg
                                    }
                                    state.copy(messages = updated)
                                }
                            }
                            // 2. Authoritative sync with DB (debounced: bursts of reactions
                            // only trigger one round-trip). Messages with in-flight pending
                            // toggles are deferred, never overwritten (see scheduleReactionSync).
                            scheduleReactionSync(event.messageId)
                        }
                        is WhisperChatEvent.DeleteEvent -> {
                            _uiState.update { state ->
                                state.copy(messages = state.messages.filter { it.id != event.messageId })
                            }
                        }
                    }
                }
        }
    }

    /** Reconnects all realtime listeners after a network failure or retry cap. */
    fun reconnectRealtime() {
        _uiState.update { it.copy(isRealtimeDisconnected = false) }
        subscribeToChat()
        subscribeToTyping()
        subscribeToPresence()
    }

    private fun subscribeToTyping() {
        typingSubscriptionJob = viewModelScope.launch {
            // Transient subscription failures must not kill typing status permanently:
            // re-subscribe every 3s, capped at 10 consecutive failures. Cancellation is
            // always rethrown.
            var retries = 0
            while (isActive) {
                try {
                    repository.subscribeToTypingStatus(otherUserId).collect { isTyping ->
                        _uiState.update { it.copy(isPartnerTyping = isTyping) }
                    }
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    retries++
                    if (retries > 10) {
                        android.util.Log.w("WhisperChatVM", "Typing subscription gave up after $retries failures")
                        break
                    }
                    delay(3000)
                }
            }
        }
    }

    fun onScrolledUp() {
        _uiState.update { it.copy(unreadMessagesScrolledUp = it.unreadMessagesScrolledUp + 1) }
    }

    fun onScrolledToBottom() {
        _uiState.update { it.copy(unreadMessagesScrolledUp = 0) }
    }

    override fun onCleared() {
        super.onCleared()
        notificationManager.currentChatId = null
        // viewModelScope is cancelled the moment onCleared starts, so a scope-launched
        // presence-off would never leave. Fire it in a top-level scope that ignores
        // cancellation so partners reliably see us go offline.
        CoroutineScope(NonCancellable + Dispatchers.IO).launch {
            runCatching { repository.sendPresence(otherUserId, false) }
                .onFailure { android.util.Log.w("WhisperChatVM", "presence-off signal failed", it) }
        }
        realtimeJob?.cancel()
        typingSubscriptionJob?.cancel()
        typingDebounceJob?.cancel()
        presenceJob?.cancel()
        undoTimerJob?.cancel()
        reactionSyncJobs.values.forEach { it.cancel() }
        reactionSyncJobs.clear()
        messagesCollectionJob?.cancel()
        searchDebounceJob?.cancel()
    }
}

private const val MAX_LOCAL_IMAGE_BYTES = 5 * 1024 * 1024 - 16 // transport cap minus AES-GCM tag

private fun readBoundedImageBytes(input: java.io.InputStream, context: Context): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= MAX_LOCAL_IMAGE_BYTES) { context.getString(R.string.st_Whisper_Error_ImageTooLarge) }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

/** Decodes a bitmap downsampled so its pixel count stays within [maxWidth]x[maxHeight]. */
private fun decodeBoundedBitmap(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth || bounds.outHeight / (sample * 2) >= maxHeight) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

private suspend fun compressImageForUpload(bytes: ByteArray, mimeType: String): ByteArray =
    withContext(Dispatchers.Default) {
        runCatching {
            // Sample-decode huge sources first so the pipeline never allocates a full-size bitmap.
            val bitmap = decodeBoundedBitmap(bytes, 1920, 1920) ?: return@withContext bytes
            val maxDimension = 1920
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = min(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                val newWidth = (width * ratio).roundToInt().coerceAtLeast(1)
                val newHeight = (height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
            val result = out.toByteArray()
            if (result.isNotEmpty() && result.size < bytes.size) result else bytes
        }.getOrDefault(bytes)
    }
