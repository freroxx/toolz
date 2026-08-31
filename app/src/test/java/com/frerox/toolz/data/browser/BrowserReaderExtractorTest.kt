package com.frerox.toolz.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserReaderExtractorTest {
    @Test fun `decodes evaluate javascript payload`() {
        val text = "A".repeat(100)
        val article = BrowserReaderExtractor.parseJavascriptResult(
            "\"{\\\"title\\\":\\\"A story\\\",\\\"source\\\":\\\"example.com\\\",\\\"text\\\":\\\"$text\\\"}\"",
        )
        requireNotNull(article)
        assertEquals("A story", article.title)
        assertEquals("example.com", article.source)
    }

    @Test fun `rejects pages without meaningful article content`() {
        assertNull(BrowserReaderExtractor.parseJavascriptResult("\"{\\\"text\\\":\\\"too short\\\"}\""))
    }
}
