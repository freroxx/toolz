package com.frerox.toolz.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.frerox.toolz.data.search.SearchResult
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiRepository"
private const val MAX_HISTORY_MESSAGES = 24
private const val OPEN_ROUTER_REFERER = "https://toolz.app"
private const val OPEN_ROUTER_TITLE = "Toolz AI"

// ─────────────────────────────────────────────────────────────
//  OpenAI-compatible request models
// ─────────────────────────────────────────────────────────────

/**
 * [MessageContent] is a sum type: a plain string for text-only turns, or a
 * list of content blocks for vision turns. [MessageContentAdapter] handles
 * Moshi serialization for both forms so Retrofit can send the right JSON.
 */
sealed class MessageContent {
    data class Text(val value: String) : MessageContent()
    data class Blocks(val blocks: List<ContentBlock>) : MessageContent()
}

data class ContentBlock(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: ImageUrl? = null,
)

data class ImageUrl(val url: String)

data class OpenAiMessage(
    val role: String,
    val content: MessageContent?,
    @Json(name = "tool_calls") val toolCalls: List<ToolCall>? = null,
    @Json(name = "tool_call_id") val toolCallId: String? = null,
)

data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    @Json(name = "response_format") val responseFormat: ResponseFormat? = null,
    val tools: List<Tool>? = null,
    @Json(name = "tool_choice") val toolChoice: String? = null,
)

data class Tool(
    val type: String = "function",
    val function: ToolDefinition,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters,
)

data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, PropertyDefinition>,
    val required: List<String>,
)

data class PropertyDefinition(
    val type: String,
    val description: String,
)

// No @JsonClass here — used inside OpenAiRequest, same reasoning applies.
data class ResponseFormat(val type: String)

// ─────────────────────────────────────────────────────────────
//  Moshi adapter — MessageContent  (String | ContentBlock[])
// ─────────────────────────────────────────────────────────────

class MessageContentAdapter {

    @ToJson
    fun toJson(writer: JsonWriter, content: MessageContent?) {
        if (content == null) {
            writer.nullValue()
            return
        }
        when (content) {
            is MessageContent.Text   -> writer.value(content.value)
            is MessageContent.Blocks -> {
                writer.beginArray()
                content.blocks.forEach { block ->
                    writer.beginObject()
                    writer.name("type").value(block.type)
                    when (block.type) {
                        "text"      -> writer.name("text").value(block.text)
                        "image_url" -> {
                            writer.name("image_url").beginObject()
                            writer.name("url").value(block.imageUrl?.url)
                            writer.endObject()
                        }
                    }
                    writer.endObject()
                }
                writer.endArray()
            }
        }
    }

    @FromJson
    fun fromJson(reader: JsonReader): MessageContent {
        // Responses always use plain text; array form only appears in requests.
        return MessageContent.Text(reader.nextString())
    }
}

// ─────────────────────────────────────────────────────────────
//  Claude request models
// ─────────────────────────────────────────────────────────────

/** Text content block inside a Claude message. */
data class ClaudeTextContent(
    val type: String = "text",
    val text: String,
)

/** Image content block inside a Claude message. */
data class ClaudeImageContent(
    val type: String = "image",
    val source: ClaudeImageSource,
)

data class ClaudeToolResultContent(
    val type: String = "tool_result",
    @Json(name = "tool_use_id") val toolUseId: String,
    val content: String,
)

data class ClaudeImageSource(
    val type: String = "base64",
    @Json(name = "media_type") val mediaType: String = "image/jpeg",
    val data: String,
)

/**
 * A Claude conversation turn.
 *
 * [content] is either a plain [String] (text-only) or a [List] of
 * [ClaudeTextContent] / [ClaudeImageContent] blocks (vision). The
 * [ClaudeMessageAdapter] handles both forms.
 */
data class ClaudeMessage(
    val role: String,
    val content: Any,  // String | List<ClaudeTextContent | ClaudeImageContent>
)

/**
 * [maxTokens] must serialize as "max_tokens". Without [@Json] the Anthropic
 * API returns a 400: {"error":{"type":"invalid_request_error","message":"max_tokens is required"}}.
 */
data class ClaudeRequest(
    val model: String,
    val messages: List<ClaudeMessage>,
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    val system: String? = null,
    val tools: List<ClaudeTool>? = null,
)

data class ClaudeTool(
    val name: String,
    val description: String,
    @Json(name = "input_schema") val inputSchema: ToolParameters,
)

// ─────────────────────────────────────────────────────────────
//  Moshi adapter — ClaudeMessage  (String content | Block[] content)
// ─────────────────────────────────────────────────────────────

