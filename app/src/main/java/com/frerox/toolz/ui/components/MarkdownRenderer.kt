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

package com.frerox.toolz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MdSegment {
    data class Paragraph(val content: AnnotatedString) : MdSegment()
    data class Header(val level: Int, val text: String) : MdSegment()
    data class Code(val language: String, val code: String) : MdSegment()
    data class BulletItem(val content: AnnotatedString, val depth: Int = 0, val isChecked: Boolean? = null) : MdSegment()
    data class NumberedItem(val index: Int, val content: AnnotatedString) : MdSegment()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<androidx.compose.ui.text.style.TextAlign> = emptyList()
    ) : MdSegment()
    data class Blockquote(val content: AnnotatedString) : MdSegment()
    object Divider : MdSegment()
}

private fun splitTableRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.drop(1)
    if (t.endsWith("|")) t = t.dropLast(1)
    // Preserve empty cells ("a || c") but trim each cell.
    return t.split("|").map { it.trim() }
}

private fun isTableSeparator(line: String): Boolean {
    val cells = splitTableRow(line)
    if (cells.isEmpty()) return false
    // Every cell must look like --- / :--- / ---: / :---: (3+ dashes)
    return cells.all { it.matches(Regex("^:?-{3,}:?$")) }
}

private fun parseTableAlignments(separatorLine: String): List<androidx.compose.ui.text.style.TextAlign> {
    return splitTableRow(separatorLine).map { cell ->
        val left = cell.startsWith(":")
        val right = cell.endsWith(":")
        when {
            left && right -> androidx.compose.ui.text.style.TextAlign.Center
            right -> androidx.compose.ui.text.style.TextAlign.End
            else -> androidx.compose.ui.text.style.TextAlign.Start
        }
    }
}

fun parseMarkdownToSegments(raw: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    val lines    = raw.lines()
    var i        = 0

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        if (line.trimStart().startsWith("```")) {
            val lang  = line.trim().removePrefix("```").trim().ifBlank { "code" }
            val code  = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            segments += MdSegment.Code(lang, code.toString().trimEnd())
            i++
            continue
        }

        // Horizontal rule
        if (line.trim().matches(Regex("^[-*_]{3,}$"))) {
            segments += MdSegment.Divider
            i++
            continue
        }

        // Headers
        if (line.trimStart().startsWith("#")) {
            val level = line.trimStart().takeWhile { it == '#' }.length.coerceIn(1, 6)
            segments += MdSegment.Header(level, line.trimStart().drop(level).trim())
            i++
            continue
        }

        // Bullet list (including checkboxes)
        val bulletMatch = Regex("^(\\s*)[\\-\\*\\+] (.*)").find(line)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1]
            val depth = indent.length / 2
            var content = bulletMatch.groupValues[2]
            
            var isChecked: Boolean? = null
            if (content.startsWith("[ ] ")) {
                isChecked = false
                content = content.drop(4)
            } else if (content.startsWith("[x] ") || content.startsWith("[X] ")) {
                isChecked = true
                content = content.drop(4)
            }
            
            segments += MdSegment.BulletItem(inlineMarkdownNoCompose(content), depth, isChecked)
            i++
            continue
        }

        // Numbered list
        val numberedMatch = Regex("^(\\d+)[\\.\\)] (.+)").find(line.trim())
        if (numberedMatch != null) {
            val idx     = numberedMatch.groupValues[1].toIntOrNull() ?: 1
            val content = numberedMatch.groupValues[2]
            segments += MdSegment.NumberedItem(idx, inlineMarkdownNoCompose(content))
            i++
            continue
        }

        // Blockquote
        if (line.trimStart().startsWith(">")) {
            val content = line.trimStart().drop(1).trim()
            segments += MdSegment.Blockquote(inlineMarkdownNoCompose(content))
            i++
            continue
        }

        // Blank line
        if (line.isBlank()) {
            i++
            continue
        }

        // Tables — support both "| a | b |" and "a | b" styles, with
        // alignment row (| :--- | :---: | ---: |). Cells keep raw markdown
        // so **bold** / `code` / links render inside the grid.
        if (line.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val rawHeaders = splitTableRow(line)
            val alignments = parseTableAlignments(lines[i + 1])
            i += 2 // skip header and separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].contains("|") && lines[i].isNotBlank()
                && !isTableSeparator(lines[i])
            ) {
                val t = lines[i].trimStart()
                if (t.startsWith("#") || t.startsWith("```") || t.startsWith(">")) break
                rows.add(splitTableRow(lines[i]))
                i++
            }
            val colCount = (listOf(rawHeaders.size) + rows.map { it.size }).maxOrNull() ?: 0
            if (colCount > 0) {
                val headers = List(colCount) { idx -> rawHeaders.getOrNull(idx).orEmpty() }
                val normRows = rows.map { row -> List(colCount) { idx -> row.getOrNull(idx).orEmpty() } }
                val normAlign = List(colCount) { idx ->
                    alignments.getOrNull(idx) ?: androidx.compose.ui.text.style.TextAlign.Start
                }
                segments += MdSegment.Table(headers, normRows, normAlign)
            }
            continue
        }

        // Normal paragraph (group consecutive text lines)
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val curr = lines[i]
            val startsTable = curr.contains("|") && i + 1 < lines.size &&
                runCatching { isTableSeparator(lines[i + 1]) }.getOrDefault(false)
            if (curr.isBlank() ||
                curr.trimStart().startsWith("#") ||
                curr.trimStart().startsWith("```") ||
                startsTable ||
                curr.matches(Regex("^(\\s*)[\\-\\*\\+] .+")) ||
                curr.matches(Regex("^(\\d+)[\\.\\)] .+")) ||
                curr.trim().matches(Regex("^[-*_]{3,}$"))
            ) break
            paragraphLines.add(curr)
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            segments += MdSegment.Paragraph(inlineMarkdownNoCompose(paragraphLines.joinToString(" ")))
        }
    }
    return segments
}

