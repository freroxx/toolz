package com.frerox.toolz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
    data class BulletItem(val content: AnnotatedString, val depth: Int = 0) : MdSegment()
    data class NumberedItem(val index: Int, val content: AnnotatedString) : MdSegment()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdSegment()
    object Divider : MdSegment()
}

@Composable
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

        // Bullet list
        if (line.matches(Regex("^(\\s*)[\\-\\*\\+] .+"))) {
            val depth   = line.indexOfFirst { !it.isWhitespace() } / 2
            val content = line.trim().drop(2)
            segments += MdSegment.BulletItem(inlineMarkdown(content), depth)
            i++
            continue
        }

        // Numbered list
        val numberedMatch = Regex("^(\\d+)[\\.\\)] (.+)").find(line.trim())
        if (numberedMatch != null) {
            val idx     = numberedMatch.groupValues[1].toIntOrNull() ?: 1
            val content = numberedMatch.groupValues[2]
            segments += MdSegment.NumberedItem(idx, inlineMarkdown(content))
            i++
            continue
        }

        // Blank line
        if (line.isBlank()) {
            i++
            continue
        }

        // Tables
        if (line.trim().startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().startsWith("|") && lines[i + 1].contains("---")) {
            val headers = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            i += 2 // skip header and separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() })
                i++
            }
            segments += MdSegment.Table(headers, rows)
            continue
        }

        // Normal paragraph
        segments += MdSegment.Paragraph(inlineMarkdown(line))
        i++
    }
    return segments
}

@Composable
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    data class Token(val start: Int, val end: Int, val content: String, val type: String)
    val tokens = mutableListOf<Token>()

    fun scan(regex: String, type: String) {
        Regex(regex).findAll(text).forEach { m ->
            tokens += Token(m.range.first, m.range.last + 1, m.groupValues.getOrElse(1) { m.value }, type)
        }
    }

    scan("""\*\*(.+?)\*\*""", "bold")
    scan("""\*(.+?)\*""", "italic")
    scan("""`(.+?)`""", "code")
    scan("""~~(.+?)~~""", "strike")

    val clean = mutableListOf<Token>()
    var cursor = 0
    for (tok in tokens.sortedBy { it.start }) {
        if (tok.start >= cursor) {
            clean += tok
            cursor = tok.end
        }
    }

    cursor = 0
    for (tok in clean) {
        if (tok.start > cursor) append(text.substring(cursor, tok.start))
        when (tok.type) {
            "bold"   -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(tok.content) }
            "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(tok.content) }
            "code"   -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colorScheme.onSurface.copy(0.08f), color = colorScheme.primary)) { append(tok.content) }
            "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(tok.content) }
            else     -> append(tok.content)
        }
        cursor = tok.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
fun MarkdownSegment(
    seg: MdSegment,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = 16.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface
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
            Text(seg.content, style = bodyStyle, modifier = modifier)
        }
        is MdSegment.BulletItem -> {
            Row(modifier = modifier.padding(start = (seg.depth * 12).dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size((baseFontSize.value / 3).dp).offset(y = (baseFontSize.value / 2).dp).background(textColor.copy(alpha = 0.8f), CircleShape))
                Text(seg.content, style = bodyStyle, modifier = Modifier.weight(1f))
            }
        }
        is MdSegment.NumberedItem -> {
            Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${seg.index}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = bodyStyle, modifier = Modifier.widthIn(min = (baseFontSize.value * 1.2f).dp))
                Text(seg.content, style = bodyStyle, modifier = Modifier.weight(1f))
            }
        }
        is MdSegment.Code -> MarkdownCodeBlock(language = seg.language, code = seg.code, modifier = modifier)
        is MdSegment.Table -> {
            Column(modifier = modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState())) {
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    seg.headers.forEach { header ->
                        Text(
                            text = header,
                            style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(8.dp).widthIn(min = 100.dp),
                            color = textColor
                        )
                    }
                }
                seg.rows.forEach { row ->
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                    Row {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                style = bodyStyle,
                                modifier = Modifier.padding(8.dp).widthIn(min = 100.dp),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
        MdSegment.Divider -> HorizontalDivider(modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
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
