package com.frerox.toolz.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.WebSearchRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiRepositoryImpl"
private const val MAX_HISTORY_MESSAGES = 24
private const val OPEN_ROUTER_REFERER = "https://github.com/frerox/toolz"
private const val OPEN_ROUTER_TITLE = "Toolz AI"

// ─────────────────────────────────────────────────────────────
//  Request Models
// ─────────────────────────────────────────────────────────────

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

data class Tool(val type: String = "function", val function: ToolDefinition)
data class ToolDefinition(val name: String, val description: String, val parameters: ToolParameters)
data class ToolParameters(val type: String = "object", val properties: Map<String, PropertyDefinition>, val required: List<String>)
data class PropertyDefinition(val type: String, val description: String)
data class ResponseFormat(val type: String)

class MessageContentAdapter {
    @ToJson fun toJson(writer: JsonWriter, content: MessageContent?) {
        if (content == null) { writer.nullValue(); return }
        when (content) {
            is MessageContent.Text -> writer.value(content.value)
            is MessageContent.Blocks -> {
                writer.beginArray()
                content.blocks.forEach { block ->
                    writer.beginObject().name("type").value(block.type)
                    if (block.type == "text") writer.name("text").value(block.text)
                    else if (block.type == "image_url") writer.name("image_url").beginObject().name("url").value(block.imageUrl?.url).endObject()
                    writer.endObject()
                }
                writer.endArray()
            }
        }
    }
    @FromJson fun fromJson(reader: JsonReader): MessageContent = MessageContent.Text(reader.nextString())
}

// ── Claude Models ─────────────────────────────────────────────

data class ClaudeTextContent(val type: String = "text", val text: String)
data class ClaudeImageContent(val type: String = "image", val source: ClaudeImageSource)
data class ClaudeToolResultContent(val type: String = "tool_result", @Json(name = "tool_use_id") val toolUseId: String, val content: String)
data class ClaudeImageSource(val type: String = "base64", @Json(name = "media_type") val mediaType: String = "image/jpeg", val data: String)
data class ClaudeMessage(val role: String, val content: Any)
data class ClaudeRequest(val model: String, val messages: List<ClaudeMessage>, @Json(name = "max_tokens") val maxTokens: Int = 4096, val system: String? = null, val tools: List<ClaudeTool>? = null)
data class ClaudeTool(val name: String, val description: String, @Json(name = "input_schema") val inputSchema: ToolParameters)
data class ClaudeToolUseContent(val type: String = "tool_use", val id: String, val name: String, val input: Map<String, Any>)

