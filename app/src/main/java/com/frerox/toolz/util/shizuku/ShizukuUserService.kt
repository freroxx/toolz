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

package com.frerox.toolz.util.shizuku

import android.content.ClipData
import android.os.IBinder
import com.frerox.toolz.ICommandCallback
import com.frerox.toolz.IUserService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Runs as the Shizuku shell user (UID 2000).
 *
 * Clipboard reads must not rely on `cmd clipboard get`: AOSP does not ship a
 * `clipboard` shell command, so on most devices that path returns nothing and
 * background capture silently does nothing. The primary path below calls the
 * clipboard service binder directly as the shell UID, which is allowed to read
 * the clipboard. The shell command remains only as a ROM-specific fallback.
 */
class ShizukuUserService : IUserService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun runCommand(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun runCommandWithCallback(cmd: String, callback: ICommandCallback) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))

            val outThread = Thread {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                try {
                    while (reader.readLine().also { line = it } != null) {
                        callback.onOutput(line)
                    }
                } catch (e: Exception) {
                    callback.onError("STDOUT Error: ${e.message}")
                }
            }

            val errThread = Thread {
                val reader = BufferedReader(InputStreamReader(process.errorStream))
                var line: String?
                try {
                    while (reader.readLine().also { line = it } != null) {
                        callback.onError(line)
                    }
                } catch (e: Exception) {
                    callback.onError("STDERR Error: ${e.message}")
                }
            }

            outThread.start()
            errThread.start()

            val exitCode = process.waitFor()
            outThread.join()
            errThread.join()

            callback.onExit(exitCode)
        } catch (e: Exception) {
            callback.onError("Exception: ${e.message}")
            callback.onExit(-1)
        }
    }

    override fun getClipboardText(): String? =
        readClipboardViaBinder() ?: readClipboardViaShellCommand()

    /**
     * Calls `IClipboard.getPrimaryClip` directly. The method signature has
     * changed across Android releases, so the arguments are derived from the
     * reflected parameter types instead of hardcoding one version.
     */
    private fun readClipboardViaBinder(): String? = try {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, "clipboard") as? IBinder
        if (binder == null) {
            null
        } else {
            val stub = Class.forName("android.content.IClipboard\$Stub")
            val clipboard = stub
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
            if (clipboard == null) {
                null
            } else {
                val method = clipboard.javaClass.methods
                    .filter { it.name == "getPrimaryClip" }
                    .minByOrNull { it.parameterCount }
                val clip = method?.let { candidate ->
                    var stringIndex = 0
                    val args: Array<Any?> = candidate.parameterTypes.map { type ->
                        when {
                            // First string is the calling package; later strings
                            // are attribution tags, which may be null.
                            type == String::class.java -> {
                                stringIndex++
                                if (stringIndex == 1) "com.android.shell" else null
                            }
                            type == Int::class.javaPrimitiveType -> 0
                            type == Boolean::class.javaPrimitiveType -> false
                            else -> null
                        }
                    }.toTypedArray()
                    candidate.invoke(clipboard, *args) as? ClipData
                }
                clip?.let(::clipToText)
            }
        }
    } catch (_: Throwable) {
        // Hidden API access can be denied on some ROMs; the shell fallback
        // below still gets a chance.
        null
    }

    private fun clipToText(clip: ClipData): String? {
        if (clip.itemCount == 0) return null
        val parts = mutableListOf<String>()
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index) ?: continue
            val text = item.text?.toString()
            val html = item.htmlText
            when {
                !text.isNullOrBlank() -> parts.add(text)
                !html.isNullOrBlank() -> parts.add(stripHtml(html))
            }
        }
        return parts.joinToString("\n").ifBlank { null }
    }

    /** ROM-specific fallback; never blocks the caller for more than 3s. */
    private fun readClipboardViaShellCommand(): String? {
        return try {
            val process = ProcessBuilder("sh", "-c", "cmd clipboard get 2>/dev/null")
                .redirectErrorStream(true)
                .start()
            // Wait before reading so a hanging command can never block the
            // binder thread forever. The output of `cmd clipboard get` is tiny,
            // so it cannot fill the pipe buffer before exit.
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            val output = process.inputStream.use { input ->
                BufferedReader(InputStreamReader(input)).readText().trim()
            }
            if (output.isBlank() || output.contains("No primary clip")) return null

            // Collect every "T:<text>" item block; join multi-item clips.
            val items = mutableListOf<String>()
            var searchFrom = 0
            while (true) {
                val tIdx = output.indexOf("T:", searchFrom)
                if (tIdx < 0) break
                var end = output.indexOf(" }", tIdx)
                if (end < 0) end = output.indexOf("})", tIdx)
                if (end < 0) end = output.length
                val chunk = output.substring(tIdx + 2, end)
                    .replace("\\n", "\n")
                    .trim()
                if (chunk.isNotBlank() && chunk != "null") items.add(chunk)
                searchFrom = end + 2
                if (searchFrom >= output.length) break
            }
            if (items.isNotEmpty()) return items.joinToString("\n").ifBlank { null }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun stripHtml(html: String): String = try {
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
    } catch (_: Exception) {
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
