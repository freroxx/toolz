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
    ): Flow<Result<String>>

    /**
     * Tests the connection for the given [config] without writing any state.
     *
     * @param config  An ephemeral [AiConfig] built from the in-dialog form values.
     * @return        A flow that emits exactly one [Result]. Success contains a
     *                short confirmation string; failure contains the error.
     */
    fun testConnection(config: AiConfig): Flow<Result<String>>
}