class ClaudeMessageAdapter {
    @ToJson fun toJson(writer: JsonWriter, message: ClaudeMessage) {
        writer.beginObject().name("role").value(message.role).name("content")
        when (val c = message.content) {
            is String -> writer.value(c)
            is List<*> -> {
                writer.beginArray()
                c.forEach { block ->
                    writer.beginObject()
                    when (block) {
                        is ClaudeTextContent -> writer.name("type").value(block.type).name("text").value(block.text)
                        is ClaudeImageContent -> writer.name("type").value(block.type).name("source").beginObject().name("type").value(block.source.type).name("media_type").value(block.source.mediaType).name("data").value(block.source.data).endObject()
                        is ClaudeToolResultContent -> writer.name("type").value(block.type).name("tool_use_id").value(block.toolUseId).name("content").value(block.content)
                        is ClaudeToolUseContent -> {
                            writer.name("type").value(block.type).name("id").value(block.id).name("name").value(block.name).name("input").beginObject()
                            block.input.forEach { (k, v) -> writer.name(k).value(v.toString()) }
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
    @FromJson fun fromJson(reader: JsonReader): ClaudeMessage {
        var role = "assistant"; var text = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "role" -> role = reader.nextString()
                "content" -> if (reader.peek() == JsonReader.Token.STRING) text = reader.nextString() else {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        while (reader.hasNext()) { if (reader.nextName() == "text") text = reader.nextString() else reader.skipValue() }
                        reader.endObject()
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ClaudeMessage(role, text)
    }
}

// ─────────────────────────────────────────────────────────────
//  Repository Implementation
// ─────────────────────────────────────────────────────────────

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val settingsManager: AiSettingsManager,
    private val openAiService: OpenAiService,
    private val moshi: Moshi,
    private val settingsRepository: com.frerox.toolz.data.settings.SettingsRepository
) : ChatRepository {

    private val systemPrompt =
        "You are Toolz AI, a professional and highly accurate assistant. " +
                "When search results are provided, you MUST use them to answer the user's query. " +
                "Do NOT say you can't find information if snippets are present below. " +
                "Always cite sources inline using [Title](URL) format. " +
                "When uncertain, state it clearly. Use markdown for formatting. " +
                "Provide a 'Sources' section at the end if web search was used."
    @Inject lateinit var webSearchRepository: WebSearchRepository

    override fun getChatResponse(
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        modelOverride: String?,
    ): Flow<Result<ChatRepository.ChatResponseChunk>> = flow {
        if (settingsRepository.offlineModeEnabled.first()) {
            emit(Result.failure(Exception("AI Assistant is unavailable in offline mode.")))
            return@flow
        }
        val provider = settingsManager.getAiProvider()
        val keyState = settingsManager.resolveApiKeyWithRemoteSync(provider)
        val modelName = modelOverride ?: settingsManager.getSelectedModel(provider)
        val searchEnabled = settingsRepository.aiSearchEnabled.first()
        
        if (searchEnabled) {
            val groqKey = settingsManager.resolveApiKey("Groq")
            if (groqKey.source != ApiKeySource.NONE) {
                // Try to extract a search query. If the model thinks a search is useful, it returns the query.
                val searchQuery = extractSearchQuery(groqKey.value, prompt)
                if (searchQuery != null) {
                    val rawResults = webSearchRepository.search(searchQuery)
                    if (rawResults.isNotEmpty()) {
                        // Pick best 5 sources for the final response
                        val bestResults = selectBestSources(groqKey.value, prompt, rawResults)
                        val contextText = bestResults.joinToString("\n\n") { "TITLE: ${it.title}\nURL: ${it.url}\nSNIPPET: ${it.snippet}" }
                        
                        val sourcesAdapter = moshi.adapter<List<SearchResult>>(Types.newParameterizedType(List::class.java, SearchResult::class.java))
                        val searchSources = sourcesAdapter.toJson(bestResults)
                        
                        val enrichedPrompt = "User Prompt: $prompt\n\n" +
                            "BELOW ARE SEARCH RESULTS FROM THE WEB. USE THEM TO ANSWER:\n$contextText\n\n" +
                            "INSTRUCTIONS:\n" +
                            "1. Answer the prompt using the search results above.\n" +
                            "2. Do NOT claim you cannot find information; use the snippets provided.\n" +
                            "3. Use inline citations [Title](URL).\n" +
                            "4. List all URLs in a 'Sources' section at the end."
                            
                        emit(callProvider(provider, keyState, modelName, enrichedPrompt, history.takeLast(MAX_HISTORY_MESSAGES), image, true).let {
                            if (it.isSuccess) Result.success(it.getOrThrow().copy(sources = searchSources)) else it
                        })
                        return@flow
                    } else {
                        // Search returned nothing - inform the AI
                        val failedPrompt = "User Prompt: $prompt\n\n" +
                            "(Note: A web search for '$searchQuery' was attempted but returned no results. " +
                            "Answer based on your training data, but mention that live search failed to find results.)"
                        emit(callProvider(provider, keyState, modelName, failedPrompt, history.takeLast(MAX_HISTORY_MESSAGES), image, false))
                        return@flow
                    }
                }
            }
        }
        emit(callProvider(provider, keyState, modelName, prompt.trim(), history.takeLast(MAX_HISTORY_MESSAGES), image, false))
    }

    private suspend fun extractSearchQuery(apiKey: String, prompt: String): String? {
        return try {
            val request = OpenAiRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(
                    OpenAiMessage("system", MessageContent.Text("You are an expert searcher. Generate a concise search query for the user prompt. " +
                        "If it's a simple greeting or doesn't need data, output 'NONE'. Otherwise, output ONLY the search query.")),
                    OpenAiMessage("user", MessageContent.Text("User says: $prompt\n\nQuery:"))
                ),
                maxTokens = 40
            )
            val response = openAiService.getChatCompletion("https://api.groq.com/openai/v1/chat/completions", "Bearer $apiKey", null, null, request)
            val query = response.choices.firstOrNull()?.message?.content?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: "NONE"
            if (query.equals("NONE", ignoreCase = true) || query.isBlank()) null else query
        } catch (e: Exception) {
            Log.e(TAG, "Search extraction failed", e)
            null
        }
    }

    private suspend fun selectBestSources(apiKey: String, prompt: String, results: List<SearchResult>): List<SearchResult> {
        if (results.size <= 5) return results
        return try {
            val selectionPrompt = "User Prompt: $prompt\n\nResults:\n" + 
                results.take(15).withIndex().joinToString("\n") { (i, r) -> "[$i] ${r.title}: ${r.snippet}" } +
                "\n\nBased on the user prompt, identify the top 5 most relevant results by index. Respond with ONLY a comma-separated list of numbers, e.g., 0,3,7,2,5"
            
            val request = OpenAiRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(
                    OpenAiMessage("system", MessageContent.Text("You are a search result selector. Reply with ONLY indices.")),
                    OpenAiMessage("user", MessageContent.Text(selectionPrompt))
                ),
                maxTokens = 32
            )
            
            val response = openAiService.getChatCompletion("https://api.groq.com/openai/v1/chat/completions", "Bearer $apiKey", null, null, request)
            val indices = response.choices.firstOrNull()?.message?.content?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
            indices.mapNotNull { results.getOrNull(it) }.distinctBy { it.url }.take(5).ifEmpty { results.take(5) }
        } catch (_: Exception) {
            results.take(5)
        }
    }

    override fun performDeepDive(
        prompt: String,
        sourcesJson: String,
        history: List<AiMessage>
    ): Flow<Result<ChatRepository.ChatResponseChunk>> = flow {
        val provider = settingsManager.getAiProvider()
        val keyState = settingsManager.resolveApiKeyWithRemoteSync(provider)
        val modelName = settingsManager.getSelectedModel(provider)
        val groqKey = settingsManager.resolveApiKey("Groq")

        if (groqKey.source == ApiKeySource.NONE) {
            emit(Result.failure(Exception("Groq key required for deep dive")))
            return@flow
        }

        try {
            val sourcesAdapter = moshi.adapter<List<SearchResult>>(Types.newParameterizedType(List::class.java, SearchResult::class.java))
            val sources = sourcesAdapter.fromJson(sourcesJson) ?: emptyList()
            
            val deepContext = StringBuilder()
            sources.take(3).forEach { source ->
                val content = webSearchRepository.fetchWebsiteContent(source.url)
                val structured = structureWebsiteContent(groqKey.value, source.title, content)
                deepContext.append("SOURCE: ${source.title}\nURL: ${source.url}\nCONTENT: $structured\n\n")
            }

            val finalPrompt = "DEEP DIVE CONTEXT (Fetched from websites):\n$deepContext\n\n" +
                "User original question: $prompt\n\n" +
                "Provide an extremely detailed answer using this full website context. Cite everything."

            // Filter history to avoid consecutive assistant messages (important for Claude/OpenAI)
            val filteredHistory = history.filter { !it.text.contains("dig deeper", ignoreCase = true) }

            emit(callProvider(provider, keyState, modelName, finalPrompt, filteredHistory.takeLast(MAX_HISTORY_MESSAGES), null, false))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private suspend fun structureWebsiteContent(apiKey: String, title: String, content: String): String {
        return try {
            val request = OpenAiRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(
                    OpenAiMessage("system", MessageContent.Text("You are a content structurer. Clean text and keep facts.")),
                    OpenAiMessage("user", MessageContent.Text("Title: $title\n\n$content"))
                ),
                maxTokens = 1024
            )
            val response = openAiService.getChatCompletion("https://api.groq.com/openai/v1/chat/completions", "Bearer $apiKey", null, null, request)
            response.choices.firstOrNull()?.message?.content ?: content.take(1000)
        } catch (_: Exception) {
            content.take(1000)
        }
    }

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

    override suspend fun checkModelAvailability(provider: String, model: String): Boolean {
        // 1. Check hardcoded list first for instant response
        if (AiSettingsHelper.isKnownModel(provider, model)) return true

        // 2. Try to fetch from API if key is available
        val apiKey = settingsManager.resolveApiKey(provider).value
        if (apiKey.isBlank()) return false

        return when (provider) {
            "ChatGPT", "Groq", "DeepSeek", "OpenRouter" -> {
                val baseUrl = when (provider) {
                    "ChatGPT" -> "https://api.openai.com/v1/models"
                    "Groq" -> "https://api.groq.com/openai/v1/models"
                    "DeepSeek" -> "https://api.deepseek.com/v1/models"
                    "OpenRouter" -> "https://openrouter.ai/api/v1/models"
                    else -> return false
                }
                try {
                    val resp = openAiService.listModels(baseUrl, "Bearer $apiKey")
                    resp.data.any { it.id.equals(model, ignoreCase = true) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to list models for $provider", e)
                    false
                }
            }
            "Gemini" -> {
                // Gemini has a different API for listing models, but for now we'll just return false
                // if not in our hardcoded list, or we could try a dummy call.
                false
            }
            "Claude" -> {
                // Anthropic doesn't have a public list models API yet.
                false
            }
            else -> false
        }
    }

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
            Result.failure(Exception("No API key available for $provider"))
        } else if (image != null && !AiSettingsHelper.supportsVision(provider, modelName)) {
            Result.failure(Exception("$provider model '$modelName' does not support vision."))
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
        settingsManager.syncRemoteKeys(force = true)
        val refreshedKey = settingsManager.resolveApiKey(provider)

        if (refreshedKey.value.isBlank() || refreshedKey.value == failedKey) {
            return Result.failure(Exception("Default key for $provider is invalid or unavailable."))
        }

        return try {
            executeProviderCall(provider, refreshedKey.value, modelName, prompt, history, image, searchEnabled)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            "Toolz AI. Web search context provided. Prompt: $prompt"
        } else prompt.ifBlank { if (image != null) "Describe image" else "Help me" }

        return if (image != null) {
            val text = generativeModel.generateContent(content { image(image); text(effectivePrompt) }).text ?: "No response"
            Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
        } else {
            val chat = generativeModel.startChat(history.map { content(role = if (it.isUser) "user" else "model") { text(it.text) } })
            val text = chat.sendMessage(effectivePrompt).text ?: "No response"
            Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
        }
    }

    private suspend fun callOpenAiCompatible(
        provider: String,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> {
        val url = AiSettingsHelper.getChatCompletionUrl(provider) ?: return Result.failure(Exception("No URL for $provider"))
        val messages = mutableListOf<OpenAiMessage>()
        messages += OpenAiMessage("system", MessageContent.Text(systemPrompt))

        history.forEach { msg ->
            messages += OpenAiMessage(role = if (msg.isUser) "user" else "assistant", content = MessageContent.Text(msg.text))
        }

        val userContent = if (image != null) {
            MessageContent.Blocks(listOf(ContentBlock("text", prompt), ContentBlock("image_url", imageUrl = ImageUrl("data:image/jpeg;base64,${bitmapToBase64(image)}"))))
        } else MessageContent.Text(prompt)
        messages += OpenAiMessage("user", userContent)

        val response = openAiService.getChatCompletion(url, "Bearer $apiKey", if (provider == "OpenRouter") OPEN_ROUTER_REFERER else null, if (provider == "OpenRouter") OPEN_ROUTER_TITLE else null, OpenAiRequest(model, messages, tools = null))
        val text = response.choices.firstOrNull()?.message?.content ?: "No response"
        return Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
    }

    private suspend fun callClaude(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean
    ): Result<ChatRepository.ChatResponseChunk> {
        val messages = mutableListOf<ClaudeMessage>()
        history.filter { it.text.isNotBlank() }.forEach { msg ->
            val role = if (msg.isUser) "user" else "assistant"
            val last = messages.lastOrNull()
            if (last != null && last.role == role) {
                messages[messages.size - 1] = last.copy(content = (last.content as String) + "\n\n" + msg.text)
            } else {
                messages += ClaudeMessage(role, msg.text)
            }
        }
        val userBlocks = if (image != null) listOf(ClaudeImageContent(source = ClaudeImageSource(data = bitmapToBase64(image))), ClaudeTextContent(text = prompt)) else listOf(ClaudeTextContent(text = prompt))
        val last = messages.lastOrNull()
        if (last != null && last.role == "user") {
            val existing = last.content as? String ?: ""
            val combined: List<Any> = if (existing.isNotBlank()) listOf(ClaudeTextContent(text = existing)) + userBlocks else userBlocks
            messages[messages.size - 1] = last.copy(content = combined)
        } else {
            messages += ClaudeMessage("user", userBlocks)
        }
        if (messages.firstOrNull()?.role != "user") messages.add(0, ClaudeMessage("user", "."))

        val response = openAiService.getClaudeCompletion("https://api.anthropic.com/v1/messages", apiKey, "2023-06-01", ClaudeRequest(model, messages, system = systemPrompt))
        val text = response.content.filter { it.type == "text" }.joinToString("\n") { it.text ?: "" }
        return Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
    }

    private fun bitmapToBase64(bitmap: Bitmap): String = ByteArrayOutputStream().use { bos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos); Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP) }
    private fun cleanResponseText(text: String): String = text.replace("\uFEFF", "").trim()
    private fun httpErrorMessage(e: HttpException, provider: String, keySource: ApiKeySource): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        return when (e.code()) {
            401 -> "Invalid $provider API key."
            403 -> "Access denied ($provider)."
            429 -> "Rate limit ($provider)."
            400 -> "Bad request ($provider): ${body ?: e.message()}"
            else -> "HTTP ${e.code()} from $provider."
        }
    }
}
