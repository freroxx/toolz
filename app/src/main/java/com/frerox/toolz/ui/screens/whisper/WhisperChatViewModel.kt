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
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(otherUserId)) }
        loadInitialData()
        subscribeToChat()
        subscribeToTyping()
        subscribeToPresence()
        sendPresenceSignal(true)
    }

    private fun handleError(err: Throwable, context: String) {
        val mapped = WhisperErrorMapper.map(err, context)
        if (mapped == WhisperErrorMapper.SESSION_EXPIRED_SENTINEL || WhisperErrorMapper.isSessionExpired(err)) {
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

            // 4. Load messages
            loadMessages()
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            repository.getMessages(otherUserId)
                .onSuccess { messages ->
                    _uiState.update { state ->
                        state.copy(
                            messages = messages,
                            isLoading = false,
                            matchingMessageIds = if (state.searchQuery.isNotBlank()) {
                                messages.filter { it.content.contains(state.searchQuery, ignoreCase = true) }.map { it.id }.toSet()
                            } else emptySet()
                        )
                    }
                    repository.markMessagesAsRead(otherUserId)
                }
                .onFailure { err ->
                    handleError(err, "getMessages")
                    _uiState.update { it.copy(isLoading = false) }
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
        viewModelScope.launch {
            repository.toggleReaction(message.id, emoji)
                .onSuccess {
                    loadMessages()
                }
                .onFailure { err ->
                    handleError(err, "toggleReaction")
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
            _uiState.update { it.copy(error = "You have been blocked by this user.") }
            return
        }
        if (uiState.value.isBlockedByMe) {
            _uiState.update { it.copy(error = "Unblock user to send messages.") }
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
            repository.deleteMessageForEveryone(message.id, myDisplayName)
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
            try {
                repository.subscribeToChat(otherUserId).collect { newMsg ->
                    _uiState.update { state ->
                        val existingIndex = state.messages.indexOfFirst { it.id == newMsg.id }
                        if (existingIndex >= 0) {
                            // Update existing message (e.g. deletion tombstone, read status)
                            val mutableList = state.messages.toMutableList()
                            mutableList[existingIndex] = newMsg
                            state.copy(messages = mutableList)
                        } else {
                            val filtered = state.messages.filter { !it.id.startsWith("pending_") || it.content != newMsg.content }
                            state.copy(messages = filtered + newMsg)
                        }
                    }
                    if (newMsg.senderId == otherUserId) {
                        repository.markMessagesAsRead(otherUserId)
                        notificationManager.cancelMessageNotification(otherUserId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WhisperChatVM", "Chat realtime collect error", e)
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
