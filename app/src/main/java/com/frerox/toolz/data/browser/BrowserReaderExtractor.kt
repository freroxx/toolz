package com.frerox.toolz.data.browser

import org.json.JSONTokener

data class BrowserReaderArticle(
    val title: String,
    val source: String,
    val text: String,
)

/** Page-side extractor and defensive parser for the browser's reader view. */
object BrowserReaderExtractor {
    val script = """
        (function() {
          var root = document.querySelector('article, [role="main"], main, .article-body, .article-content, .post-content, .entry-content') || document.body;
          var clone = root.cloneNode(true);
          clone.querySelectorAll('script,style,noscript,nav,header,footer,aside,form,button,iframe,[role="navigation"],[role="banner"],.ad,.ads,.advertisement,.cookie,.comments,.share').forEach(function(n) { n.remove(); });
          var text = (clone.innerText || '').replace(/\\n{3,}/g, '\\n\\n').replace(/[ \\t]+\\n/g, '\\n').trim();
          return JSON.stringify({ title: document.title || '', source: location.hostname || '', text: text });
        })();
    """.trimIndent()

    fun parseJavascriptResult(raw: String): BrowserReaderArticle? {
        // WebView versions differ: some return the object directly, others a JSON
        // string containing it. Accept both without trusting malformed page data.
        val decoded = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        val candidates = listOfNotNull(
            (decoded as? String)?.let(::normalisePayload),
            raw.takeIf { it.trimStart().startsWith("{") },
            normalisePayload(raw).takeIf { it.startsWith("{") },
        )
        val payload = candidates.firstOrNull { it.contains("\"text\"") } ?: return null
        val text = jsonValue(payload, "text").orEmpty().trim()
        if (text.length < 80) return null
        return BrowserReaderArticle(
            title = jsonValue(payload, "title").orEmpty()
                .ifBlank { jsonValue(payload, "source").orEmpty().ifBlank { "Reader view" } },
            source = jsonValue(payload, "source").orEmpty(),
            text = text,
        )
    }

    private fun normalisePayload(value: String): String = value
        .removeSurrounding("\"")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    private fun jsonValue(payload: String, key: String): String? {
        val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            .find(payload)?.groupValues?.getOrNull(1) ?: return null
        return match.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
