package com.frerox.toolz.util.password

import java.security.SecureRandom

object PasswordGenerator {
    private val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val NUMBERS = "0123456789"
    private val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val charPool = StringBuilder(LOWERCASE)
        if (includeUppercase) charPool.append(UPPERCASE)
        if (includeNumbers) charPool.append(NUMBERS)
        if (includeSymbols) charPool.append(SYMBOLS)

        val random = SecureRandom()
        return (1..length)
            .map { charPool[random.nextInt(charPool.length)] }
            .joinToString("")
    }

    fun calculateStrength(password: String): Int {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        
        return (score / 1.5).toInt().coerceIn(0, 4)
    }
}
