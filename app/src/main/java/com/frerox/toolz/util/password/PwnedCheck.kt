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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object PwnedCheck {
    suspend fun isPwned(password: String): Int = withContext(Dispatchers.IO) {
        try {
            val hash = sha1(password)
            val prefix = hash.substring(0, 5)
            val suffix = hash.substring(5)

            val url = URL("https://api.pwnedpasswords.com/range/$prefix")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val (pwnedSuffix, count) = line.split(":")
                        if (pwnedSuffix.equals(suffix, ignoreCase = true)) {
                            return@withContext count.toInt()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return bytes.joinToString("") { "%02X".format(it) }.uppercase(Locale.ROOT)
    }
}
