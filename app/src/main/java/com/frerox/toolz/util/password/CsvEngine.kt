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

package com.frerox.toolz.util.password

import android.content.Context
import android.net.Uri
import com.frerox.toolz.data.password.PasswordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object CsvEngine {
    const val MAX_IMPORT_ROWS = 5000

    data class ImportResult(
        val imported: List<PasswordEntity>,
        val skipped: Int,
        val truncated: Boolean
    )

    suspend fun importCsv(context: Context, uri: Uri): List<PasswordEntity> =
        importCsvDetailed(context, uri).imported

    suspend fun importCsvDetailed(context: Context, uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            val passwords = mutableListOf<PasswordEntity>()
            var skipped = 0
            var truncated = false
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                    val rawHeader = reader.readLine() ?: return@withContext ImportResult(emptyList(), 0, false)
                    val header = parseLine(rawHeader).map { normalizeHeader(it) }

                    val nameIdx = header.indexOfFirst { it == "name" || it == "title" || it == "account" }
                        .takeIf { it >= 0 }
                        ?: header.indexOfFirst { "name" in it }
                    val urlIdx = header.indexOfFirst { it in setOf("url", "uri", "login_uri", "website", "origin") }
                        .takeIf { it >= 0 }
                        ?: header.indexOfFirst { "url" in it || "uri" in it }
                    val userIdx = header.indexOfFirst { it in setOf("username", "login_username", "user", "login", "email") }
                        .takeIf { it >= 0 }
                        ?: header.indexOfFirst { "username" in it || "login_username" in it }
                    val passIdx = header.indexOfFirst { it in setOf("password", "login_password", "pass", "passwd") }
                        .takeIf { it >= 0 }
                        ?: header.indexOfFirst { "password" in it || "login_password" in it }

                    if (passIdx < 0) return@withContext ImportResult(emptyList(), 0, false)

                    var rowCount = 0
                    while (true) {
                        val raw = reader.readLine() ?: break
                        if (raw.isBlank()) continue
                        if (rowCount >= MAX_IMPORT_ROWS) {
                            truncated = true
                            break
                        }
                        rowCount++
                        val parts = parseLine(raw)
                        val passwordText = parts.getOrNull(passIdx) ?: ""
                        // Never trim secrets: leading/trailing spaces can be significant.
                        if (passwordText.isEmpty()) {
                            skipped++
                            continue
                        }
                        val name = parts.getOrNull(nameIdx)
                            ?.takeIf { it.isNotBlank() } ?: "Unknown"
                        val url = parts.getOrNull(urlIdx)?.takeIf { it.isNotBlank() }
                        val username = parts.getOrNull(userIdx) ?: ""
                        passwords.add(
                            PasswordEntity(
                                name = name,
                                url = url,
                                username = username,
                                password = passwordText,
                                strength = PasswordGenerator.calculateStrength(passwordText)
                            )
                        )
                    }
                }
            }
            ImportResult(passwords, skipped, truncated)
        }

    suspend fun exportCsv(passwords: List<PasswordEntity>): String = withContext(Dispatchers.IO) {
        val builder = StringBuilder()
        builder.append("name,url,username,password\n")
        passwords.forEach {
            builder.append(escapeField(it.name)).append(',')
            builder.append(escapeField(it.url ?: "")).append(',')
            builder.append(escapeField(it.username)).append(',')
            builder.append(escapeField(it.password)).append('\n')
        }
        builder.toString()
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim()
            .removePrefix("\uFEFF")
            .removeSurrounding("\"")
            .trim()
            .lowercase()
            .replace('-', '_')

    /**
     * Minimal RFC 4180 parser: handles quoted fields, "" escapes and commas.
     * Multi-line quoted fields are out of scope and treated as separate rows.
     */
    fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out += cur.toString()
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out.map { it.removePrefix("\uFEFF") }
    }

    private fun escapeField(value: String): String {
        var v = value
        // Mitigate CSV formula injection when opened in spreadsheet apps.
        if (v.isNotEmpty() && (v[0] == '=' || v[0] == '+' || v[0] == '-' || v[0] == '@' ||
                    v[0] == '|' || v[0] == '%' || v[0] == '\t' || v[0] == '\r')
        ) {
            v = "'$v"
        }
        return if (v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else {
            v
        }
    }
}
