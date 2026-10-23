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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Maps a note's stored font style to the family used while rendering it. */
fun noteFontFamily(style: String): FontFamily = when (style) {
    "SERIF"     -> FontFamily.Serif
    "MONOSPACE" -> FontFamily.Monospace
    "CASUAL"    -> FontFamily.Cursive
    else        -> FontFamily.Default
}

/**
 * Rich note body: renders markdown through the app's shared parser while
 * keeping the note's own typography (chosen font, bold/italic, size) as the
 * base style — a styled note still looks like itself. Inline emphasis from
 * the markdown spans composes on top of the base weight.
 */
@Composable
fun NoteMarkdownBody(
    content: String,
    baseStyle: TextStyle,
    baseColor: Color,
    strongColor: Color,
    modifier: Modifier = Modifier,
) {
    val segments = remember(content) { parseMarkdownToSegments(content) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is MdSegment.Header -> {
                    val scale = when (seg.level) {
                        1 -> 1.35f
                        2 -> 1.2f
                        else -> 1.08f
                    }
                    Text(
                        inlineMarkdownNoCompose(seg.text),
                        style = baseStyle.copy(
                            fontSize   = (baseStyle.fontSize.value * scale).sp,
                            lineHeight = (baseStyle.fontSize.value * scale * 1.3f).sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = strongColor,
                    )
                }
                is MdSegment.Paragraph -> Text(seg.content, style = baseStyle, color = baseColor)
                is MdSegment.BulletItem -> Row(
                    Modifier.padding(start = (seg.depth * 12).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (seg.isChecked != null) {
                        Icon(
                            if (seg.isChecked) Icons.Rounded.Check else Icons.Rounded.CheckBoxOutlineBlank,
                            null,
                            Modifier.size(15.dp).offset(y = 2.dp),
                            tint = baseColor.copy(if (seg.isChecked) 1f else 0.45f),
                        )
                    } else {
                        Box(
                            Modifier
                                .offset(y = 7.dp)
                                .size(5.dp)
                                .background(baseColor.copy(0.75f), CircleShape)
                        )
                    }
                    Text(
                        seg.content,
                        style = baseStyle,
                        color = baseColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdSegment.NumberedItem -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${seg.index}.",
                        style = baseStyle,
                        fontWeight = FontWeight.ExtraBold,
                        color = strongColor,
                    )
                    Text(
                        seg.content,
                        style = baseStyle,
                        color = baseColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdSegment.Blockquote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(baseColor.copy(0.3f), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        seg.content,
                        style = baseStyle.copy(fontStyle = FontStyle.Italic),
                        color = baseColor.copy(0.85f),
                    )
                }
                is MdSegment.Code -> Text(
                    seg.code.trimEnd('\n'),
                    style = baseStyle.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (baseStyle.fontSize.value * 0.85f).sp,
                        lineHeight = (baseStyle.fontSize.value * 0.85f * 1.4f).sp,
                    ),
                    color = baseColor.copy(0.9f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(baseColor.copy(0.07f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                is MdSegment.Table -> MarkdownSegment(
                    seg = seg,
                    baseFontSize = (baseStyle.fontSize.value * 0.9f).sp,
                    textColor = baseColor,
                )
                MdSegment.Divider -> HorizontalDivider(
                    Modifier.padding(vertical = 2.dp),
                    thickness = 0.5.dp,
                    color = baseColor.copy(0.2f),
                )
            }
        }
    }
}