fun inlineMarkdownNoCompose(text: String): AnnotatedString = buildAnnotatedString {
    data class Token(val start: Int, val end: Int, val content: String, val type: String, val url: String? = null)
    val tokens = mutableListOf<Token>()

    fun scan(regex: String, type: String) {
        Regex(regex).findAll(text).forEach { m ->
            val start = m.range.first
            val end = m.range.last + 1
            if (type == "link") {
                tokens += Token(start, end, m.groupValues[1], "link", m.groupValues[2])
            } else {
                tokens += Token(start, end, m.groupValues.getOrElse(1) { m.value }, type)
            }
        }
    }

    // Priority: code + link first so ** inside `code` or [text](url) never
    // steals the span. Bold supports both **x** and __x__; italic uses
    // single-star/underscore guards so "**bold**" isn't misread as italic.
    scan("""`(.+?)`""", "code")
    scan("""\[(.+?)\]\((.+?)\)""", "link")
    scan("""\*\*([\s\S]+?)\*\*""", "bold")
    scan("""__([\s\S]+?)__""", "bold")
    scan("""(?<!\*)\*([^*\n]+?)\*(?!\*)""", "italic")
    scan("""(?<!_)_([^_\n]+?)_(?!_)""", "italic")
    scan("""~~(.+?)~~""", "strike")

    // Same start → longest (most specific) wins; otherwise earliest wins.
    // This keeps "**text**" as bold even though the italic regex could also
    // match its inner stars, and keeps `code`/links ahead of bold.
    val clean = mutableListOf<Token>()
    var cursor = 0
    for (tok in tokens.sortedWith(compareBy<Token> { it.start }.thenByDescending { it.end })) {
        if (tok.start >= cursor) {
            clean += tok
            cursor = tok.end
        }
    }

    cursor = 0
    for (tok in clean) {
        if (tok.start > cursor) append(text.substring(cursor, tok.start))
        when (tok.type) {
            // Recursively parse inner content so "**bold with *italic* or `code`**"
            // keeps both styles instead of leaking raw markers.
            "bold"   -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inlineMarkdownNoCompose(tok.content)) }
            "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inlineMarkdownNoCompose(tok.content)) }
            "code"   -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Black.copy(0.08f), color = Color(0xFF2962FF))) { append(tok.content) }
            "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(inlineMarkdownNoCompose(tok.content)) }
            "link"   -> {
                pushStringAnnotation(tag = "URL", annotation = tok.url ?: "")
                withStyle(SpanStyle(color = Color(0xFF2962FF), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) {
                    append(inlineMarkdownNoCompose(tok.content))
                }
                pop()
            }
            else     -> append(tok.content)
        }
        cursor = tok.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
private fun MarkdownAnnotatedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val hasLinks = remember(text) {
        text.getStringAnnotations("URL", 0, text.length).isNotEmpty()
    }
    if (!hasLinks) {
        Text(
            text = text,
            style = style,
            modifier = modifier,
        )
    } else {
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = text,
            style = style,
            modifier = modifier.pointerInput(text, onLinkClick, onLongClick) {
                detectTapGestures(
                    onLongPress = {
                        onLongClick?.invoke()
                    },
                    onTap = { pos ->
                        layoutResult?.let { layout ->
                            val offset = layout.getOffsetForPosition(pos)
                            text.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    onLinkClick(annotation.item)
                                }
                        }
                    }
                )
            },
            onTextLayout = { layoutResult = it }
        )
    }
}

