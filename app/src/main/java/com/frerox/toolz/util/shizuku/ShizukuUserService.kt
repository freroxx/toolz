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

import com.frerox.toolz.IUserService
import com.frerox.toolz.ICommandCallback
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

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

    override fun getClipboardText(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cmd clipboard get"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()

            if (output.isBlank()) return null

            // Primary parser: extract after "T:" prefix, before trailing " }"
            // Output format: ClipData { label } ( Item { T:actual text } )
            val tIdx = output.indexOf("T:")
            if (tIdx >= 0) {
                val content = output.substring(tIdx + 2)
                // Strip the trailing " }" that closes the Item block
                val trimmed = content.trimEnd()
                val result = if (trimmed.endsWith(" }")) {
                    trimmed.dropLast(2)
                } else if (trimmed.endsWith("}")) {
                    trimmed.dropLast(1).trimEnd()
                } else {
                    trimmed
                }
                return result.ifBlank { null }
            }

            // Fallback: extract between first and last double-quote
            // Works for simpler output formats on some ROMs
            if (output.startsWith("ClipData")) {
                val firstQuote = output.indexOf('"')
                val lastQuote = output.lastIndexOf('"')
                if (firstQuote in 0 until lastQuote) {
                    return output.substring(firstQuote + 1, lastQuote).ifBlank { null }
                }
            }

            // Last resort: return raw output if non-blank
            output.ifBlank { null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
