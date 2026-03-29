package com.frerox.toolz.util.password

import android.content.Context
import android.net.Uri
import com.frerox.toolz.data.password.PasswordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvEngine {
    suspend fun importCsv(context: Context, uri: Uri): List<PasswordEntity> = withContext(Dispatchers.IO) {
        val passwords = mutableListOf<PasswordEntity>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val header = reader.readLine()?.split(",") ?: return@withContext emptyList()
                
                // Common formats: 
                // Chrome: name,url,username,password
                // Bitwarden: folder,favorite,type,name,notes,fields,repropt,login_uri,login_username,login_password,login_totp
                
                val nameIdx = header.indexOfFirst { it.contains("name", true) }
                val urlIdx = header.indexOfFirst { it.contains("url", true) || it.contains("uri", true) }
                val userIdx = header.indexOfFirst { it.contains("username", true) || it.contains("login_username", true) }
                val passIdx = header.indexOfFirst { it.contains("password", true) || it.contains("login_password", true) }

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line?.split(",") ?: continue
                    if (parts.size > maxOf(nameIdx, urlIdx, userIdx, passIdx)) {
                        val passwordText = parts[passIdx].trim()
                        passwords.add(
                            PasswordEntity(
                                name = parts.getOrNull(nameIdx)?.trim() ?: "Unknown",
                                url = parts.getOrNull(urlIdx)?.trim(),
                                username = parts.getOrNull(userIdx)?.trim() ?: "",
                                password = passwordText,
                                strength = PasswordGenerator.calculateStrength(passwordText)
                            )
                        )
                    }
                }
            }
        }
        passwords
    }

    suspend fun exportCsv(passwords: List<PasswordEntity>): String = withContext(Dispatchers.Default) {
        val builder = StringBuilder()
        builder.append("name,url,username,password\n")
        passwords.forEach {
            builder.append("${it.name},${it.url ?: ""},${it.username},${it.password}\n")
        }
        builder.toString()
    }
}
