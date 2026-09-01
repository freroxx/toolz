package com.frerox.toolz.data.browser

import org.json.JSONTokener

data class BrowserReaderArticle(
    val title: String,
    val source: String,
    val text: String,
    val markdown: String = text,
    val html: String? = null,
)

/** Page-side extractor and defensive parser for the browser's reader view. */
object BrowserReaderExtractor {
    val script = """
        (function() {
          var root = document.querySelector('article, [role="main"], main, .article-body, .article-content, .post-content, .entry-content, #content') || document.body;
          var clone = root.cloneNode(true);
          clone.querySelectorAll('script,style,noscript,nav,header,footer,aside,form,button,iframe,[role="navigation"],[role="banner"],.ad,.ads,.advertisement,.cookie,.comments,.share,.sidebar,#cookie-banner').forEach(function(n) { n.remove(); });
          // Clean attributes but keep structure
          clone.querySelectorAll('*').forEach(function(el){
            // remove inline styles that may hide content
            el.removeAttribute('style');
          });
          var text = (clone.innerText || '').replace(/\n{3,}/g, '\n\n').replace(/[ \t]+\n/g, '\n').trim();
          var html = clone.innerHTML || '';
          // Limit html size to avoid WebView string limits
          if (html.length > 80000) html = html.substring(0,80000);
          return JSON.stringify({ title: document.title || '', source: location.hostname || '', text: text, html: html });
        })();
    """.trimIndent()

    fun parseJavascriptResult(raw: String): BrowserReaderArticle? {
        val decoded = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        val candidates = listOfNotNull(
            (decoded as? String)?.let(::normalisePayload),
            raw.takeIf { it.trimStart().startsWith("{") },
            normalisePayload(raw).takeIf { it.startsWith("{") },
        )
        val payload = candidates.firstOrNull { it.contains("\"text\"") } ?: return null
        val text = jsonValue(payload, "text").orEmpty().trim()
        if (text.length < 80) return null
        val title = jsonValue(payload, "title").orEmpty()
            .ifBlank { jsonValue(payload, "source").orEmpty().ifBlank { "Reader view" } }
        val source = jsonValue(payload, "source").orEmpty()
        val html = jsonValue(payload, "html")
        val markdown = if (!html.isNullOrBlank()) {
            htmlToMarkdown(html).takeIf { it.trim().length >= 80 } ?: text
        } else text
        return BrowserReaderArticle(
            title = title,
            source = source,
            text = text,
            markdown = markdown,
            html = html,
        )
    }

    fun htmlToMarkdown(html: String): String {
        return try {
            val doc = org.jsoup.Jsoup.parseBodyFragment(html)
            val sb = StringBuilder()
            fun walk(node: org.jsoup.nodes.Node, listDepth: Int = 0, ordered: Boolean = false, index: Int = 0) {
                when (node) {
                    is org.jsoup.nodes.TextNode -> {
                        val t = node.text().replace(Regex("\\s+"), " ")
                        if (t.isNotBlank()) sb.append(t)
                    }
                    is org.jsoup.nodes.Element -> {
                        val tag = node.tagName().lowercase()
                        when (tag) {
                            "h1" -> { sb.append("\n# "); node.childNodes().forEach { walk(it) }; sb.append("\n\n") }
                            "h2" -> { sb.append("\n## "); node.childNodes().forEach { walk(it) }; sb.append("\n\n") }
                            "h3" -> { sb.append("\n### "); node.childNodes().forEach { walk(it) }; sb.append("\n\n") }
                            "h4" -> { sb.append("\n#### "); node.childNodes().forEach { walk(it) }; sb.append("\n\n") }
                            "h5", "h6" -> { sb.append("\n##### "); node.childNodes().forEach { walk(it) }; sb.append("\n\n") }
                            "p", "div", "section", "article" -> {
                                if (sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append("\n\n")
                                node.childNodes().forEach { walk(it) }
                                if (!sb.endsWith("\n\n")) sb.append("\n\n")
                            }
                            "br" -> sb.append("  \n")
                            "blockquote" -> {
                                sb.append("\n> ")
                                node.childNodes().forEach { walk(it) }
                                sb.append("\n\n")
                            }
                            "ul" -> node.childNodes().filterIsInstance<org.jsoup.nodes.Element>().forEach { walk(it, listDepth + 1, false) }
                            "ol" -> {
                                var i = 1
                                node.childNodes().filterIsInstance<org.jsoup.nodes.Element>().forEach { el ->
                                    if (el.tagName() == "li") { walk(el, listDepth + 1, true, i); i++ } else walk(el, listDepth, false)
                                }
                            }
                            "li" -> {
                                val prefix = if (ordered) "${index}. " else "- "
                                sb.append("\n${"  ".repeat(listDepth - 1)}$prefix")
                                node.childNodes().forEach { walk(it, listDepth, ordered) }
                            }
                            "pre" -> {
                                sb.append("\n```\n")
                                sb.append(node.text())
                                sb.append("\n```\n\n")
                            }
                            "code" -> {
                                val parentIsPre = node.parent()?.tagName()?.lowercase() == "pre"
                                if (!parentIsPre) {
                                    sb.append("`")
                                    node.childNodes().forEach { walk(it) }
                                    sb.append("`")
                                }
                            }
                            "a" -> {
                                val href = node.attr("href")
                                val linkText = node.text().takeIf { it.isNotBlank() } ?: href
                                if (href.isNotBlank() && linkText != href) sb.append("[$linkText]($href)") else sb.append(linkText)
                            }
                            "img" -> {
                                val src = node.attr("src").ifBlank { node.attr("data-src") }
                                val alt = node.attr("alt").ifBlank { "image" }
                                if (src.isNotBlank()) sb.append("\n![$alt]($src)\n") 
                            }
                            "strong", "b" -> { sb.append("**"); node.childNodes().forEach { walk(it) }; sb.append("**") }
                            "em", "i" -> { sb.append("*"); node.childNodes().forEach { walk(it) }; sb.append("*") }
                            "hr" -> sb.append("\n---\n")
                            "table" -> {
                                sb.append("\n")
                                node.childNodes().forEach { walk(it) }
                                sb.append("\n")
                            }
                            "tr" -> {
                                val cells = node.children().filter { it.tagName() in listOf("td","th") }
                                if (cells.isNotEmpty()) {
                                    sb.append("| ")
                                    cells.forEach { cell ->
                                        val cellText = cell.text().replace("|","\\|").trim()
                                        sb.append(cellText); sb.append(" | ")
                                    }
                                    sb.append("\n")
                                    if (cells.any { it.tagName()=="th"}) {
                                        sb.append("| ")
                                        cells.forEach { _ -> sb.append("--- | ") }
                                        sb.append("\n")
                                    }
                                }
                            }
                            else -> node.childNodes().forEach { walk(it, listDepth, ordered) }
                        }
                    }
                }
            }
            doc.body().childNodes().forEach { walk(it) }
            var md = sb.toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .replace(Regex("[ \\t]+\n"), "\n")
                .trim()
            // Fix gaps: ensure not too many blank lines, preserve code
            md = md.replace(Regex("(?m)^[ \\t]+"), "")
            md
        } catch (_: Exception) {
            org.jsoup.Jsoup.parse(html).text().replace(Regex("\\n{3,}"), "\n\n").trim()
        }
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
