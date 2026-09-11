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

package com.frerox.toolz.data.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PdfDocInfo(
    val title: String?,
    val author: String?,
    val subject: String?,
    val pageCount: Int,
    val isEncrypted: Boolean
)

data class PdfTocEntry(
    val title: String,
    val pageIndex: Int,
    val children: List<PdfTocEntry> = emptyList()
)

data class PdfPageMatch(
    val pageIndex: Int,
    val snippets: List<String>
)

/**
 * Embedded-text layer (PdfBox-Android). OCR stays as fallback for scanned pages.
 * All heavy work on Dispatchers.IO; per-document page-text cached in RAM.
 */
@Singleton
class PdfTextEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pageTextCache = ConcurrentHashMap<String, List<String>>()
    private val infoCache = ConcurrentHashMap<String, PdfDocInfo>()
    @Volatile private var pdfBoxInit = false

    private fun ensureInit() {
        if (!pdfBoxInit) {
            synchronized(this) {
                if (!pdfBoxInit) {
                    try {
                        PDFBoxResourceLoader.init(context.applicationContext)
                    } catch (_: Exception) { }
                    pdfBoxInit = true
                }
            }
        }
    }

    private fun loadDoc(uri: Uri): PDDocument? = try {
        ensureInit()
        val input = if (uri.scheme == "file") {
            val f = java.io.File(uri.path ?: return null)
            if (!f.isFile || !f.canRead()) return null
            java.io.FileInputStream(f)
        } else {
            context.contentResolver.openInputStream(uri) ?: return null
        }
        input.use { PDDocument.load(it) }
    } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        null
    } catch (_: Exception) {
        null
    }

    suspend fun getDocInfo(uri: Uri): PdfDocInfo? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        infoCache[key]?.let { return@withContext it }
        val doc = loadDoc(uri) ?: return@withContext PdfDocInfo(null, null, null, 0, true)
        try {
            val info = doc.documentInformation
            val result = PdfDocInfo(
                title = info.title?.takeIf { it.isNotBlank() },
                author = info.author?.takeIf { it.isNotBlank() },
                subject = info.subject?.takeIf { it.isNotBlank() },
                pageCount = doc.numberOfPages,
                isEncrypted = false
            )
            infoCache[key] = result
            result
        } finally {
            try { doc.close() } catch (_: Exception) { }
        }
    }

    suspend fun getPageText(uri: Uri, pageIndex: Int): String? = withContext(Dispatchers.IO) {
        getAllPageTexts(uri).getOrNull(pageIndex)
    }

    /** Extracts (and caches) every page's embedded text. Empty string = scanned page. */
    suspend fun getAllPageTexts(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val key = uri.toString()
        pageTextCache[key]?.let { return@withContext it }
        val doc = loadDoc(uri) ?: return@withContext emptyList()
        try {
            val n = doc.numberOfPages
            if (n <= 0 || n > 2000) return@withContext emptyList()
            val out = ArrayList<String>(n)
            // Page-by-page strip keeps memory flat on big docs.
            for (i in 0 until n) {
                try {
                    val stripper = PDFTextStripper()
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1
                    stripper.sortByPosition = true
                    out.add(stripper.getText(doc).trim())
                } catch (_: Exception) {
                    out.add("")
                }
            }
            pageTextCache[key] = out
            out
        } finally {
            try { doc.close() } catch (_: Exception) { }
        }
    }

    suspend fun isScannedPage(uri: Uri, pageIndex: Int): Boolean {
        val t = getPageText(uri, pageIndex) ?: return false
        return t.trim().length < 20
    }

    suspend fun search(uri: Uri, query: String, maxPages: Int = 200): List<PdfPageMatch> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val pages = getAllPageTexts(uri)
            val out = ArrayList<PdfPageMatch>()
            pages.forEachIndexed { idx, text ->
                if (idx >= maxPages && pages.size > maxPages) return@forEachIndexed
                if (text.contains(q, ignoreCase = true)) {
                    out.add(PdfPageMatch(idx, snippetsFor(text, q)))
                    if (out.size >= 100) return@withContext out
                }
            }
            out
        }

    private fun snippetsFor(text: String, q: String, radius: Int = 48): List<String> {
        val lower = text.lowercase()
        val needle = q.lowercase()
        val hits = ArrayList<String>()
        var from = 0
        while (hits.size < 3) {
            val at = lower.indexOf(needle, from)
            if (at < 0) break
            val s = (at - radius).coerceAtLeast(0)
            val e = (at + needle.length + radius).coerceAtMost(text.length)
            hits.add("…" + text.substring(s, e).replace(Regex("\\s+"), " ").trim() + "…")
            from = at + needle.length
        }
        return hits
    }

    suspend fun getOutline(uri: Uri): List<PdfTocEntry> = withContext(Dispatchers.IO) {
        val doc = loadDoc(uri) ?: return@withContext emptyList()
        try {
            val outline = doc.documentCatalog.documentOutline ?: return@withContext emptyList()
            outline.children().toList().mapNotNull { node ->
                try {
                    val title = (node as? com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem)?.title
                        ?: return@mapNotNull null
                    val dest = (node as? com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem)
                        ?.destination
                    var pageIdx = 0
                    try {
                        if (dest is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination) {
                            val p = dest.page
                            if (p != null) pageIdx = doc.pages.indexOf(p).coerceAtLeast(0)
                        }
                    } catch (_: Exception) { }
                    PdfTocEntry(title, pageIdx)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            try { doc.close() } catch (_: Exception) { }
        }
    }

    fun invalidate(uri: Uri) {
        pageTextCache.remove(uri.toString())
        infoCache.remove(uri.toString())
    }
}
