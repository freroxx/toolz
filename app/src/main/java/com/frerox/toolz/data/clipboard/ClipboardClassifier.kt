package com.frerox.toolz.data.clipboard

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardClassifier @Inject constructor() {

    private val colorRegex = Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")
    private val urlRegex = Regex("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("^[+]?[\\d\\s\\-().]{7,15}$")
    private val otpRegex = Regex("\\b\\d{4,6}\\b")
    private val otpKeywords = listOf("code", "verify", "otp", "confirmation", "pin", "token", "authentication")
    private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val mathRegex = Regex("[0-9]+\\s*[+\\-*/=√^%]\\s*[0-9]+")
    private val mathKeywords = listOf("sqrt", "sin", "cos", "tan", "log", "integral", "derivative", "equation", "sum", "π")
    private val personalKeywords = listOf(
        "love", "miss", "feeling", "happy", "sad", "home", "family", "heart",
        "friend", "sorry", "thank", "care", "remember", "dream", "wish",
        "birthday", "anniversary", "mom", "dad", "brother", "sister"
    )
    private val codeKeywords = listOf(
        "function", "import", "class", "def ", "return", "const ", "val ", "var ",
        "public", "private", "void", "int ", "string", "boolean", "if (", "for (",
        "while (", "switch", "=>", "->", "println", "console.log", "System.out"
    )
    private val addressKeywords = listOf(
        "street", "st.", "avenue", "ave.", "boulevard", "blvd", "road", "rd.",
        "drive", "dr.", "lane", "ln.", "zip", "apt", "suite", "floor"
    )

    fun classify(text: String): String {
        val trimmed = text.trim()
        
        // Color hex codes
        if (colorRegex.matches(trimmed)) return "COLOR"
        
        // Email
        if (emailRegex.matches(trimmed)) return "EMAIL"
        
        // URL
        if (urlRegex.containsMatchIn(trimmed) && (trimmed.startsWith("http") || trimmed.startsWith("www"))) return "URL"
        
        // OTP codes — digit sequences near verification keywords
        val lowerText = trimmed.lowercase()
        if (otpRegex.containsMatchIn(trimmed) && otpKeywords.any { lowerText.contains(it) }) return "OTP"
        
        // Phone number
        if (phoneRegex.matches(trimmed.replace(" ", ""))) return "PHONE"
        
        // Code / Programming
        if (codeKeywords.count { lowerText.contains(it) } >= 2 || (trimmed.contains("{") && trimmed.contains("}"))) return "CODE"
        
        // Maths expressions
        if (mathRegex.containsMatchIn(trimmed) || mathKeywords.any { lowerText.contains(it) }) return "MATHS"
        
        // Address
        if (addressKeywords.count { lowerText.contains(it) } >= 2) return "ADDRESS"
        
        // Personal / Emotional
        if (personalKeywords.count { lowerText.contains(it) } >= 2) return "PERSONAL"
        
        return "TEXT"
    }

    fun getTypeEmoji(type: String): String = when (type) {
        "COLOR" -> "🎨"
        "URL" -> "🔗"
        "PHONE" -> "📞"
        "OTP" -> "🔐"
        "EMAIL" -> "📧"
        "MATHS" -> "🧮"
        "PERSONAL" -> "💜"
        "CODE" -> "💻"
        "ADDRESS" -> "📍"
        else -> "📋"
    }
}
