/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.GroupPolicyV1
import com.frerox.toolz.data.whisper.WhisperGroupCapability
import com.frerox.toolz.data.whisper.WhisperGroupMembership
import com.frerox.toolz.data.whisper.groupErrorText
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.syncGroup
import com.frerox.toolz.data.whisper.updateGroupPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor state for the owner's who-can-do-what screen. The drafts start as the
 * membership's verified policy and diverge locally until the owner saves —
 * one `updateGroupPolicy` call, one signed POLICY event, no per-toggle writes.
 */
@HiltViewModel
class WhisperGroupPermissionsViewModel @Inject constructor(
    private val repository: com.frerox.toolz.data.whisper.WhisperRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    data class UiState(
        val membership: WhisperGroupMembership? = null,
        val adminCaps: Set<WhisperGroupCapability> = emptySet(),
        val memberCaps: Set<WhisperGroupCapability> = emptySet(),
        val isLoading: Boolean = true,
        val isWorking: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        if (groupsEnabled() && groupId.isNotBlank()) load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.syncGroup(groupId)
                .onSuccess { m ->
                    _uiState.update {
                        it.copy(
                            membership = m,
                            adminCaps = m.policy.adminCapabilities,
                            memberCaps = m.policy.memberCapabilities,
                            isLoading = false,
                            isWorking = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, isWorking = false, error = err(e)) }
                }
        }
    }

    /** Only the owner may touch this screen at all — mirror the server rule. */
    fun isOwner(): Boolean =
        _uiState.value.membership?.policy?.ownerId == repository.myId

    fun toggleAdmin(cap: WhisperGroupCapability) {
        _uiState.update { it.copy(adminCaps = toggle(it.adminCaps, cap)) }
    }

    fun toggleMember(cap: WhisperGroupCapability) {
        // The wire restricts member grants to the delegable subset; refusing
        // here keeps a never-signable draft from ever existing.
        if (cap !in GroupPolicyV1.MEMBER_DELEGABLE_CAPABILITIES) return
        _uiState.update { it.copy(memberCaps = toggle(it.memberCaps, cap)) }
    }

    /** How many role-capability slots differ from the current verified policy. */
    fun changedCount(): Int {
        val m = _uiState.value.membership ?: return 0
        val c = _uiState.value
        return (c.adminCaps - m.policy.adminCapabilities).size +
            (m.policy.adminCapabilities - c.adminCaps).size +
            (c.memberCaps - m.policy.memberCapabilities).size +
            (m.policy.memberCapabilities - c.memberCaps).size
    }

    fun save() {
        // Re-entry + already-saved reset: a second tap mid-save must not race
        // a second signed POLICY event, and a second save in the same visit
        // must re-fire the "saved" auto-return (the flag resets up front).
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, error = null, saved = false) }
        viewModelScope.launch {
            val current = _uiState.value
            val m = current.membership ?: run {
                _uiState.update { it.copy(isWorking = false) }
                return@launch
            }
            runCatching {
                val next = GroupPolicyV1(
                    ownerId = m.policy.ownerId,
                    adminCapabilities = current.adminCaps,
                    memberCapabilities = current.memberCaps,
                )
                repository.updateGroupPolicy(groupId, next).getOrThrow()
            }.onSuccess {
                // Re-sync proves the event round-tripped before claiming saved.
                repository.syncGroup(groupId).onSuccess { verified ->
                    _uiState.update { it.copy(isWorking = false, saved = true, membership = verified) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isWorking = false, error = err(e)) }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isWorking = false, error = err(e)) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun toggle(caps: Set<WhisperGroupCapability>, cap: WhisperGroupCapability): Set<WhisperGroupCapability> =
        if (cap in caps) caps - cap else caps + cap

    private fun err(e: Throwable?): String = groupErrorText(appContext, e)
}
