package com.frerox.toolz.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    private fun bullets(markdown: String) =
        parseMarkdownToSegments(markdown).filterIsInstance<MdSegment.BulletItem>()

    @Test
    fun looseBullet_withoutSpace_isParsedAsBullet() {
        val items = bullets("*gldoallxc")
        assertEquals(1, items.size)
        assertEquals("gldoallxc", items.single().content.text)
    }

    @Test
    fun looseBullets_multipleLines_doNotBecomeParagraph() {
        val segments = parseMarkdownToSegments("*first\n*second")
        val items = segments.filterIsInstance<MdSegment.BulletItem>()
        assertEquals(listOf("first", "second"), items.map { it.content.text })
        assertTrue(segments.none { it is MdSegment.Paragraph })
    }

    @Test
    fun strictBullet_stillWorks() {
        val items = bullets("- item\n* item\n+ item")
        assertEquals(listOf("item", "item", "item"), items.map { it.content.text })
    }

    @Test
    fun looseBullet_honoursCheckboxes() {
        val items = bullets("*[ ] todo\n*[x] done")
        assertEquals(listOf(false, true), items.map { it.isChecked })
        assertEquals(listOf("todo", "done"), items.map { it.content.text })
    }

    @Test
    fun bulletWithInlineEmphasis_staysBullet() {
        val items = bullets("*item with *emphasis*")
        assertEquals(1, items.size)
        assertEquals("item with emphasis", items.single().content.text)
    }

    @Test
    fun boldLine_isNotTreatedAsBullet() {
        val segments = parseMarkdownToSegments("**bold**")
        assertTrue(segments.none { it is MdSegment.BulletItem })
        assertTrue(segments.any { it is MdSegment.Paragraph })
    }

    @Test
    fun italicLine_isNotTreatedAsBullet() {
        val segments = parseMarkdownToSegments("*italic*")
        assertTrue(segments.none { it is MdSegment.BulletItem })
        assertTrue(segments.any { it is MdSegment.Paragraph })
    }

    @Test
    fun multiWordItalicLine_isNotTreatedAsBullet() {
        val segments = parseMarkdownToSegments("*italic text*")
        assertTrue(segments.none { it is MdSegment.BulletItem })
        assertTrue(segments.any { it is MdSegment.Paragraph })
    }

    @Test
    fun leadingEmphasisWithTrailingText_isNotTreatedAsBullet() {
        val segments = parseMarkdownToSegments("*Note*: remember this")
        assertTrue(segments.none { it is MdSegment.BulletItem })
        assertTrue(segments.any { it is MdSegment.Paragraph })
    }

    @Test
    fun horizontalRule_isNotTreatedAsBullet() {
        val segments = parseMarkdownToSegments("***")
        assertTrue(segments.none { it is MdSegment.BulletItem })
        assertEquals(1, segments.count { it is MdSegment.Divider })
    }

    @Test
    fun stripMarkdown_removesLooseBulletMarkerButKeepsEmphasis() {
        assertEquals("gldoallxc", stripMarkdown("*gldoallxc"))
        assertEquals("Note: remember", stripMarkdown("*Note*: remember"))
    }

    @Test
    fun containsMarkdown_detectsCommonSyntax() {
        assertTrue(containsMarkdown("*loose bullet"))
        assertTrue(containsMarkdown("* item"))
        assertTrue(containsMarkdown("**bold**"))
        assertTrue(containsMarkdown("# Heading"))
        assertTrue(containsMarkdown("`code`"))
        assertTrue(containsMarkdown("a\n- item"))
    }

    @Test
    fun containsMarkdown_ignoresPlainText() {
        assertFalse(containsMarkdown(""))
        assertFalse(containsMarkdown("just a normal note\nwith two lines"))
        assertFalse(containsMarkdown("2 * 3 = 6"))
        assertFalse(containsMarkdown("a - b"))
    }
}