data class ClaudeToolUseContent(
    val type: String = "tool_use",
    val id: String,
    val name: String,
    val input: Map<String, Any>,
)

class ClaudeMessageAdapter {

    @ToJson
    fun toJson(writer: JsonWriter, message: ClaudeMessage) {
        writer.beginObject()
        writer.name("role").value(message.role)
        writer.name("content")
        when (val c = message.content) {
            is String -> writer.value(c)
            is List<*> -> {
                writer.beginArray()
                @Suppress("UNCHECKED_CAST")
                (c as List<Any>).forEach { block ->
                    writer.beginObject()
                    when (block) {
                        is ClaudeTextContent -> {
                            writer.name("type").value(block.type)
                            writer.name("text").value(block.text)
                        }
                        is ClaudeImageContent -> {
                            writer.name("type").value(block.type)
                            writer.name("source").beginObject()
                            writer.name("type").value(block.source.type)
                            writer.name("media_type").value(block.source.mediaType)
                            writer.name("data").value(block.source.data)
                            writer.endObject()
                        }
                        is ClaudeToolResultContent -> {
                            writer.name("type").value(block.type)
                            writer.name("tool_use_id").value(block.toolUseId)
                            writer.name("content").value(block.content)
                        }
                        is ClaudeToolUseContent -> {
                            writer.name("type").value(block.type)
                            writer.name("id").value(block.id)
                            writer.name("name").value(block.name)
                            writer.name("input")
                            writer.beginObject()
                            block.input.forEach { (k, v) ->
                                writer.name(k).value(v.toString())
                            }
                            writer.endObject()
                        }
                    }
                    writer.endObject()
                }
                writer.endArray()
            }
            else -> writer.value(c.toString())
        }
        writer.endObject()
    }

