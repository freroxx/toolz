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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

// ─────────────────────────────────────────────────────────────
//  OpenAI-compatible response models
//
//  Request models (OpenAiRequest, OpenAiMessage, etc.) live in
//  AiRepositoryImpl.kt alongside their custom Moshi adapters.
//  Keeping them there avoids duplicates and lets the adapters
//  reference the types directly without cross-file complexity.
// ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class OpenAiResponse(
    val choices: List<OpenAiChoice>,
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    val message: OpenAiResponseMessage,
)

@JsonClass(generateAdapter = true)
data class OpenAiResponseMessage(
    val role: String,
    val content: String?,
    @Json(name = "tool_calls") val toolCalls: List<ToolCall>? = null,
)

@JsonClass(generateAdapter = true)
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@JsonClass(generateAdapter = true)
data class FunctionCall(
    val name: String,
    val arguments: String,
)

@JsonClass(generateAdapter = true)
data class OpenAiModelsResponse(
    val data: List<OpenAiModel>,
)

@JsonClass(generateAdapter = true)
data class OpenAiModel(
    val id: String,
)

// ─────────────────────────────────────────────────────────────
//  Claude (Anthropic) response models
// ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ClaudeResponse(
    val content: List<ClaudeContentBlock>,
    val id: String? = null,
    val role: String? = null,
    val model: String? = null,
)

@JsonClass(generateAdapter = true)
data class ClaudeContentBlock(
    val text: String? = null,
    val type: String,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any>? = null,
)

// ─────────────────────────────────────────────────────────────
//  Retrofit service interface
//
//  @Url makes both endpoints dynamic so the same interface works
//  for ChatGPT / Groq / DeepSeek / OpenRouter without subclassing.
// ─────────────────────────────────────────────────────────────

interface OpenAiService {

    /**
     * OpenAI-compatible chat completion endpoint.
     * Used by: ChatGPT, Groq, DeepSeek, OpenRouter.
     */
    @POST
    suspend fun getChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Header("HTTP-Referer") referer: String? = null,
        @Header("X-Title") title: String? = null,
        @Body request: OpenAiRequest,
    ): OpenAiResponse

    /**
     * OpenAI-compatible models list endpoint.
     */
    @retrofit2.http.GET
    suspend fun listModels(
        @Url url: String,
        @Header("Authorization") authHeader: String,
    ): OpenAiModelsResponse

    /**
     * Anthropic Claude Messages API.
     */
    @POST
    suspend fun getClaudeCompletion(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String,
        @Body request: ClaudeRequest,
    ): ClaudeResponse
}
