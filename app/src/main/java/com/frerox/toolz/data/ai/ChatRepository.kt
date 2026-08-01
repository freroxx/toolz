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

package com.frerox.toolz.data.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over all AI providers.
 *
 * - [getChatResponse] sends a prompt (and optional image) to the currently-configured
 *   provider. The flow emits one [Result] per chunk for streaming providers, or a
 *   single [Result] for request/response providers. The ViewModel accumulates chunks.
 *
 * - [testConnection] validates credentials for an ephemeral [AiConfig] WITHOUT
 *   touching the global [AiSettingsManager] state. Safe to call during the
 *   settings dialog without clobbering the live configuration.
 */
interface ChatRepository {

    fun getChatResponse(
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap? = null,
        modelOverride: String? = null,
        providerOverride: String? = null,
        systemPromptOverride: String? = null
    ): Flow<Result<ChatResponseChunk>>

    data class ChatResponseChunk(
        val text: String,
        val sources: String? = null
    )

    /**
     * Tests the connection for the given [config] without writing any state.
     *
     * @param config  An ephemeral [AiConfig] built from the in-dialog form values.
     * @return        A flow that emits exactly one [Result]. Success contains a
     *                short confirmation string; failure contains the error.
     */
    fun testConnection(config: AiConfig): Flow<Result<String>>

    fun performDeepDive(
        prompt: String,
        sourcesJson: String,
        history: List<AiMessage>
    ): Flow<Result<ChatResponseChunk>>

    /**
     * Checks if a specific model is available for the given provider.
     * This might involve calling a provider-specific API.
     */
    suspend fun checkModelAvailability(provider: String, model: String): Boolean
}
