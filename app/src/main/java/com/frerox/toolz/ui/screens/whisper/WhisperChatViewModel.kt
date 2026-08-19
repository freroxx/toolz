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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class WhisperChatViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val crypto: WhisperCrypto,
    private val notificationManager: WhisperNotificationManager,
    private val mutePrefs: WhisperMutePreferences,
    private val hiddenChatsStore: WhisperHiddenChatsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val otherUserId: String = checkNotNull(savedStateHandle["otherUserId"])
    val myUserId: String get() = authManager.currentUserId ?: ""

    private val _uiState = MutableStateFlow(WhisperChatUiState())
    val uiState: StateFlow<WhisperChatUiState> = _uiState.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(replay = 0)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    private var partnerPublicKey: String? = null
    private var realtimeJob: Job? = null
    private var typingSubscriptionJob: Job? = null
    private var typingDebounceJob: Job? = null
    private var presenceJob: Job? = null
    private var undoTimerJob: Job? = null
    private var isCurrentlyTyping = false

    private val deletedMessagesUndoBuffer = mutableListOf<WhisperMessage>()

    init {
        notificationManager.currentChatId = otherUserId
        notificationManager.cancelMessageNotification(otherUserId)
        hiddenChatsStore.unhideChat(otherUserId)
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(otherUserId)) }
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
        // 1. Instant loading from Room cache
        viewModelScope.launch {
            repository.getMessagesFlow(otherUserId).collect { newMessages ->
                _uiState.update { state ->
                    // CRITICAL: Preserve transient metadata (reactions, enriched reply data) 
                    // that isn't persisted in the basic message entity.
                    val merged = newMessages.map { newMsg ->
                        val existing = state.messages.find { it.id == newMsg.id }
                        if (existing != null) {
                            newMsg.copy(
                                reactions = if (newMsg.reactions.isEmpty()) existing.reactions else newMsg.reactions,
                                replyToContent = newMsg.replyToContent ?: existing.replyToContent,
                                replyToSenderName = newMsg.replyToSenderName ?: existing.replyToSenderName,
                                isPending = newMsg.isPending
                            )
                        } else newMsg
                    }

                    state.copy(
                        messages = merged,
                        isLoading = false,
                        matchingMessageIds = if (state.searchQuery.isNotBlank()) {
                            merged.filter { it.content.contains(state.searchQuery, ignoreCase = true) }.map { it.id }.toSet()
                        } else emptySet()
                    )
                }
                if (newMessages.any { it.senderId == otherUserId && !it.isRead }) {
                    repository.markMessagesAsRead(otherUserId)
                }
            }
        }

        // 2. Background sync with Supabase
        viewModelScope.launch {
            repository.getMessages(otherUserId)
                .onFailure { err ->
                    handleError(err, "getMessagesSync")
                }
        }
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
        if (message.id.isBlank()) return
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
                    handleError(err, "toggleReaction")
                    loadMessages()
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
        _uiState.update { state ->
            val matchedIds = if (query.isBlank()) {
                emptySet()
            } else {
                state.messages
                    .filter { !it.isDeletedForEveryone && it.content.contains(query, ignoreCase = true) }
                    .map { it.id }
                    .toSet()
            }
            state.copy(
                searchQuery = query,
                matchingMessageIds = matchedIds,
                activeSearchMatchIndex = if (matchedIds.isNotEmpty()) 0 else -1
            )
        }
    }

    fun navigateSearchMatch(direction: Int) {
        _uiState.update { state ->
            val size = state.matchingMessageIds.size
            if (size == 0) return@update state
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
        val optimisticMsg = WhisperMessage(
            id = "pending_${System.currentTimeMillis()}",
            senderId = myUserId,
            receiverId = otherUserId,
            content = trimmedContent,
            replyToId = replyTarget?.id,
            replyToContent = replyTarget?.content?.take(100),
            replyToSenderName = if (replyTarget?.senderId == myUserId) "You" else uiState.value.otherUser?.effectiveName ?: "User",
            isPending = true
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
                            replyToContent = replyTarget?.content?.take(100),
                            replyToSenderName = if (replyTarget?.senderId == myUserId) "You" else uiState.value.otherUser?.effectiveName ?: "User"
                        )
                        state.copy(messages = filtered + enrichedMsg)
                    }
                }
                .onFailure { err ->
                    _draftText.value = originalText
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id }
                        state.copy(messages = filtered)
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
                            messages = state.messages.filterNot { it.id == message.id } + message,
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

    fun loadEncryptedImage(message: WhisperMessage) {
        if (_uiState.value.decryptedImageBytes.containsKey(message.id)) return
        val attachment = WhisperImageAttachment.fromMessageContent(message.content) ?: return
        viewModelScope.launch {
            val key = partnerPublicKey ?: repository.getProfile(otherUserId).getOrNull()?.publicKey
            if (key != null) {
                partnerPublicKey = key
            }
            repository.downloadEncryptedImage(attachment, key)
                .onSuccess { bytes -> _uiState.update { state -> state.copy(decryptedImageBytes = state.decryptedImageBytes + (message.id to bytes)) } }
                .onFailure { error -> handleError(error, "downloadEncryptedImage") }
        }
    }


    // ── MESSAGE DELETION ──
    fun deleteMessageForEveryone(message: WhisperMessage) {
        val myDisplayName = authManager.currentUserId?.let { "You" } ?: "User"

        // Optimistic local update
        val tombstone = "[deleted_by_sender:$myDisplayName]"
        _uiState.update { state ->
            val updated = state.messages.map {
                if (it.id == message.id) it.copy(content = tombstone, contentIv = null) else it
            }
            state.copy(messages = updated)
        }

        viewModelScope.launch {
            repository.deleteMessageForEveryone(message.id, otherUserId, myDisplayName)
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
                    deletedMessagesUndoBuffer.clear()
                    deletedMessagesUndoBuffer.addAll(deletedList)
                    _uiState.update { state ->
                        val deletedIds = deletedList.map { it.id }.toSet()
                        state.copy(
                            messages = state.messages.filter { it.id !in deletedIds },
                            clearedUndoMessagesCount = deletedList.size,
                            undoSecondsRemaining = 30
                        )
                    }

                    undoTimerJob?.cancel()
                    undoTimerJob = viewModelScope.launch {
                        for (sec in 30 downTo 1) {
                            _uiState.update { it.copy(undoSecondsRemaining = sec) }
                            delay(1_000)
                        }
                        deletedMessagesUndoBuffer.clear()
                        _uiState.update { it.copy(clearedUndoMessagesCount = 0, undoSecondsRemaining = 0) }
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
        _uiState.update { it.copy(clearedUndoMessagesCount = 0, undoSecondsRemaining = 0) }

        viewModelScope.launch {
            repository.restoreMessages(toRestore)
                .onSuccess {
                    deletedMessagesUndoBuffer.clear()
                    loadMessages()
                }
                .onFailure { err ->
                    handleError(err, "restoreMessages")
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

    private fun sendPresenceSignal(isOnline: Boolean) {
        viewModelScope.launch {
            repository.sendPresence(otherUserId, isOnline)
        }
    }

    private fun subscribeToPresence() {
        presenceJob = viewModelScope.launch {
            try {
                repository.subscribeToPresence(otherUserId).collect { (isOnline, ts) ->
                    _uiState.update { it.copy(isPartnerOnline = isOnline, partnerLastSeen = ts) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun subscribeToChat() {
        val myId = myUserId
        if (myId.isEmpty()) return

        realtimeJob = viewModelScope.launch {
            repository.subscribeToChat(otherUserId)
                .retry { cause ->
                    android.util.Log.e("WhisperVM", "Realtime subscription error: ${cause.message}. Retrying in 3s...")
                    delay(3000)
                    true // Retry indefinitely while screen is active
                }
                .collect { event ->
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
                                    val enrichedMsg = if (newMsg.replyToId != null && newMsg.replyToContent == null) {
                                        val replyTarget = state.messages.find { it.id == newMsg.replyToId }
                                        if (replyTarget != null) {
                                            newMsg.copy(
                                                replyToContent = replyTarget.content.take(100),
                                                replyToSenderName = if (replyTarget.senderId == myId) "You" else state.otherUser?.effectiveName ?: "User"
                                            )
                                        } else newMsg
                                    } else newMsg

                                    // Deduplicate: remove pending messages that likely match this incoming one
                                    // Use a stricter match or just let Room handle the cleanup if repository persists it.
                                    // Repository DOES persist it, so Room will eventually emit the cleaned list.
                                    // We add it here for ultra-low-latency UI updates.
                                    val filtered = state.messages.filter { 
                                        val isThisPending = it.id.startsWith("pending_")
                                        if (isThisPending) {
                                            // Stricter check: only remove if content is identical and it's mine
                                            it.content.trim() != enrichedMsg.content.trim() || enrichedMsg.senderId != myUserId
                                        } else {
                                            it.id != enrichedMsg.id
                                        }
                                    }
                                    state.copy(messages = filtered + enrichedMsg)
                                }
                            }
                            if (newMsg.senderId == otherUserId) {
                                repository.markMessagesAsRead(otherUserId)
                                notificationManager.cancelMessageNotification(otherUserId)
                            }
                        }
                        is WhisperChatEvent.ReactionEvent -> {
                            // 1. Optimistic local update from the instant broadcast event
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
                            // 2. Authoritative sync with DB
                            viewModelScope.launch {
                                val reactionMap = repository.getReactionsForMessages(listOf(event.messageId)).getOrNull()
                                if (reactionMap != null) {
                                    val updatedList = reactionMap[event.messageId] ?: emptyList()
                                    _uiState.update { state ->
                                        val updated = state.messages.map { msg ->
                                            if (msg.id == event.messageId) msg.copy(reactions = updatedList) else msg
                                        }
                                        state.copy(messages = updated)
                                    }
                                }
                            }
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

    private fun subscribeToTyping() {
        typingSubscriptionJob = viewModelScope.launch {
            try {
                repository.subscribeToTypingStatus(otherUserId).collect { isTyping ->
                    _uiState.update { it.copy(isPartnerTyping = isTyping) }
                }
            } catch (_: Exception) { }
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
        sendPresenceSignal(false)
        realtimeJob?.cancel()
        typingSubscriptionJob?.cancel()
        typingDebounceJob?.cancel()
        presenceJob?.cancel()
        undoTimerJob?.cancel()
    }
}