@Composable
fun MarkdownSegment(
    seg: MdSegment,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = 16.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onLinkClick: (String) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = baseFontSize,
        lineHeight = (baseFontSize.value * 1.5f).sp,
        color = textColor
    )

    when (seg) {
        is MdSegment.Header -> {
            val (fontSize, weight) = when (seg.level) {
                1 -> (baseFontSize.value * 1.4f).sp to FontWeight.Black
                2 -> (baseFontSize.value * 1.25f).sp to FontWeight.ExtraBold
                else -> (baseFontSize.value * 1.15f).sp to FontWeight.Bold
            }
            Text(seg.text, fontSize = fontSize, fontWeight = weight, lineHeight = (fontSize.value + 4).sp, color = textColor, modifier = modifier.padding(top = 12.dp, bottom = 4.dp))
        }
        is MdSegment.Paragraph -> {
            MarkdownAnnotatedText(
                text = seg.content,
                style = bodyStyle,
                modifier = modifier,
                onLinkClick = onLinkClick,
                onLongClick = onLongClick,
            )
        }
        is MdSegment.BulletItem -> {
            Row(modifier = modifier.padding(start = (seg.depth * 12).dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (seg.isChecked != null) {
                    Icon(
                        imageVector = if (seg.isChecked) Icons.Rounded.Check else Icons.Rounded.CheckBoxOutlineBlank,
                        contentDescription = null,
                        modifier = Modifier.size((baseFontSize.value * 1.2f).dp).offset(y = 2.dp),
                        tint = if (seg.isChecked) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.5f)
                    )
                } else {
                    Box(Modifier.size((baseFontSize.value / 3).dp).offset(y = (baseFontSize.value / 2).dp).background(textColor.copy(alpha = 0.8f), CircleShape))
                }
                MarkdownAnnotatedText(
                    text = seg.content,
                    style = bodyStyle,
                    modifier = Modifier.weight(1f),
                    onLinkClick = onLinkClick,
                    onLongClick = onLongClick,
                )
            }
        }
        is MdSegment.NumberedItem -> {
            Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${seg.index}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = bodyStyle, modifier = Modifier.widthIn(min = (baseFontSize.value * 1.2f).dp))
                MarkdownAnnotatedText(
                    text = seg.content,
                    style = bodyStyle,
                    modifier = Modifier.weight(1f),
                    onLinkClick = onLinkClick,
                    onLongClick = onLongClick,
                )
            }
        }
        is MdSegment.Code -> MarkdownCodeBlock(language = seg.language, code = seg.code, modifier = modifier)
        is MdSegment.Table -> {
            MarkdownTable(
                headers = seg.headers,
                rows = seg.rows,
                alignments = seg.alignments,
                baseFontSize = baseFontSize,
                textColor = textColor,
                onLinkClick = onLinkClick,
                modifier = modifier,
            )
        }
        is MdSegment.Blockquote -> {
            Row(modifier = modifier.padding(vertical = 8.dp).height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(16.dp))
                MarkdownAnnotatedText(
                    text = seg.content,
                    style = bodyStyle.copy(fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.8f)),
                    onLinkClick = onLinkClick,
                    onLongClick = onLongClick,
                )
            }
        }
        MdSegment.Divider -> HorizontalDivider(modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
    }
}

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = 16.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    onLinkClick: (String) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val segments = remember(markdown) { parseMarkdownToSegments(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement
    ) {
        segments.forEach { seg ->
            MarkdownSegment(
                seg = seg,
                baseFontSize = baseFontSize,
                textColor = textColor,
                onLinkClick = onLinkClick,
                onLongClick = onLongClick,
            )
        }
    }
}

/**
 * Strips markdown syntax elements, returning a clean, readable plain text representation.
 */
fun stripMarkdown(markdown: String): String {
    var text = markdown
    // 1. Fenced code blocks ```lang\n...``` -> ...
    text = text.replace(Regex("```[a-zA-Z0-9_-]*\\n?([\\s\\S]*?)```"), "$1")
    // 2. Inline code `code` -> code
    text = text.replace(Regex("`([^`]+)`"), "$1")
    // 3. Images ![alt](url) -> alt
    text = text.replace(Regex("!\\[(.*?)\\]\\(.*?\\)"), "$1")
    // 4. Links [text](url) -> text
    text = text.replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
    // 5. Headers: # Header -> Header
    text = text.replace(Regex("(?m)^#{1,6}\\s+(.+)$"), "$1")
    // 6. Blockquotes: > quote -> quote
    text = text.replace(Regex("(?m)^>\\s?"), "")
    // 7. Bold & Italic
    text = text.replace(Regex("\\*\\*\\*([^*]+)\\*\\*\\*"), "$1")
    text = text.replace(Regex("___([^_]+)___"), "$1")
    text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    text = text.replace(Regex("__([^_]+)__"), "$1")
    text = text.replace(Regex("(?<!\\*)\\*([^*\n]+)\\*(?!\\*)"), "$1")
    text = text.replace(Regex("(?<!_)_([^_\n]+)_(?!_)"), "$1")
    // 8. Strikethrough: ~~text~~ -> text
    text = text.replace(Regex("~~([^~]+)~~"), "$1")
    // 9. Horizontal rules: --- or *** or ___ -> empty line
    text = text.replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")
    // 10. Table formatting: remove table delimiter rows |---|---|
    text = text.replace(Regex("(?m)^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?$"), "")
    // Table rows: | cell | cell | -> cell | cell
    text = text.lines().joinToString("\n") { line ->
        if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
            line.trim().removeSurrounding("|", "|").split("|").joinToString("  |  ") { it.trim() }
        } else {
            line
        }
    }
    // 11. Normalize excess blank lines
    text = text.replace(Regex("\\n{3,}"), "\n\n")
    return text.trim()
}

