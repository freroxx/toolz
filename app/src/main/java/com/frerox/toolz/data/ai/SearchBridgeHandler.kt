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
                    // Fallback for when arguments are passed as a plain string by some providers
                    mapOf("query" to toolCall.function.arguments)
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
                    "No results found for '$query'. The search might have failed or no relevant information was found."
                } else {
                    results.take(maxResults).joinToString("\n\n") { result ->
                        "Title: ${result.title}\nSnippet: ${result.snippet}\nURL: ${result.url}"
                    }
                }
            } catch (e: Exception) {
                "Search failed: ${e.message ?: "Unknown error"}. Please inform the user that you couldn't access the web right now."
            }
        } else {
            "Error: Unknown tool '${toolCall.function.name}'"
        }
    }
}
