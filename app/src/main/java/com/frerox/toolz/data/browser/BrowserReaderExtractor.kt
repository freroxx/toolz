package com.frerox.toolz.data.browser

import org.json.JSONObject
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
        val json = candidates.firstNotNullOfOrNull { candidate ->
            runCatching { JSONObject(candidate) }.getOrNull()
        } ?: return null
        val text = (json.opt("text") as? String).orEmpty().trim()
        if (text.length < 80) return null
        return BrowserReaderArticle(
            title = (json.opt("title") as? String).orEmpty()
                .ifBlank { (json.opt("source") as? String).orEmpty().ifBlank { "Reader view" } },
            source = (json.opt("source") as? String).orEmpty(),
            text = text,
        )
    }

    private fun normalisePayload(value: String): String = value
        .removeSurrounding("\"")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
