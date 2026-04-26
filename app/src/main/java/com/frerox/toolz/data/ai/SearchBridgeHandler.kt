package com.frerox.toolz.data.ai

import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.WebSearchRepository
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchBridgeHandler @Inject constructor(
    private val webSearchRepository: WebSearchRepository,
    private val moshi: Moshi
) {
    private val toolDefinition = Tool(
        function = ToolDefinition(
            name = "web_search",
            description = "Search the web for real-time information, news, facts, or weather. Use this when the user asks about current events or things outside your training data.",
            parameters = ToolParameters(
                properties = mapOf(
                    "query" to PropertyDefinition(
                        type = "string",
                        description = "The search query to look up."
                    ),
                    "max_results" to PropertyDefinition(
                        type = "integer",
                        description = "Optional maximum number of results to return (default 5)."
                    )
                ),
                required = listOf("query")
            )
        )
    )

    fun getToolDefinition(): Tool = toolDefinition

    private val _lastSearchResults = mutableListOf<SearchResult>()
    val lastSearchResults: List<SearchResult> get() = _lastSearchResults

    suspend fun handleToolCall(toolCall: ToolCall): String {
        return if (toolCall.function.name == "web_search") {
            try {
                val adapter = moshi.adapter(Map::class.java)
                val args = try {
                    adapter.fromJson(toolCall.function.arguments)
                } catch (_: Exception) {
                    val raw = toolCall.function.arguments.trim()
                    if (raw.startsWith("{") && raw.contains("\"query\"")) {
                        val queryStart = raw.indexOf("\"query\"")
                        val colon = raw.indexOf(":", queryStart)
                        val quote = raw.indexOf("\"", colon)
                        if (quote != -1) {
                            var extracted = raw.substring(quote + 1)
                            extracted = extracted.replace(Regex("\",\\s*\"max_results\"\\s*:\\s*\\d+\\s*\\}*\$"), "")
                            extracted = extracted.removeSuffix("}").trim().removeSuffix("\"")
                            mapOf("query" to extracted)
                        } else {
                            mapOf("query" to raw)
                        }
                    } else {
                        mapOf("query" to raw)
                    }
                }
                val query = args?.get("query")?.toString() ?: return "Error: Missing query parameter"
                
                val maxResults = when (val rawMax = args["max_results"]) {
                    is Double -> rawMax.toInt()
                    is String -> rawMax.toIntOrNull() ?: 5
                    else -> 5
                }

                val results = webSearchRepository.search(query)
                _lastSearchResults.clear()
                _lastSearchResults.addAll(results.take(maxResults))

                if (results.isEmpty()) {
                    "No results found for '$query'. The search might have failed or no relevant information was found. Inform the user you tried searching but couldn't find matches."
                } else {
                    val contextText = results.take(maxResults).joinToString("\n\n") { result ->
                        "SOURCE: ${result.title}\nURL: ${result.url}\nCONTENT: ${result.snippet}"
                    }
                    "Search results for '$query':\n\n$contextText\n\nPlease use the provided information to answer accurately. Always cite sources."
                }
            } catch (e: Exception) {
                "Search failed: ${e.message ?: "Unknown error"}. Please inform the user that you couldn't access the web right now."
            }
        } else {
            "Error: Unknown tool '${toolCall.function.name}'"
        }
    }
}