// ─────────────────────────────────────────────────────────────────────────────
// Aligned table grid — fixed per-column widths (computed from the longest
// cell) so header + every row line up perfectly, markdown (**bold**, `code`,
// links) parsed inside each cell, alignment row (:--- / :---: / ---:) honored,
// zebra striping + card container for structure.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    alignments: List<androidx.compose.ui.text.style.TextAlign> = emptyList(),
    baseFontSize: TextUnit = 15.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (headers.isEmpty()) return
    val colCount = headers.size
    val colAlign = List(colCount) { idx ->
        alignments.getOrNull(idx) ?: androidx.compose.ui.text.style.TextAlign.Start
    }
    // Fixed width per column from longest raw cell → perfect vertical alignment.
    // ~6.5dp per char at 14-15sp + padding, clamped to keep narrow tables compact
    // and wide tables scrollable instead of squished.
    val colWidths = remember(headers, rows) {
        List(colCount) { c ->
            val maxLen = (listOf(headers[c]) + rows.map { it.getOrNull(c).orEmpty() })
                .maxOf { it.replace(Regex("""[*_`~\[\]()|]"""), "").trim().length }
            (((maxLen * 6.5f) + 28f).coerceIn(96f, 220f)).dp
        }
    }
    val headerStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = baseFontSize,
        lineHeight = (baseFontSize.value * 1.4f).sp,
        color = textColor,
        fontWeight = FontWeight.Bold,
    )
    val cellStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = baseFontSize,
        lineHeight = (baseFontSize.value * 1.45f).sp,
        color = textColor,
    )
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                headers.forEachIndexed { c, h ->
                    TableCellText(
                        raw = h,
                        style = headerStyle,
                        textAlign = colAlign[c],
                        width = colWidths[c],
                        isHeader = true,
                        showRightDivider = c < colCount - 1,
                        dividerColor = border,
                        onLinkClick = onLinkClick,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = border)
            // Rows
            rows.forEachIndexed { r, row ->
                Row(
                    modifier = Modifier.background(
                        if (r % 2 == 1) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                        else Color.Transparent
                    )
                ) {
                    row.forEachIndexed { c, cell ->
                        TableCellText(
                            raw = cell,
                            style = cellStyle,
                            textAlign = colAlign[c],
                            width = colWidths[c],
                            isHeader = false,
                            showRightDivider = c < colCount - 1,
                            dividerColor = border,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
                if (r < rows.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp, color = border.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun TableCellText(
    raw: String,
    style: androidx.compose.ui.text.TextStyle,
    textAlign: androidx.compose.ui.text.style.TextAlign,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean,
    showRightDivider: Boolean,
    dividerColor: Color,
    onLinkClick: (String) -> Unit,
) {
    // Parse **bold** / *italic* / `code` / links so cells never show raw markers.
    val annotated = remember(raw) { inlineMarkdownNoCompose(raw) }
    Row(
        modifier = Modifier
            .width(width)
            .height(IntrinsicSize.Max),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = when (textAlign) {
                androidx.compose.ui.text.style.TextAlign.Center -> Alignment.TopCenter
                androidx.compose.ui.text.style.TextAlign.End -> Alignment.TopEnd
                else -> Alignment.TopStart
            },
        ) {
            androidx.compose.foundation.text.ClickableText(
                text = annotated,
                style = style.copy(textAlign = textAlign),
                onClick = { offset ->
                    annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { onLinkClick(it.item) }
                }
            )
        }
        if (showRightDivider) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(dividerColor.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
fun MarkdownCodeBlock(language: String, code: String, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope  = rememberCoroutineScope()

    Surface(
        color    = Color(0xFF1E1E1E),
        shape    = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(language.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    copied = true
                    scope.launch { delay(2000); copied = false }
                }, modifier = Modifier.size(24.dp)) {
                    Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, modifier = Modifier.size(14.dp), tint = if (copied) Color.Green else Color.Gray)
                }
            }
            Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp))
        }
    }
}