    @FromJson
    fun fromJson(reader: JsonReader): ClaudeMessage {
        // Response form: {"role":"assistant","content":[{"type":"text","text":"..."}]}
        var role = "assistant"
        var text = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "role"    -> role = reader.nextString()
                "content" -> when (reader.peek()) {
                    JsonReader.Token.STRING      -> text = reader.nextString()
                    JsonReader.Token.BEGIN_ARRAY -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "text" -> text = reader.nextString()
                                    else   -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ClaudeMessage(role = role, content = text)
    }
}

// ─────────────────────────────────────────────────────────────
//  Repository
// ─────────────────────────────────────────────────────────────

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val settingsManager: AiSettingsManager,
    private val openAiService: OpenAiService,
    private val moshi: Moshi
) : ChatRepository {

    private val systemPrompt =
        "You are a helpful, concise, and accurate AI assistant inside the Toolz productivity app. " +
            "Format code inside markdown fenced code blocks with the language tag. " +
            "Use **bold** for emphasis and keep responses focused and practical. " +
            "When there is uncertainty, say so plainly instead of guessing. " +
            "You are also known as Toolz AI. You have access to a web search tool. " +
            "If the tool is available, use it to provide accurate, cited information for current events or facts outside your training data. " +
            "If you cannot find information, admit it rather than hallucinating. " +
            "When using web search, cite your sources clearly in the text."

    @Inject lateinit var searchBridgeHandler: SearchBridgeHandler
    @Inject lateinit var settingsRepository: com.frerox.toolz.data.settings.SettingsRepository

    // ── getChatResponse ───────────────────────────────────────────────────

    override fun getChatResponse(
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        modelOverride: String?,
    ): Flow<Result<ChatRepository.ChatResponseChunk>> = flow {
        val provider = settingsManager.getAiProvider()
        val keyState = settingsManager.resolveApiKeyWithRemoteSync(provider)
        val modelName = modelOverride ?: settingsManager.getSelectedModel(provider)
        
        val searchEnabled = settingsRepository.aiSearchEnabled.first()
        
        emit(callProvider(provider, keyState, modelName, prompt.trim(), history.takeLast(MAX_HISTORY_MESSAGES), image, searchEnabled))
    }

    // ── testConnection ────────────────────────────────────────────────────

    override fun testConnection(config: AiConfig): Flow<Result<String>> = flow {
        val key = config.apiKey.trim().ifBlank {
            settingsManager.resolveApiKeyWithRemoteSync(config.provider).value
        }
        emit(
            try {
                val result = callProvider(
                    provider = config.provider,
                    keyState = ResolvedApiKey(
                        value = key,
                        source = if (key.isBlank()) ApiKeySource.NONE else ApiKeySource.USER,
                    ),
                    modelName = config.model,
                    prompt = "Reply with exactly: OK",
                    history = emptyList(),
                    image = null,
                    searchEnabled = false
                )
                result.map { it.text }
            } catch (e: Exception) {
                Result.failure(e)
            }
        )
    }

    // ── Core provider dispatch ────────────────────────────────────────────

    private suspend fun callProvider(
        provider: String,
        keyState: ResolvedApiKey,
        modelName: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> = try {
        if (keyState.value.isBlank()) {
            Result.failure(Exception("No API key available for $provider. Please add your own key in settings."))
        } else if (image != null && !AiSettingsHelper.supportsVision(provider, modelName)) {
            Result.failure(Exception("$provider model '$modelName' does not support image input. Choose a vision-capable model or remove the image."))
        } else {
            executeProviderCall(provider, keyState.value, modelName, prompt, history, image, searchEnabled)
        }
    } catch (e: HttpException) {
        if (e.code() == 401 && (keyState.source == ApiKeySource.REMOTE || keyState.source == ApiKeySource.DEFAULT)) {
            refreshRemoteKeyAndRetry(provider, keyState.value, modelName, prompt, history, image, searchEnabled)
        } else {
            Result.failure(Exception(httpErrorMessage(e, provider, keyState.source)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun executeProviderCall(
        provider: String,
        apiKey: String,
        modelName: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> = when (provider) {
        "Gemini" -> callGemini(apiKey, modelName, prompt, history, image, searchEnabled)
        "ChatGPT",
        "Groq",
        "DeepSeek",
        "OpenRouter" -> callOpenAiCompatible(provider, apiKey, modelName, prompt, history, image, searchEnabled)
        "Claude" -> callClaude(apiKey, modelName, prompt, history, image, searchEnabled)
        else -> Result.failure(Exception("Unknown provider: $provider"))
    }

    private suspend fun refreshRemoteKeyAndRetry(
        provider: String,
        failedKey: String,
        modelName: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> {
        settingsManager.invalidateRemoteKey(provider, failedKey)
        val refreshed = settingsManager.syncRemoteKeys(force = true)
        val refreshedKey = settingsManager.resolveApiKey(provider)

        if (!refreshed || (refreshedKey.source != ApiKeySource.REMOTE && refreshedKey.source != ApiKeySource.DEFAULT) || refreshedKey.value.isBlank()) {
            return Result.failure(
                Exception("The Toolz default key for $provider is invalid or unavailable. Please add your own key in settings.")
            )
        }

        if (refreshedKey.value == failedKey) {
            return Result.failure(
                Exception("The Toolz default key for $provider is invalid or unavailable. Please add your own key in settings.")
            )
        }

        return try {
            executeProviderCall(provider, refreshedKey.value, modelName, prompt, history, image, searchEnabled)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                settingsManager.invalidateRemoteKey(provider, refreshedKey.value)
            }
            Result.failure(Exception(httpErrorMessage(e, provider, refreshedKey.source)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Gemini ────────────────────────────────────────────────────────────

    private suspend fun callGemini(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> {
        val generativeModel = GenerativeModel(modelName = model, apiKey = apiKey)
        val effectivePrompt = if (searchEnabled) {
            "You are also known as Toolz AI. You have access to a web search tool. " +
            "I have access to a web search tool. If my prompt requires real-time info, I will use it. " +
            "Current prompt: $prompt"
        } else prompt.ifBlank {
            if (image != null) "Describe this image in detail and highlight anything important." else "Help me with this task."
        }
        return if (image != null) {
            val inputContent = content {
                image(image)
                text(effectivePrompt)
            }
            val text = generativeModel.generateContent(inputContent).text ?: "No response from Gemini"
            Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
        } else {
            val chat = generativeModel.startChat(
                history.map { msg ->
                    content(role = if (msg.isUser) "user" else "model") { text(msg.text) }
                }
            )
            val text = chat.sendMessage(effectivePrompt).text ?: "No response from Gemini"
            Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
        }
    }

    // ── OpenAI-compatible (ChatGPT / Groq / DeepSeek / OpenRouter) ────────

    private suspend fun callOpenAiCompatible(
        provider: String,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> {
        val completionUrl = AiSettingsHelper.getChatCompletionUrl(provider)
            ?: return Result.failure(Exception("No chat completion URL configured for $provider"))
        val effectivePrompt = prompt.ifBlank {
            if (image != null) "Describe this image in detail and highlight anything important." else "Help me with this task."
        }

        val messages = mutableListOf<OpenAiMessage>()
        messages += OpenAiMessage("system", MessageContent.Text(systemPrompt))

        history.forEach { msg ->
            messages += OpenAiMessage(
                role    = if (msg.isUser) "user" else "assistant",
                content = MessageContent.Text(msg.text),
            )
        }

        val userContent: MessageContent = if (image != null) {
            val base64 = bitmapToBase64(image)
            MessageContent.Blocks(
                listOf(
                    ContentBlock(type = "text", text = effectivePrompt),
                    ContentBlock(type = "image_url", imageUrl = ImageUrl("data:image/jpeg;base64,$base64")),
                )
            )
        } else {
            MessageContent.Text(effectivePrompt)
        }
        messages += OpenAiMessage("user", userContent)

        var request = OpenAiRequest(
            model          = model,
            messages       = messages,
            responseFormat = if (effectivePrompt.contains("JSON", ignoreCase = true)) ResponseFormat("json_object") else null,
            tools          = if (searchEnabled) listOf(searchBridgeHandler.getToolDefinition()) else null
        )
        
        var response = openAiService.getChatCompletion(
            url = completionUrl,
            authHeader = "Bearer $apiKey",
            referer = if (provider == "OpenRouter") OPEN_ROUTER_REFERER else null,
            title = if (provider == "OpenRouter") OPEN_ROUTER_TITLE else null,
            request = request,
        )

        val choice = response.choices.firstOrNull() ?: return Result.success(ChatRepository.ChatResponseChunk("No response"))
        
        var searchSources: String? = null
        
        if (choice.message.toolCalls != null && choice.message.toolCalls.isNotEmpty()) {
            val toolCalls = choice.message.toolCalls
            val updatedMessages = messages.toMutableList()
            
            // Add the assistant's tool call message
            updatedMessages += OpenAiMessage(
                role = "assistant",
                content = null,
                toolCalls = toolCalls
            )

            // Handle tool calls
            for (toolCall in toolCalls) {
                val result = searchBridgeHandler.handleToolCall(toolCall)
                updatedMessages += OpenAiMessage(
                    role = "tool",
                    toolCallId = toolCall.id,
                    content = MessageContent.Text(result)
                )
            }
            
            // Serialize search sources
            val sources = searchBridgeHandler.lastSearchResults
            if (sources.isNotEmpty()) {
                val sourcesAdapter = moshi.adapter<List<SearchResult>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, SearchResult::class.java)
                )
                searchSources = sourcesAdapter.toJson(sources)
            }

            // Call again with tool results
            // Ensure tool_choice is not present when sending back tool results to some providers
            request = request.copy(messages = updatedMessages, toolChoice = null)
            response = openAiService.getChatCompletion(
                url = completionUrl,
                authHeader = "Bearer $apiKey",
                referer = if (provider == "OpenRouter") OPEN_ROUTER_REFERER else null,
                title = if (provider == "OpenRouter") OPEN_ROUTER_TITLE else null,
                request = request,
            )
        }

        val text = response.choices.firstOrNull()?.message?.content ?: "No response"
        return Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text), searchSources))
    }

    // ── Claude ────────────────────────────────────────────────────────────

    private suspend fun callClaude(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> {
        val messages = mutableListOf<ClaudeMessage>()
        val effectivePrompt = prompt.ifBlank {
            if (image != null) "Describe this image in detail and highlight anything important." else "Help me with this task."
        }

        // Build history — Claude requires strictly alternating user/assistant turns.
        // Consecutive same-role messages are merged.
        history
            .filter { it.text.isNotBlank() }
            .forEach { msg ->
                val role = if (msg.isUser) "user" else "assistant"
                val last = messages.lastOrNull()
                if (last != null && last.role == role) {
                    val merged = (last.content as? String ?: "") + "\n\n" + msg.text
                    messages[messages.size - 1] = last.copy(content = merged)
                } else {
                    messages += ClaudeMessage(role = role, content = msg.text)
                }
            }

        // Current user turn — with optional image via content blocks
        if (image != null) {
            val base64 = bitmapToBase64(image)
            val blocks: List<Any> = buildList {
                add(ClaudeImageContent(source = ClaudeImageSource(data = base64)))
                add(ClaudeTextContent(text = effectivePrompt))
            }
            val last = messages.lastOrNull()
            if (last != null && last.role == "user") {
                val existing = last.content as? String ?: ""
                val combined: List<Any> = buildList {
                    if (existing.isNotBlank()) add(ClaudeTextContent(text = existing))
                    addAll(blocks)
                }
                messages[messages.size - 1] = last.copy(content = combined)
            } else {
                messages += ClaudeMessage(role = "user", content = blocks)
            }
        } else {
            val last = messages.lastOrNull()
            if (last != null && last.role == "user") {
                val merged = (last.content as? String ?: "") + "\n\n" + effectivePrompt
                messages[messages.size - 1] = last.copy(content = merged)
            } else {
                messages += ClaudeMessage(role = "user", content = effectivePrompt)
            }
        }

        // Claude API requires that the first message is from "user"
        if (messages.firstOrNull()?.role != "user") {
            Log.w(TAG, "Claude: first message is not 'user'; prepending sentinel.")
            messages.add(0, ClaudeMessage(role = "user", content = "."))
        }

        val claudeTools = if (searchEnabled) {
            val tool = searchBridgeHandler.getToolDefinition()
            listOf(ClaudeTool(
                name = tool.function.name,
                description = tool.function.description,
                inputSchema = tool.function.parameters
            ))
        } else null

        var request = ClaudeRequest(
            model    = model,
            messages = messages,
            system   = systemPrompt,
            tools    = claudeTools
        )
        
        var response = openAiService.getClaudeCompletion(
            url     = "https://api.anthropic.com/v1/messages",
            apiKey  = apiKey,
            version = "2023-06-01",
            request = request,
        )

        var searchSources: String? = null
        val toolUseBlocks = response.content.filter { it.type == "tool_use" }

        if (toolUseBlocks.isNotEmpty()) {
            val updatedMessages = messages.toMutableList()
            
            // Add the assistant's tool use message
            val assistantContent = response.content.map { block ->
                when (block.type) {
                    "text" -> ClaudeTextContent(text = block.text ?: "")
                    "tool_use" -> ClaudeToolUseContent(
                        id = block.id ?: "",
                        name = block.name ?: "",
                        input = block.input ?: emptyMap()
                    )
                    else -> ClaudeTextContent(text = "")
                }
            }
            updatedMessages += ClaudeMessage(role = "assistant", content = assistantContent)

            // Handle tool calls
            val toolResults = mutableListOf<ClaudeToolResultContent>()
            for (block in toolUseBlocks) {
                // Adapt Claude tool_use to ToolCall for SearchBridgeHandler
                val toolCall = ToolCall(
                    id = block.id ?: "",
                    function = FunctionCall(
                        name = block.name ?: "",
                        arguments = moshi.adapter(Map::class.java).toJson(block.input)
                    )
                )
                val result = searchBridgeHandler.handleToolCall(toolCall)
                toolResults += ClaudeToolResultContent(
                    toolUseId = block.id ?: "",
                    content = result
                )
            }
            
            updatedMessages += ClaudeMessage(role = "user", content = toolResults)
            
            // Serialize search sources
            val sources = searchBridgeHandler.lastSearchResults
            if (sources.isNotEmpty()) {
                val sourcesAdapter = moshi.adapter<List<SearchResult>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, SearchResult::class.java)
                )
                searchSources = sourcesAdapter.toJson(sources)
            }

            // Call again with tool results
            request = request.copy(messages = updatedMessages)
            response = openAiService.getClaudeCompletion(
                url     = "https://api.anthropic.com/v1/messages",
                apiKey  = apiKey,
                version = "2023-06-01",
                request = request,
            )
        }

        val text = response.content.filter { it.type == "text" }.joinToString("\n") { it.text ?: "" }
        return Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text), searchSources))
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    /**
     * Compresses [bitmap] to JPEG at 80 % quality and returns a Base64 string.
     * [Base64.NO_WRAP] is essential — line-wrapped base64 (the [Base64.DEFAULT]
     * behaviour) is rejected by both the OpenAI and Anthropic vision APIs.
     * [ByteArrayOutputStream.use] guarantees the stream is closed on all paths.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String =
        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }

    private fun cleanResponseText(text: String): String =
        text.replace("\uFEFF", "").trim()

    private fun httpErrorMessage(
        e: HttpException,
        provider: String,
        keySource: ApiKeySource,
    ): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        return when (e.code()) {
            401  -> {
                if (keySource == ApiKeySource.REMOTE || keySource == ApiKeySource.DEFAULT) {
                    "The Toolz default key for $provider is invalid or expired. Please add your own key in settings."
                } else {
                    "Invalid API key for $provider. Please check your settings."
                }
            }
            403  -> "Access denied ($provider). Your key may lack the required permissions."
            429  -> "Rate limit or quota exceeded ($provider). Try again later or switch providers."
            400  -> "Bad request to $provider (400): ${body ?: e.message()}"
            500,
            502,
            503  -> "$provider is temporarily unavailable (${e.code()}). Please try again."
            else -> "HTTP ${e.code()} from $provider: ${body ?: e.message()}"
        }
    }
}
