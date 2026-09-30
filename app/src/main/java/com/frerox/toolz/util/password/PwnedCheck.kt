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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object PwnedCheck {
    private const val TAG = "PwnedCheck"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    sealed interface PwnedResult {
        data class Checked(val count: Int) : PwnedResult
        data class Failed(val reason: String) : PwnedResult
    }

    /**
     * K-anonymity breach check. Returns a [PwnedResult] so callers can
     * distinguish "not breached" from "network failed".
     */
    suspend fun checkPwned(password: String): PwnedResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val hash = sha1(password)
            val prefix = hash.substring(0, 5)
            val suffix = hash.substring(5)

            connection = (URL("https://api.pwnedpasswords.com/range/$prefix").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Add-Padding", "true")
                setRequestProperty("User-Agent", "Toolz-Password-Vault")
            }

            when (connection.responseCode) {
                200 -> {
                    connection.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                        for (line in lines) {
                            val sep = line.indexOf(':')
                            if (sep <= 0) continue
                            val pwnedSuffix = line.substring(0, sep).trim()
                            val count = line.substring(sep + 1).trim().toIntOrNull() ?: continue
                            if (pwnedSuffix.equals(suffix, ignoreCase = true)) {
                                return@withContext PwnedResult.Checked(count)
                            }
                        }
                    }
                    PwnedResult.Checked(0)
                }
                429 -> PwnedResult.Failed("rate-limited")
                else -> PwnedResult.Failed("http-${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Breach check failed", e)
            PwnedResult.Failed(e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Legacy helper kept for existing call sites: returns the breach count,
     * or 0 when the check fails. Prefer [checkPwned] for new code.
     */
    suspend fun isPwned(password: String): Int =
        when (val r = checkPwned(password)) {
            is PwnedResult.Checked -> r.count
            is PwnedResult.Failed -> 0
        }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }.uppercase(Locale.ROOT)
    }
}
