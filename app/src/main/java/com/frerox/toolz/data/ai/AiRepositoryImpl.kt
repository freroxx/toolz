package com.frerox.toolz.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiRepository"

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
    val content: MessageContent,
)

/**
 * DO NOT add @JsonClass(generateAdapter = true) here.
 *
 * OpenAiRequest contains List<OpenAiMessage>, whose [content] field is
 * serialized by the custom [MessageContentAdapter]. Moshi codegen generates
 * an adapter at compile time that has no awareness of runtime-registered
 * custom adapters, so it fails to serialize [MessageContent].
 *
 * Without the annotation, Moshi uses KotlinJsonAdapterFactory reflection,
 * which looks up registered adapters per field type at runtime and correctly
 * finds [MessageContentAdapter] for the [content] field.
 *
 * @Json annotations are honoured by the reflection adapter, so
 * "max_tokens" and "response_format" still serialize with the right names.
 */
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    @Json(name = "response_format") val responseFormat: ResponseFormat? = null,
)

// No @JsonClass here — used inside OpenAiRequest, same reasoning applies.
data class ResponseFormat(val type: String)

// ─────────────────────────────────────────────────────────────
//  Moshi adapter — MessageContent  (String | ContentBlock[])
// ─────────────────────────────────────────────────────────────

class MessageContentAdapter {

    @ToJson
    fun toJson(writer: JsonWriter, content: MessageContent) {
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
)

// ─────────────────────────────────────────────────────────────
//  Moshi adapter — ClaudeMessage  (String content | Block[] content)
// ─────────────────────────────────────────────────────────────

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
) : ChatRepository {

    private val systemPrompt =
        "You are a helpful, concise, and accurate AI assistant inside the Toolz productivity app. " +
                "Format code inside markdown fenced code blocks with the language tag. " +
                "Use **bold** for emphasis and keep responses focused and practical."

    // ── getChatResponse ───────────────────────────────────────────────────

    override fun getChatResponse(
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        modelOverride: String?,
    ): Flow<Result<String>> = flow {
        val provider  = settingsManager.getAiProvider()
        val apiKey    = settingsManager.getApiKey(provider)
        val modelName = modelOverride ?: settingsManager.getSelectedModel(provider)
        emit(callProvider(provider, apiKey, modelName, prompt, history, image))
    }

    // ── testConnection ────────────────────────────────────────────────────

    override fun testConnection(config: AiConfig): Flow<Result<String>> = flow {
        val key = config.apiKey.ifBlank { AiSettingsHelper.getDefaultKey(config.provider) }
        emit(
            try {
                callProvider(
                    provider  = config.provider,
                    apiKey    = key,
                    modelName = config.model,
                    prompt    = "Reply with exactly: OK",
                    history   = emptyList(),
                    image     = null,
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        )
    }

    // ── Core provider dispatch ────────────────────────────────────────────

    private suspend fun callProvider(
        provider: String,
        apiKey: String,
        modelName: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
    ): Result<String> = try {
        when (provider) {
            "Gemini"     -> callGemini(apiKey, modelName, prompt, history, image)
            "ChatGPT",
            "Groq",
            "DeepSeek",
            "OpenRouter" -> callOpenAiCompatible(provider, apiKey, modelName, prompt, history, image)
            "Claude"     -> callClaude(apiKey, modelName, prompt, history, image)
            else         -> Result.failure(Exception("Unknown provider: $provider"))
        }
    } catch (e: HttpException) {
        Result.failure(Exception(httpErrorMessage(e, provider)))
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ── Gemini ────────────────────────────────────────────────────────────

    private suspend fun callGemini(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
    ): Result<String> {
        val generativeModel = GenerativeModel(modelName = model, apiKey = apiKey)
        return if (image != null) {
            val inputContent = content {
                image(image)
                text(prompt.ifBlank { "Describe this image in detail." })
            }
            Result.success(generativeModel.generateContent(inputContent).text ?: "No response from Gemini")
        } else {
            val chat = generativeModel.startChat(
                history.map { msg ->
                    content(role = if (msg.isUser) "user" else "model") { text(msg.text) }
                }
            )
            Result.success(chat.sendMessage(prompt).text ?: "No response from Gemini")
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
    ): Result<String> {
        val baseUrl = when (provider) {
            "ChatGPT"    -> "https://api.openai.com/v1/"
            "Groq"       -> "https://api.groq.com/openai/v1/"
            "DeepSeek"   -> "https://api.deepseek.com/v1/"
            "OpenRouter" -> "https://openrouter.ai/api/v1/"
            else         -> "https://api.openai.com/v1/"
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
                    ContentBlock(type = "text",      text     = prompt.ifBlank { "Describe this image." }),
                    ContentBlock(type = "image_url", imageUrl = ImageUrl("data:image/jpeg;base64,$base64")),
                )
            )
        } else {
            MessageContent.Text(prompt)
        }
        messages += OpenAiMessage("user", userContent)

        val request = OpenAiRequest(
            model          = model,
            messages       = messages,
            responseFormat = if (prompt.contains("JSON", ignoreCase = true)) ResponseFormat("json_object") else null,
        )
        val response = openAiService.getChatCompletion("${baseUrl}chat/completions", "Bearer $apiKey", request)
        return Result.success(response.choices.firstOrNull()?.message?.content ?: "No response")
    }

    // ── Claude ────────────────────────────────────────────────────────────

    private suspend fun callClaude(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
    ): Result<String> {
        val messages = mutableListOf<ClaudeMessage>()

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
                add(ClaudeTextContent(text = prompt.ifBlank { "Describe this image in detail." }))
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
                val merged = (last.content as? String ?: "") + "\n\n" + prompt
                messages[messages.size - 1] = last.copy(content = merged)
            } else {
                messages += ClaudeMessage(role = "user", content = prompt)
            }
        }

        // Claude API requires that the first message is from "user"
        if (messages.firstOrNull()?.role != "user") {
            Log.w(TAG, "Claude: first message is not 'user'; prepending sentinel.")
            messages.add(0, ClaudeMessage(role = "user", content = "."))
        }

        val request = ClaudeRequest(
            model    = model,
            messages = messages,
            system   = systemPrompt,
        )
        val response = openAiService.getClaudeCompletion(
            url     = "https://api.anthropic.com/v1/messages",
            apiKey  = apiKey,
            version = "2023-06-01",
            request = request,
        )
        return Result.success(response.content.firstOrNull()?.text ?: "No response from Claude")
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

    private fun httpErrorMessage(e: HttpException, provider: String): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        return when (e.code()) {
            401  -> "Invalid API key for $provider. Please check your settings."
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