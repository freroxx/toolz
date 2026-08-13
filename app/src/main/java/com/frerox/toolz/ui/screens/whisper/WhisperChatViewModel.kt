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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private var partnerPublicKey: String? = null
    private var realtimeJob: Job? = null
    private var typingSubscriptionJob: Job? = null
    private var typingDebounceJob: Job? = null
    private var undoTimerJob: Job? = null
    private var isCurrentlyTyping = false

    private val deletedMessagesUndoBuffer = mutableListOf<WhisperMessage>()

    init {
        notificationManager.currentChatId = otherUserId
        notificationManager.cancelMessageNotification(otherUserId)
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(otherUserId)) }
        loadInitialData()
        subscribeToMessages()
        subscribeToTyping()
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
                    _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "getProfile")) }
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
                    _uiState.update {
                        it.copy(
                            error = WhisperErrorMapper.map(err, "getFriendshipStatus"),
                            isFriendStatusLoaded = true,
                        )
                    }
                }

            // 3. Load block status
            repository.getBlockStatus(otherUserId)
                .onSuccess { (byMe, byOther) ->
                    _uiState.update { it.copy(isBlockedByMe = byMe, isBlockedByOther = byOther) }
                }

            // 4. Load messages
            loadMessages()
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            repository.getMessages(otherUserId)
                .onSuccess { messages ->
                    _uiState.update { it.copy(messages = messages, isLoading = false) }
                    repository.markMessagesAsRead(otherUserId)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "getMessages"), isLoading = false) }
                }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (uiState.value.isBlockedByMe || uiState.value.isBlockedByOther) {
            _uiState.update { it.copy(error = "Cannot send message while blocked.") }
            return
        }

        val originalText = content
        _draftText.value = ""
        sendTypingSignal(false)
        val trimmedContent = content.trim()
        val optimisticMsg = WhisperMessage(
            id = "pending_${System.currentTimeMillis()}",
            senderId = myUserId,
            receiverId = otherUserId,
            content = trimmedContent
        )

        _uiState.update { state ->
            state.copy(messages = state.messages + optimisticMsg)
        }

        viewModelScope.launch {
            repository.sendMessage(otherUserId, trimmedContent)
                .onSuccess { newMsg ->
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id }
                        state.copy(messages = filtered + newMsg)
                    }
                }
                .onFailure { err ->
                    _draftText.value = originalText
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id }
                        state.copy(error = WhisperErrorMapper.map(err, "sendMessage"), messages = filtered)
                    }
                }
        }
    }

    fun sendFriendRequest() {
        viewModelScope.launch {
            repository.sendFriendRequest(otherUserId)
                .onSuccess {
                    _uiState.update { it.copy(friendStatus = FriendStatus.PENDING, iAmRequester = true) }
                }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "sendFriendRequest")) } }
        }
    }

    // ── CLEAR CHAT WITH 60s UNDO ──
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
                            clearedUndoMessagesCount = deletedList.size
                        )
                    }

                    // Start 60-second undo countdown
                    undoTimerJob?.cancel()
                    undoTimerJob = viewModelScope.launch {
                        delay(60_000)
                        deletedMessagesUndoBuffer.clear()
                        _uiState.update { it.copy(clearedUndoMessagesCount = 0) }
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "clearChat")) }
                }
        }
    }

    fun undoClearChat() {
        if (deletedMessagesUndoBuffer.isEmpty()) return
        val toRestore = deletedMessagesUndoBuffer.toList()
        undoTimerJob?.cancel()
        _uiState.update { it.copy(clearedUndoMessagesCount = 0) }

        viewModelScope.launch {
            repository.restoreMessages(toRestore)
                .onSuccess {
                    deletedMessagesUndoBuffer.clear()
                    loadMessages()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "restoreMessages")) }
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
                        _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "unblockUser")) }
                    }
            } else {
                repository.blockUser(otherUserId)
                    .onSuccess {
                        _uiState.update { it.copy(isBlockedByMe = true) }
                    }
                    .onFailure { err ->
                        _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "blockUser")) }
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

    private fun subscribeToMessages() {
        val myId = myUserId
        if (myId.isEmpty()) return

        realtimeJob = viewModelScope.launch {
            try {
                repository.subscribeToIncomingMessages(myId).collect { newMsg ->
                    if (newMsg.senderId == otherUserId || (newMsg.senderId == myId && newMsg.receiverId == otherUserId)) {
                        val keyToUse = partnerPublicKey
                        val decryptedContent = if (newMsg.contentIv != null && keyToUse != null) {
                            crypto.decryptMessage(newMsg.content, newMsg.contentIv, keyToUse)
                        } else newMsg.content

                        _uiState.update { state ->
                            if (state.messages.any { it.id == newMsg.id }) {
                                state
                            } else {
                                val updatedMessages = state.messages + newMsg.copy(content = decryptedContent)
                                state.copy(messages = updatedMessages)
                            }
                        }
                        if (newMsg.senderId == otherUserId) {
                            repository.markMessagesAsRead(otherUserId)
                            notificationManager.cancelMessageNotification(otherUserId)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WhisperChatVM", "Realtime collect error", e)
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

    override fun onCleared() {
        super.onCleared()
        notificationManager.currentChatId = null
        realtimeJob?.cancel()
        typingSubscriptionJob?.cancel()
        typingDebounceJob?.cancel()
        undoTimerJob?.cancel()
    }
}
