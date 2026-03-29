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
