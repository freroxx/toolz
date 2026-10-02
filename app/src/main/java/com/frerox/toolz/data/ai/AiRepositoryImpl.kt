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
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiRepositoryImpl"
private const val MAX_HISTORY_MESSAGES = 24
private const val OPEN_ROUTER_REFERER = "https://github.com/frerox/toolz"
private const val OPEN_ROUTER_TITLE = "Toolz AI"
private const val MAX_API_ATTEMPTS = 3
private const val RETRY_BASE_DELAY_MS = 600L

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
                "Cite inline using [Title](URL) format, using ONLY the URLs given — " +
                "never invent URLs. Do NOT write a 'Sources' section; the app renders " +
                "the source list separately. " +
                "When uncertain, state it clearly. Use markdown for formatting."
    @Inject lateinit var webSearchRepository: WebSearchRepository

    override fun getChatResponse(
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        modelOverride: String?,
        providerOverride: String?,
        systemPromptOverride: String?
    ): Flow<Result<ChatRepository.ChatResponseChunk>> = flow {
        if (settingsRepository.offlineModeEnabled.first()) {
            emit(Result.failure(Exception("AI Assistant is unavailable in offline mode.")))
            return@flow
        }
        val rawProvider = providerOverride ?: settingsManager.getAiProvider()
        val provider = AiSettingsHelper.canonicalProvider(rawProvider)
        val keyState = settingsManager.resolveApiKey(provider)
        // Blank stored model means "never chosen" — fall back to recommended instead of sending "".
        val rawStored = (modelOverride ?: settingsManager.getSelectedModel(provider)).ifBlank {
            AiSettingsHelper.getRecommendedModel(provider)
        }
        val modelName = AiSettingsHelper.migrateRetiredModel(provider, rawStored)
            ?.also { migrated ->
                Log.w(TAG, "Migrated retired $provider model '$rawStored' -> '$migrated'")
                runCatching { settingsManager.setSelectedModel(migrated, provider) }
            } ?: rawStored
        val searchEnabled = settingsRepository.aiSearchEnabled.first()

        if (searchEnabled && needsWebSearchHeuristic(prompt)) {
            // Resolve a key for query extraction: prefer current provider, else Groq, else Zen/Go.
            val extractionKey = resolveSearchHelperKey(provider)
            val searchQuery = if (extractionKey != null) {
                extractSearchQuery(extractionKey.first, extractionKey.second, prompt)
                    ?: heuristicSearchQuery(prompt)
            } else {
                heuristicSearchQuery(prompt)
            }
            if (searchQuery != null) {
                val rawResults = try {
                    // AI always uses the dedicated META fan-out (multiple unique results),
                    // independent of the user's Search-tab engine setting.
                    webSearchRepository.searchForAi(searchQuery, maxResults = 10)
                } catch (e: Exception) {
                    Log.e(TAG, "Web search failed for '$searchQuery'", e)
                    emptyList()
                }
                if (rawResults.isNotEmpty()) {
                    // searchForAi already returns canonical-deduped + consensus-ranked.
                    // Re-dedupe defensively (canonical) in case ranking helpers change order.
                    val deduped = rawResults.distinctBy { canonicalSourceUrl(it.url) }
                    // Rank locally first (fast, free), then optionally refine with LLM top-8.
                    val ranked = rankResultsLocally(prompt, deduped)
                    val bestResults = if (extractionKey != null) {
                        selectBestSources(extractionKey.first, extractionKey.second, prompt, ranked)
                    } else ranked.take(8)
                    // Cap snippet length + mark untrusted to reduce prompt-injection surface.
                    // The bottom sheet (searchSources JSON) is the ONLY rendered source list —
                    // the model must NOT emit its own 'Sources' section.
                    val contextText = bestResults.joinToString("\n\n") {
                        "TITLE: ${it.title.take(200)}\nURL: ${it.url}\nSNIPPET: <untrusted>${it.snippet.take(500)}</untrusted>"
                    }

                    val sourcesAdapter = moshi.adapter<List<SearchResult>>(Types.newParameterizedType(List::class.java, SearchResult::class.java))
                    val searchSources = sourcesAdapter.toJson(bestResults)

                    val enrichedPrompt = "User Prompt: ${prompt.take(4000)}\n\n" +
                        "BELOW ARE LIVE WEB SEARCH RESULTS (query: \"$searchQuery\"). USE THEM TO ANSWER:\n$contextText\n\n" +
                        "INSTRUCTIONS:\n" +
                        "1. Answer the prompt using the search results above. Prefer fresh facts over training data.\n" +
                        "2. Do NOT claim you cannot find information; use the snippets provided.\n" +
                        "3. Cite inline as [Title](URL) using ONLY the URLs given above. Never invent URLs.\n" +
                        "4. Do NOT write a 'Sources' section — the app renders the source list separately."

                    emit(callProvider(provider, keyState, modelName, enrichedPrompt, history.takeLast(MAX_HISTORY_MESSAGES), image, true, systemPromptOverride).let {
                        if (it.isSuccess) Result.success(stripSourcesSection(it.getOrThrow().text).let { cleaned ->
                            it.getOrThrow().copy(text = cleaned, sources = searchSources)
                        }) else it
                    })
                    return@flow
                } else {
                    // Search returned nothing - inform the AI so it can still answer
                    val failedPrompt = "User Prompt: $prompt\n\n" +
                        "(Note: A live web search for '$searchQuery' returned no results " +
                        "(engine rate-limit or no coverage). Answer from training data and " +
                        "mention live search came up empty.)"
                    emit(callProvider(provider, keyState, modelName, failedPrompt, history.takeLast(MAX_HISTORY_MESSAGES), image, false, systemPromptOverride))
                    return@flow
                }
            }
        }
        emit(callProvider(provider, keyState, modelName, prompt.trim(), history.takeLast(MAX_HISTORY_MESSAGES), image, false, systemPromptOverride))
    }

    // ── Web-search helpers ──────────────────────────────────────────

    /**
     * Canonical URL for AI dedup — mirrors MetaMerger.canonicalUrl without a
     * new DI edge. Keeps the bottom-sheet JSON and the prompt in sync so the
     * same page never appears twice under different tracking params.
     */
    internal fun canonicalSourceUrl(url: String): String = try {
        val n = url.trim().let {
            if (it.startsWith("http://", ignoreCase = true)) "https://" + it.substring(7) else it
        }
        val uri = java.net.URI(n)
        val host = uri.host?.lowercase(java.util.Locale.ROOT)?.removePrefix("www.")?.removePrefix("m.") ?: ""
        val path = (uri.path ?: "").removeSuffix("/").lowercase(java.util.Locale.ROOT)
        val q = uri.query?.split("&")
            ?.filterNot {
                val k = it.substringBefore("=").lowercase(java.util.Locale.ROOT)
                k.startsWith("utm_") || k in setOf(
                    "ref", "fbclid", "gclid", "msclkid", "yclid", "mc_cid",
                    "igshid", "wbraid", "gbraid"
                )
            }
            ?.joinToString("&")?.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
        if (host.isNotEmpty()) "https://$host$path$q"
        else n.lowercase(java.util.Locale.ROOT).removeSuffix("/").substringBefore("#")
    } catch (_: Exception) {
        url.trim().substringBefore("#").removeSuffix("/").lowercase(java.util.Locale.ROOT)
    }

    /**
     * Safety net: strip a model-generated trailing 'Sources' section if the
     * model ignored the no-Sources instruction. The bottom sheet JSON is the
     * single source of truth, so removing this block never loses data.
     *
     * Tolerant of `## Sources`, `**Sources:**`, `__Sources__`, `Source:`,
     * emoji-prefixed headers etc. — a header line counts when it contains
     * nothing but the word sources/source/references. Conservative by design:
     * the tail must look like a link list (bare/markdown URLs, bullets,
     * numbers) or nothing is stripped.
     */
    internal fun stripSourcesSection(text: String): String {
        val lines = text.lines()
        val headerIdx = lines.indexOfFirst { isSourcesHeader(it) }
        if (headerIdx == -1) return text
        val tail = lines.drop(headerIdx + 1)
        if (tail.none { it.isNotBlank() }) return text
        val tailText = tail.joinToString("\n")
        if (!tailText.contains("http", ignoreCase = true) && !tailText.contains("](")) return text
        val listShaped = tail.all { ln ->
            val t = ln.trim()
            t.isBlank() || t.startsWith("-") || t.startsWith("*") || t.startsWith("+") ||
                t.startsWith(">") || t.startsWith("[") ||
                t.matches(Regex("^\\d+[.)].*")) ||
                t.contains("http") || t.contains("](")
        }
        if (!listShaped) return text
        return lines.take(headerIdx).joinToString("\n").trimEnd()
    }

    private fun isSourcesHeader(line: String): Boolean {
        // Keep only letters: "## **Sources:**" -> "sources"; a prose line like
        // "Sources of protein include..." keeps other letters and never matches.
        val letters = line.trim().filter { it.isLetter() }.lowercase(java.util.Locale.ROOT)
        return letters == "sources" || letters == "source" ||
            letters == "references" || letters == "reference"
    }

    /** Cheap local gate so greetings / chit-chat skip the search pipeline entirely. */
    private fun needsWebSearchHeuristic(prompt: String): Boolean {
        val p = prompt.trim().lowercase(java.util.Locale.ROOT)
        if (p.length < 4) return false
        val noSearch = listOf(
            "hi", "hello", "hey", "thanks", "thank you", "bye", "good morning",
            "good afternoon", "good evening", "how are you", "who are you"
        )
        if (noSearch.any { p == it || p.startsWith("$it ") || p.startsWith("$it?") || p.startsWith("$it!") }) return false
        // Code / creative / chat intents never need live search.
        val codeHints = listOf("write code", "write a function", "debug", "explain code", "regex", "sql query", "python", "kotlin", "java ", "javascript")
        if (codeHints.any { p.contains(it) }) return false
        // Explicit user intent always searches
        if (p.contains("search") || p.contains("latest") || p.contains("today") ||
            p.contains("news") || p.contains("price") || p.contains("score") ||
            p.contains("weather") || p.contains("who won") || p.contains("release")
        ) return true
        val words = p.split(Regex("\\s+")).filter { it.isNotBlank() }
        // Questions about current/factual topics default to search; plain chat needs more words.
        if (p.endsWith("?") && words.size >= 4) return true
        if (words.size >= 6) return true
        return false
    }

    /** Local keyword query when LLM extraction is unavailable or says NONE. */
    private fun heuristicSearchQuery(prompt: String): String? {
        var cleaned = prompt.trim()
        // Case-insensitive prefix strip (original removePrefix was case-sensitive).
        listOf("search for", "search", "google").forEach { prefix ->
            if (cleaned.lowercase(java.util.Locale.ROOT).startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length)
            }
        }
        cleaned = cleaned.trim(' ', ':', '-', '?', '!', '.', '"', '\'')
        if (cleaned.length < 3) return null
        // Cap length so engine URLs stay short
        return cleaned.take(180)
    }

    /** Pick any usable key for the tiny helper LLM calls (extraction / ranking). */
    private fun resolveSearchHelperKey(currentProvider: String): Pair<String, String>? {
        // Prefer current provider (keeps quota in one place), then Groq, then Zen/Go.
        val order = listOf(currentProvider, "Groq", "OpenCode Zen", "OpenCode Go", "OpenRouter", "DeepSeek")
            .distinct()
        for (p in order) {
            val key = settingsManager.resolveApiKey(p).value
            if (key.isNotBlank()) {
                val helperModel = when (AiSettingsHelper.canonicalProvider(p)) {
                    "Groq" -> "openai/gpt-oss-20b"
                    "OpenCode Zen" -> "muse-spark-1.3-contributor-free"
                    "OpenCode Go" -> "glm-5.3-flash"
                    else -> settingsManager.getSelectedModel(p)
                }
                return p to helperModel
            }
        }
        return null
    }

    /** Local BM25-lite ranking: overlap of query terms + title boost + freshness. */
    private fun rankResultsLocally(prompt: String, results: List<SearchResult>): List<SearchResult> {
        if (results.size <= 8) return results
        // Note: [^a-z0-9] drops CJK; keep as cheap pre-filter only, consensus still applies.
        val terms = prompt.lowercase(java.util.Locale.ROOT).split(Regex("[^a-z0-9\\p{L}]+"))
            .filter { it.length >= 2 }.toSet()
        if (terms.isEmpty()) return results.take(10)
        return results.map { r ->
            val hay = "${r.title} ${r.snippet}".lowercase(java.util.Locale.ROOT)
            var score = 0.0
            terms.forEach { t ->
                if (r.title.lowercase(java.util.Locale.ROOT).contains(t)) score += 3.0 else if (hay.contains(t)) score += 1.0
            }
            // Prefer results corroborated by multiple engines + with dates
            score += r.engines.size * 0.5
            if (!r.date.isNullOrBlank()) score += 0.3
            // Penalize very short snippets (usually nav junk)
            if (r.snippet.length < 60) score -= 1.0
            r to score
        }.sortedByDescending { it.second }.map { it.first }.take(12)
    }

    private suspend fun extractSearchQuery(provider: String, model: String, prompt: String): String? {
        val canonical = AiSettingsHelper.canonicalProvider(provider)
        val url = AiSettingsHelper.getChatCompletionUrl(canonical) ?: return heuristicSearchQuery(prompt)
        val apiKey = settingsManager.resolveApiKey(canonical).value.ifBlank { return heuristicSearchQuery(prompt) }
        val modelsToTry = listOf(model) + AiSettingsHelper.getFallbackModels(canonical)
        for (modelName in modelsToTry.distinct().take(2)) {
            try {
                val request = OpenAiRequest(
                    model = modelName,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text("You are an expert searcher. Generate a concise search query for the user prompt. " +
                            "Strip chit-chat. If it's a simple greeting or doesn't need live data, output 'NONE'. Otherwise, output ONLY the search query, no quotes.")),
                        OpenAiMessage("user", MessageContent.Text("User says: $prompt\n\nQuery:"))
                    ),
                    maxTokens = 40
                )
                val response = callChatCompletionWithRetry(
                    provider = canonical,
                    url = url,
                    apiKey = apiKey,
                    request = request
                )
                val query = response.choices.firstOrNull()?.message?.content?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: "NONE"
                if (query.equals("NONE", ignoreCase = true) || query.isBlank()) {
                    // Greeting → no search, but long factual prompts still get heuristic query
                    return if (prompt.trim().split(" ").size >= 6) heuristicSearchQuery(prompt) else null
                }
                return query
            } catch (e: Exception) {
                Log.e(TAG, "Search extraction failed with $modelName, trying fallback if available", e)
            }
        }
        return heuristicSearchQuery(prompt)
    }

    private suspend fun selectBestSources(provider: String, model: String, prompt: String, results: List<SearchResult>): List<SearchResult> {
        // Dedupe canonically FIRST so the LLM never sees the same page twice.
        val unique = results.distinctBy { canonicalSourceUrl(it.url) }
        if (unique.size <= 8) return unique
        val canonical = AiSettingsHelper.canonicalProvider(provider)
        val url = AiSettingsHelper.getChatCompletionUrl(canonical)?.takeIf { it.isNotBlank() } ?: return unique.take(8)
        val apiKey = settingsManager.resolveApiKey(canonical).value.ifBlank { return unique.take(8) }
        val modelsToTry = listOf(model) + AiSettingsHelper.getFallbackModels(canonical)
        for (modelName in modelsToTry.distinct().take(2)) {
            try {
                // Give the selector consensus metadata so META agreement isn't discarded.
                val selectionPrompt = "User Prompt: ${prompt.take(1000)}\n\nResults:\n" +
                    unique.take(12).withIndex().joinToString("\n") { (i, r) ->
                        "[$i] ${r.title.take(160)} | engines:${r.engines.joinToString("+").ifBlank { r.source }} | date:${r.date ?: "n/a"} | ${r.snippet.take(220)}"
                    } +
                    "\n\nBased on the user prompt, identify the top 8 most relevant results by index. Respond with ONLY a comma-separated list of numbers, e.g., 0,3,7,2,5"

                val request = OpenAiRequest(
                    model = modelName,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text("You are a search result selector. Reply with ONLY indices.")),
                        OpenAiMessage("user", MessageContent.Text(selectionPrompt))
                    ),
                    maxTokens = 48
                )

                val response = callChatCompletionWithRetry(
                    provider = canonical,
                    url = url,
                    apiKey = apiKey,
                    request = request
                )
                val indices = response.choices.firstOrNull()?.message?.content?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
                return indices.mapNotNull { unique.getOrNull(it) }.distinctBy { canonicalSourceUrl(it.url) }.take(8).ifEmpty { unique.take(8) }
            } catch (e: Exception) {
                Log.e(TAG, "Source selection failed with $modelName, trying fallback if available", e)
            }
        }
        return unique.take(8)
    }

    override fun performDeepDive(
        prompt: String,
        sourcesJson: String,
        history: List<AiMessage>
    ): Flow<Result<ChatRepository.ChatResponseChunk>> = flow {
        val rawProvider = settingsManager.getAiProvider()
        val provider = AiSettingsHelper.canonicalProvider(rawProvider)
        val keyState = settingsManager.resolveApiKey(provider)
        val rawStored = settingsManager.getSelectedModel(provider).ifBlank {
            AiSettingsHelper.getRecommendedModel(provider)
        }
        val modelName = AiSettingsHelper.migrateRetiredModel(provider, rawStored) ?: rawStored
        val helperKey = resolveSearchHelperKey(provider)

        if (helperKey == null) {
            emit(Result.failure(Exception("Add any API key (Groq / Zen / current provider) for deep dive")))
            return@flow
        }

        try {
            val sourcesAdapter = moshi.adapter<List<SearchResult>>(Types.newParameterizedType(List::class.java, SearchResult::class.java))
            val sources = (sourcesAdapter.fromJson(sourcesJson) ?: emptyList())
                .distinctBy { canonicalSourceUrl(it.url) }

            val deepContext = StringBuilder()
            val fetched = mutableListOf<SearchResult>()
            sources.take(3).forEach { source ->
                val content = webSearchRepository.fetchWebsiteContent(source.url)
                if (content.startsWith("Error:")) return@forEach
                val structured = structureWebsiteContent(helperKey.first, helperKey.second, source.title, content)
                deepContext.append("SOURCE: ${source.title.take(200)}\nURL: ${source.url}\nCONTENT: <untrusted>${structured.take(1200)}</untrusted>\n\n")
                fetched.add(source)
            }
            if (deepContext.isBlank()) {
                emit(Result.failure(Exception("Deep dive failed: could not fetch any source content.")))
                return@flow
            }
            // The deep-dive answer gets its own sources payload so the UI can
            // show the Sources bottom-sheet button on the result message too.
            val usedSourcesJson = sourcesAdapter.toJson(fetched)

            val finalPrompt = "DEEP DIVE CONTEXT (Fetched live from websites):\n$deepContext\n\n" +
                "User original question: ${prompt.take(4000)}\n\n" +
                "Provide an extremely detailed answer using this full website context. " +
                "Cite inline with [Title](URL) using ONLY the URLs above. Do NOT write a 'Sources' section."

            // Filter history to avoid consecutive assistant messages (important for Claude/OpenAI)
            val filteredHistory = history.filter { !it.text.contains("dig deeper", ignoreCase = true) }

            emit(callProvider(provider, keyState, modelName, finalPrompt, filteredHistory.takeLast(MAX_HISTORY_MESSAGES), null, false, null).let {
                if (it.isSuccess) {
                    val cleaned = stripSourcesSection(it.getOrThrow().text)
                    Result.success(it.getOrThrow().copy(text = cleaned, sources = usedSourcesJson))
                } else it
            })
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private suspend fun structureWebsiteContent(provider: String, model: String, title: String, content: String): String {
        if (content.isBlank() || content.startsWith("Error:")) return content.take(1000)
        val canonical = AiSettingsHelper.canonicalProvider(provider)
        val url = AiSettingsHelper.getChatCompletionUrl(canonical) ?: return content.take(2000)
        val apiKey = settingsManager.resolveApiKey(canonical).value.ifBlank { return content.take(2000) }
        val modelsToTry = (listOf(model) + AiSettingsHelper.getFallbackModels(canonical)).distinct().take(2)
        for (modelName in modelsToTry) {
            try {
                val request = OpenAiRequest(
                    model = modelName,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text("You are a content structurer. Clean boilerplate, keep facts, dates, numbers, quotes. Max 1200 chars.")),
                        OpenAiMessage("user", MessageContent.Text("Title: $title\n\n${content.take(6000)}"))
                    ),
                    maxTokens = 1024
                )
                val response = callChatCompletionWithRetry(
                    provider = canonical,
                    url = url,
                    apiKey = apiKey,
                    request = request
                )
                return response.choices.firstOrNull()?.message?.content ?: content.take(1000)
            } catch (e: Exception) {
                Log.e(TAG, "Website structuring failed with $modelName", e)
            }
        }
        return content.take(2000)
    }

    override fun testConnection(config: AiConfig): Flow<Result<String>> = flow {
        val key = config.apiKey.trim().ifBlank {
            settingsManager.resolveApiKey(config.provider).value
        }
        val model = config.model.ifBlank { AiSettingsHelper.getRecommendedModel(config.provider) }
        emit(
            try {
                val result = callProvider(
                    provider = config.provider,
                    keyState = ResolvedApiKey(
                        value = key,
                        source = if (key.isBlank()) ApiKeySource.NONE else ApiKeySource.USER,
                    ),
                    modelName = model,
                    prompt = "Reply with exactly: OK",
                    history = emptyList(),
                    image = null,
                    searchEnabled = false,
                    systemPromptOverride = null,
                    // Contract: test must not mutate global selected-model state.
                    persistFallback = false,
                )
                result.map { it.text }
            } catch (e: Exception) {
                Result.failure(e)
            }
        )
    }

    override suspend fun checkModelAvailability(provider: String, model: String): Boolean {
        val canonical = AiSettingsHelper.canonicalProvider(provider)
        // 1. Check hardcoded list first for instant response
        if (AiSettingsHelper.isKnownModel(canonical, model)) return true

        // 2. Try to fetch from API if key is available
        val apiKey = settingsManager.resolveApiKey(canonical).value
        if (apiKey.isBlank()) return false

        return when (canonical) {
            "ChatGPT", "Groq", "DeepSeek", "OpenRouter", "OpenCode Zen", "OpenCode Go" -> {
                val baseUrl = AiSettingsHelper.getModelsUrl(canonical) ?: return false
                try {
                    val (referer, title) = referralHeaders(canonical)
                    val resp = openAiService.listModels(baseUrl, "Bearer $apiKey", referer, title)
                    resp.data.any { it.id.equals(model, ignoreCase = true) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to list models for $canonical", e)
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

    // ── Retry core: 3 attempts, exponential backoff, 404 migration ──

    private fun isModelNotFound(e: Throwable): Boolean {
        if (e is HttpException && e.code() == 404) return true
        // 410 Gone = retired model; 400 with model mention = bad model id.
        if (e is HttpException && (e.code() == 410 || e.code() == 400)) {
            val body = runCatching { e.response()?.errorBody()?.string().orEmpty() }
                .getOrDefault("").lowercase(java.util.Locale.ROOT)
            val msg = (e.message ?: "").lowercase(java.util.Locale.ROOT) + " " + body
            if (msg.contains("model")) return true
        }
        val msg = (e.message ?: "").lowercase(java.util.Locale.ROOT)
        // Tightened: bare "does not exist" without 404/model context caused false positives.
        return (msg.contains("404") && (msg.contains("model") || msg.contains("not found") || msg.contains("decommissioned"))) ||
            msg.contains("model_not_found") || msg.contains("model not found") ||
            msg.contains("decommissioned") ||
            (msg.contains("does not exist") && msg.contains("model"))
    }

    private fun isRateLimited(e: Throwable): Boolean {
        if (e is HttpException && (e.code() == 429 || e.code() == 503 || e.code() == 502 || e.code() == 500 || e.code() == 504)) return true
        val msg = (e.message ?: "").lowercase(java.util.Locale.ROOT)
        return msg.contains("429") || msg.contains("rate limit") || msg.contains("quota") ||
            msg.contains("503") || msg.contains("502") || msg.contains("overloaded") ||
            msg.contains("temporarily unavailable") || msg.contains("try again later")
    }

    private fun isRetryable(e: Throwable): Boolean {
        if (e is HttpException) {
            return when (e.code()) {
                429, 500, 502, 503, 504 -> true
                else -> false
            }
        }
        // Moshi parse errors / auth errors must never retry.
        val name = e.javaClass.name
        if (name.contains("JsonDataException") || name.contains("JsonEncodingException")) return false
        val msg = (e.message ?: "").lowercase(java.util.Locale.ROOT)
        if (msg.contains("unauthorized") || msg.contains("invalid api key") || msg.contains("401") || msg.contains("403")) return false
        return msg.contains("timeout") || msg.contains("unavailable") || msg.contains("deadline") ||
            msg.contains("network") || msg.contains("500") || msg.contains("502") || msg.contains("503")
    }

    private fun referralHeaders(provider: String): Pair<String?, String?> = when (AiSettingsHelper.canonicalProvider(provider)) {
        "OpenRouter", "OpenCode Zen", "OpenCode Go" -> OPEN_ROUTER_REFERER to OPEN_ROUTER_TITLE
        else -> null to null
    }

    /**
     * Single chat-completion POST with up to [MAX_API_ATTEMPTS] attempts.
     * Retries transient failures (429/5xx/timeout) with exponential backoff + jitter.
     * Honors Retry-After when present. Does NOT retry 404/auth/parse errors.
     */
    private suspend fun callChatCompletionWithRetry(
        provider: String,
        url: String,
        apiKey: String,
        request: OpenAiRequest,
        attempts: Int = MAX_API_ATTEMPTS
    ): OpenAiResponse {
        val canonical = AiSettingsHelper.canonicalProvider(provider)
        val (referer, title) = referralHeaders(canonical)
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                return openAiService.getChatCompletion(url, "Bearer $apiKey", referer, title, request)
            } catch (e: HttpException) {
                lastError = e
                when {
                    e.code() == 404 || e.code() == 410 -> throw e // model gone — caller must migrate
                    e.code() == 401 || e.code() == 403 -> throw e // bad key — retry won't help
                    !isRetryable(e) -> throw e
                    attempt < attempts - 1 -> {
                        val backoff = retryBackoff(attempt, e)
                        Log.w(TAG, "Chat call $canonical/${request.model} attempt ${attempt + 1}/$attempts failed HTTP ${e.code()}, retry in ${backoff}ms")
                        delay(backoff)
                    }
                    else -> throw e
                }
            } catch (e: Exception) {
                lastError = e
                if (isModelNotFound(e)) throw e
                if (!isRetryable(e)) throw e
                if (attempt < attempts - 1) {
                    val backoff = retryBackoff(attempt, e)
                    Log.w(TAG, "Chat call $canonical/${request.model} attempt ${attempt + 1}/$attempts failed (${e.message}), retry in ${backoff}ms")
                    delay(backoff)
                }
            }
        }
        throw lastError ?: Exception("Chat completion failed after $attempts attempts")
    }

    private fun retryBackoff(attempt: Int, e: Throwable): Long {
        // Prefer server Retry-After (seconds) when present, else exp backoff + jitter.
        val retryAfterSec = (e as? HttpException)?.let { http ->
            runCatching {
                http.response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()
            }.getOrNull()
        }?.takeIf { it in 1..120 }?.times(1000L)
        if (retryAfterSec != null) return retryAfterSec
        val exp = RETRY_BASE_DELAY_MS * (1L shl attempt.coerceAtMost(4))
        val jitter = (Math.random() * 250L).toLong()
        return (exp + jitter).coerceAtMost(10_000L)
    }

    private suspend fun callProvider(
        provider: String,
        keyState: ResolvedApiKey,
        modelName: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean,
        systemPromptOverride: String?,
        persistFallback: Boolean = true,
    ): Result<ChatRepository.ChatResponseChunk> {
        val canonical = AiSettingsHelper.canonicalProvider(provider)
        if (keyState.value.isBlank()) {
            return Result.failure(Exception("No API key for $canonical. Open Settings → $canonical and paste a key."))
        }

        // Build candidate chain: stored model → retired-model migration → provider fallbacks.
        // Dedupe while preserving order. Cap at 4 to bound latency.
        val candidates = buildList {
            add(modelName.ifBlank { AiSettingsHelper.getRecommendedModel(canonical) })
            AiSettingsHelper.migrateRetiredModel(canonical, modelName)?.let { add(it) }
            addAll(AiSettingsHelper.getFallbackModels(canonical))
        }.distinct().take(4)

        // Image requests: fail fast if NO candidate supports vision (avoids silent Unknown error).
        if (image != null && candidates.none { AiSettingsHelper.supportsVision(canonical, it) }) {
            return Result.failure(
                Exception("$canonical has no vision-capable model in its fallback chain. Remove the image or pick a vision model.")
            )
        }

        var lastError: Throwable? = null
        for ((index, currentModel) in candidates.withIndex()) {
            try {
                if (image != null && !AiSettingsHelper.supportsVision(canonical, currentModel)) {
                    Log.w(TAG, "Skipping non-vision model $currentModel for image request")
                    // Don't abort — try next fallback that supports vision.
                    continue
                }
                val result = executeProviderCall(canonical, keyState.value, currentModel, prompt, history, image, searchEnabled, systemPromptOverride)
                if (result.isSuccess && index > 0 && persistFallback) {
                    // Persist the working fallback so the next message doesn't 404 again.
                    runCatching { settingsManager.setSelectedModel(currentModel, canonical) }
                    Log.i(TAG, "Auto-recovered $canonical: now using $currentModel")
                }
                return result
            } catch (e: Exception) {
                lastError = e
                val recoverable = isModelNotFound(e) || isRateLimited(e)
                Log.w(TAG, "Call failed with $currentModel (attempt ${index + 1}/${candidates.size}, recoverable=$recoverable)", e)
                if (!recoverable) return Result.failure(friendlyError(canonical, currentModel, e))
                if (isRateLimited(e) && index == candidates.size - 1) {
                    // Last candidate also rate-limited — small extra wait already happened inside retry; surface clearly.
                    return Result.failure(friendlyError(canonical, currentModel, e))
                }
                // else: try next candidate model
            }
        }
        return Result.failure(lastError?.let { friendlyError(canonical, modelName, it) }
            ?: Exception("Unknown error in callProvider"))
    }

    private fun friendlyError(provider: String, model: String, e: Throwable): Throwable {
        // Truncate raw detail: server messages can be long / contain request echoes.
        val raw = (e.message ?: "Unknown error").take(300)
        return when {
            isModelNotFound(e) -> Exception("Model '$model' is retired / not found on $provider (404). Pick ${AiSettingsHelper.getRecommendedModel(provider)} in Settings — or it auto-switched already. Detail: $raw")
            isRateLimited(e) -> Exception("$provider rate limit hit for '$model'. Wait ~30s or switch to ${AiSettingsHelper.getFallbackModels(provider).firstOrNull() ?: "another model"}. Detail: $raw")
            raw.contains("401", true) || raw.contains("unauthorized", true) -> Exception("Invalid API key for $provider. Open Settings → $provider and check the key.")
            else -> e
        }
    }

    private suspend fun executeProviderCall(
        provider: String,
        apiKey: String,
        modelName: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean,
        systemPromptOverride: String?
    ): Result<ChatRepository.ChatResponseChunk> = when (AiSettingsHelper.canonicalProvider(provider)) {
        "Gemini" -> callGemini(apiKey, modelName, prompt, history, image, searchEnabled, systemPromptOverride)
        "ChatGPT",
        "Groq",
        "DeepSeek",
        "OpenRouter",
        "OpenCode Zen",
        "OpenCode Go" -> callOpenAiCompatible(provider, apiKey, modelName, prompt, history, image, searchEnabled, systemPromptOverride)
        "Claude" -> callClaude(apiKey, modelName, prompt, history, image, searchEnabled, systemPromptOverride)
        else -> Result.failure(Exception("Unknown provider: $provider"))
    }

    private suspend fun callGemini(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean,
        systemPromptOverride: String?
    ): Result<ChatRepository.ChatResponseChunk> {
        var lastError: Exception? = null
        repeat(MAX_API_ATTEMPTS) { attempt ->
            try {
                val generativeModel = GenerativeModel(modelName = model, apiKey = apiKey, systemInstruction = systemPromptOverride?.let { content { text(it) } } ?: content { text(systemPrompt) })
                val effectivePrompt = if (searchEnabled) {
                    "Toolz AI. Web search context provided. Prompt: $prompt"
                } else prompt.ifBlank { if (image != null) "Describe image" else "Help me" }

                return if (image != null) {
                    val text = generativeModel.generateContent(content { image(image); text(effectivePrompt) }).text ?: "No response"
                    Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
                } else {
                    // Merge consecutive messages of same role for Gemini
                    val mergedHistory = mutableListOf<AiMessage>()
                    history.forEach { msg ->
                        val last = mergedHistory.lastOrNull()
                        if (last != null && last.isUser == msg.isUser) {
                            mergedHistory[mergedHistory.size - 1] = last.copy(text = last.text + "\n\n" + msg.text)
                        } else {
                            mergedHistory += msg
                        }
                    }
                    val chat = generativeModel.startChat(mergedHistory.map { content(role = if (it.isUser) "user" else "model") { text(it.text) } })
                    val text = chat.sendMessage(effectivePrompt).text ?: "No response"
                    Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
                }
            } catch (e: Exception) {
                lastError = e as? Exception ?: Exception(e.message)
                if (isModelNotFound(e)) throw e // let callProvider migrate models
                if (attempt < MAX_API_ATTEMPTS - 1 && (isRateLimited(e) || isTransient(e))) {
                    delay(RETRY_BASE_DELAY_MS * (1L shl attempt))
                } else if (attempt == MAX_API_ATTEMPTS - 1) {
                    throw e
                } else throw e
            }
        }
        throw lastError ?: Exception("Gemini call failed")
    }

    private fun isTransient(e: Throwable): Boolean {
        val msg = (e.message ?: "").lowercase(java.util.Locale.ROOT)
        return msg.contains("timeout") || msg.contains("unavailable") || msg.contains("deadline") ||
            msg.contains("network") || msg.contains("500") || msg.contains("502") || msg.contains("503")
    }

    /** Cap history to ~12k chars total (plus current prompt) to bound tokens/latency. */
    private fun budgetHistory(history: List<AiMessage>, maxChars: Int = 12_000): List<AiMessage> {
        if (history.isEmpty()) return history
        var used = 0
        val out = ArrayDeque<AiMessage>()
        for (m in history.asReversed()) {
            val len = m.text.length
            if (used + len > maxChars && out.isNotEmpty()) break
            out.addFirst(m)
            used += len
            if (out.size >= MAX_HISTORY_MESSAGES) break
        }
        return out.toList()
    }

    private suspend fun callOpenAiCompatible(
        provider: String,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean,
        systemPromptOverride: String?
    ): Result<ChatRepository.ChatResponseChunk> {
        val url = AiSettingsHelper.getChatCompletionUrl(provider)?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("No URL for $provider"))
        val messages = mutableListOf<OpenAiMessage>()
        messages += OpenAiMessage("system", MessageContent.Text(systemPromptOverride ?: systemPrompt))

        // Merge consecutive messages of same role for OpenAI compatible
        val mergedHistory = mutableListOf<AiMessage>()
        budgetHistory(history).forEach { msg ->
            val last = mergedHistory.lastOrNull()
            if (last != null && last.isUser == msg.isUser) {
                mergedHistory[mergedHistory.size - 1] = last.copy(text = last.text + "\n\n" + msg.text)
            } else {
                mergedHistory += msg
            }
        }

        mergedHistory.forEach { msg ->
            messages += OpenAiMessage(role = if (msg.isUser) "user" else "assistant", content = MessageContent.Text(msg.text))
        }

        val userContent = if (image != null) {
            MessageContent.Blocks(listOf(ContentBlock("text", prompt), ContentBlock("image_url", imageUrl = ImageUrl("data:image/jpeg;base64,${bitmapToBase64(image)}"))))
        } else MessageContent.Text(prompt)
        messages += OpenAiMessage("user", userContent)

        val canonical = AiSettingsHelper.canonicalProvider(provider)
        val response = callChatCompletionWithRetry(
            provider = canonical,
            url = url,
            apiKey = apiKey,
            request = OpenAiRequest(model, messages, tools = null)
        )
        val text = response.choices.firstOrNull()?.message?.content ?: "No response"
        return Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
    }

    private suspend fun callClaude(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<AiMessage>,
        image: Bitmap?,
        searchEnabled: Boolean,
        systemPromptOverride: String?
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

        val response = openAiService.getClaudeCompletion("https://api.anthropic.com/v1/messages", apiKey, "2023-06-01", ClaudeRequest(model, messages, system = systemPromptOverride ?: systemPrompt))
        val text = response.content.filter { it.type == "text" }.joinToString("\n") { it.text ?: "" }
        return Result.success(ChatRepository.ChatResponseChunk(cleanResponseText(text)))
    }

    /**
     * Downscale to max 1024px before base64 to avoid OOM / 413s.
     * Full-res JPEG-80 can exceed 3MB base64; capped version stays < ~1.2MB.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSide = 1024
        val w = bitmap.width
        val h = bitmap.height
        val scale = if (w <= maxSide && h <= maxSide) 1f else maxSide / maxOf(w, h).toFloat()
        val scaled = if (scale >= 1f) bitmap else try {
            android.graphics.Bitmap.createScaledBitmap(
                bitmap, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true
            )
        } catch (_: Exception) { bitmap }
        return try {
            ByteArrayOutputStream().use { bos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, bos)
                var bytes = bos.toByteArray()
                // Extra guard: recompress lower if still huge (>1.5MB raw).
                if (bytes.size > 1_500_000) {
                    ByteArrayOutputStream().use { bos2 ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 60, bos2)
                        bytes = bos2.toByteArray()
                    }
                }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } finally {
            if (scaled !== bitmap) runCatching { scaled.recycle() }
        }
    }
    private fun cleanResponseText(text: String): String = stripSourcesSection(text.replace("\uFEFF", "")).trim()
}
