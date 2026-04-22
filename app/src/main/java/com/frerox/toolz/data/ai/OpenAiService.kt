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
