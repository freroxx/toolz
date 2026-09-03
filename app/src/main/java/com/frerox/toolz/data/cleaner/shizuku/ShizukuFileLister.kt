/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Read-only privileged listing over Shizuku shell. Used ONLY as a fallback
 * when the File API cannot list a directory (notably Android/obb, which is
 * unlistable even with All-files access). Never deletes — findings flow back
 * through the normal trash/restore pipeline.
 */

package com.frerox.toolz.data.cleaner.shizuku

import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuFileLister @Inject constructor(
    private val exec: ShizukuShellExecutor
) {
    data class DirEntry(val name: String, val path: String, val sizeBytes: Long)

    fun isUsable(): Boolean = try { ShizukuHelper.isAuthorized() } catch (_: Exception) { false }

    /**
     * Lists immediate subdirectories of [dir] with sizes via `du`.
     * Returns null when Shizuku is unavailable, the command fails/times out,
     * or the output can't be parsed — callers must fall back gracefully.
     */
    suspend fun listDirs(dir: File, timeoutMs: Long = 8_000): List<DirEntry>? {
        if (!isUsable()) return null
        return try {
            val q = dir.absolutePath.replace("'", "'\\''")
            val res = exec.executeForResult("du -sb -- '$q'/* 2>/dev/null", timeoutMs)
            if (!res.isSuccess || res.stdout.isBlank()) return null
            val out = res.stdout.lines().mapNotNull { parseDuLine(it, dir.absolutePath) }
            out.ifEmpty { null }
        } catch (_: Exception) { null }
    }

    companion object {
        /** Lexical normalization (resolves `.`/`..`) so containment checks can't be fooled. Pure. */
        fun normalizePath(path: String): String {
            val absolute = path.startsWith("/")
            val parts = ArrayDeque<String>()
            for (seg in path.split('/')) {
                when {
                    seg.isEmpty() || seg == "." -> {}
                    seg == ".." -> if (parts.isNotEmpty()) parts.removeLast()
                    else -> parts.add(seg)
                }
            }
            return (if (absolute) "/" else "") + parts.joinToString("/")
        }

        /** Parses one `du -sb` line ("<bytes>\t<path>"). Pure — unit tested. */
        fun parseDuLine(line: String, parentPath: String): DirEntry? {
            val tab = line.indexOf('\t')
            if (tab <= 0) return null
            val size = line.substring(0, tab).trim().toLongOrNull() ?: return null
            if (size < 0) return null
            val raw = line.substring(tab + 1).trim().trimEnd('/')
            if (raw.isEmpty()) return null
            val normParent = normalizePath(parentPath.trimEnd('/'))
            val norm = normalizePath(raw)
            // Strict containment: the entry itself must live INSIDE the parent (parent excluded).
            if (!norm.startsWith("$normParent/")) return null
            val name = norm.substringAfterLast('/')
            if (name.isEmpty() || name == "." || name == "..") return null
            return DirEntry(name, norm, size)
        }
    }
}
