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

package com.frerox.toolz.data.clipboard

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline, deterministic clipboard classifier.
 *
 * It never performs network calls and never logs content. Every detector is
 * ordered so the most security-relevant classification wins: OTP/card/secret
 * are checked before link/code so a pasted "code: 123456" is not stored as
 * plain text. AI may later refine [ClipboardEntry.type] but can never clear a
 * sensitive flag.
 */
@Singleton
class ClipboardClassifier @Inject constructor() {

    private val colorRegex = Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8}|[A-Fa-f0-9]{3})$")
    private val urlRegex = Regex(
        "^(https?://|ftp://|www\\.)[^\\s<>\"']+$",
        RegexOption.IGNORE_CASE,
    )
    private val urlAnywhereRegex = Regex(
        "(https?://|www\\.)[^\\s<>\"']+",
        RegexOption.IGNORE_CASE,
    )
    private val socialRegex = Regex(
        "(instagram\\.com|twitter\\.com|x\\.com|linkedin\\.com|github\\.com|facebook\\.com|" +
            "youtube\\.com|youtu\\.be|t\\.me|reddit\\.com|tiktok\\.com|threads\\.net|mastodon)",
        RegexOption.IGNORE_CASE,
    )
    private val phoneRegex = Regex("^[+]?[\\d\\s\\-().]{7,20}$")
    private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val mathRegex = Regex("^[\\d\\s.,()+\\-*/=%^√π]+$")
    private val mathKeywordRegex = Regex(
        "\\b(sqrt|sin|cos|tan|log|ln|integral|derivative|equation|sum|theta|delta)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val cryptoRegex = Regex(
        "\\b(bc1[a-zA-HJ-NP-Z0-9]{25,90}|[13][a-km-zA-HJ-NP-Z1-9]{25,34}|0x[a-fA-F0-9]{40}|" +
            "4[0-9AB][1-9A-HJ-NP-Za-km-z]{93}|[48][0-9AB][1-9A-HJ-NP-Za-km-z]{94,105})\\b",
    )
    private val jsonRegex = Regex("^\\s*[\\[{].*[\\]}]\\s*$", RegexOption.DOT_MATCHES_ALL)
    private val otpDigitsRegex = Regex("^[\\d\\s\\-]{4,9}$")
    private val otpInlineRegex = Regex("\\b\\d{4,8}\\b")
    private val otpKeywords = listOf(
        "otp", "one-time", "one time", "verification code", "verify", "confirmation code",
        "security code", "auth code", "authentication code", "passcode", "2fa", "two-factor",
        "login code", "sms code", "pin code",
    )
    private val secretLabelRegex = Regex(
        "(?i)\\b(password|passwd|pwd|passphrase|secret|api[_\\s-]?key|access[_\\s-]?token|" +
            "auth[_\\s-]?token|client[_\\s-]?secret|private[_\\s-]?key|bearer)\\b\\s*[:=]\\s*\\S+",
    )
    private val cardLikeRegex = Regex("^[\\d][\\d \\-]{11,25}$")
    private val highEntropyRegex = Regex("^[A-Za-z0-9!@#\\$%^&*()_+\\-=\\[\\]{};:'\",.<>/?\\\\|`~]{10,128}$")
    private val codeKeywords = listOf(
        "function ", "fun ", "def ", "class ", "import ", "return ", "const ", "val ",
        "var ", "public ", "private ", "protected ", "void ", "interface ", "override ",
        "suspend ", "package ", "namespace ", "using ", "SELECT ", "UPDATE ", "INSERT ",
        "DELETE FROM", "console.log", "println(", "System.out", "module.exports", "=>",
        "->", "</", "/>", "#!/", "docker ", "git ", "npm ", "pip install", "gradle",
    )
    private val addressKeywords = listOf(
        "street", " st.", "avenue", " ave.", "boulevard", " blvd", " road", " rd.",
        " drive", " dr.", " lane", " ln.", " zip", " apt", " suite", " floor",
        "p.o. box", "postal code",
    )
    private val personalKeywords = listOf(
        "love", "miss", "feeling", "happy", "sad", "family", "heart", "friend",
        "sorry", "thank", "care", "remember", "dream", "wish", "birthday",
        "anniversary", "mom", "dad", "brother", "sister", "babe", "darling",
    )

    // ── Public API ────────────────────────────────────────────────────────

    /** Legacy type-only classification. */
    fun classify(text: String): String = classifyDetailed(text).type

    /**
     * Full classification used by every capture path.
     *
     * @param isSensitiveExtra the clip carries `ClipDescription.EXTRA_IS_SENSITIVE`
     *   (set by password managers and some keyboards).
     * @param passwordVaultMatch an exact match against the local Password Vault.
     */
    fun classifyDetailed(
        text: String,
        isSensitiveExtra: Boolean = false,
        passwordVaultMatch: Boolean = false,
    ): ClipClassification {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ClipClassification(ClipboardType.TEXT)

        val lower = trimmed.lowercase()
        val type = detectType(trimmed, lower)
        val reason = sensitivityReason(trimmed, lower, isSensitiveExtra, passwordVaultMatch)

        return ClipClassification(
            type = if (reason != null && type == ClipboardType.TEXT) {
                when (reason) {
                    ClipSensitivityReason.OTP -> ClipboardType.OTP
                    ClipSensitivityReason.CARD -> ClipboardType.CARD
                    ClipSensitivityReason.PASSWORD_VAULT,
                    ClipSensitivityReason.SECRET,
                    ClipSensitivityReason.LABEL,
                    -> ClipboardType.SECRET
                    else -> type
                }
            } else {
                type
            },
            isSensitive = reason != null,
            reason = reason,
            passwordVaultMatch = passwordVaultMatch,
        )
    }

    /** True when the clip must be masked and excluded from AI. */
    fun isSensitive(text: String): Boolean =
        sensitivityReason(text.trim(), text.trim().lowercase(), false, false) != null

    /** Human-readable masking used everywhere content leaves the DB. */
    fun mask(text: String): String {
        if (text.isBlank()) return text
        val lines = text.lines()
        val masked = lines.map { line ->
            if (line.isBlank()) line
            else "•".repeat(line.length.coerceIn(4, 24))
        }
        return masked.joinToString("\n")
    }

    /** Mask a single-line preview, preserving a tiny amount of structure. */
    fun maskPreview(text: String, maxLines: Int = 2): String {
        val preview = text.lines().take(maxLines).joinToString("\n")
        return mask(preview)
    }

    /** Luhn checksum for card numbers (spaces/dashes ignored). */
    fun luhnValid(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var double = false
        for (i in digits.indices.reversed()) {
            var value = digits[i] - '0'
            if (double) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
            double = !double
        }
        return sum % 10 == 0
    }

    /** Extracts a likely one-time code from text, or null. */
    fun extractOtp(text: String): String? {
        val compact = text.trim()
        if (otpDigitsRegex.matches(compact)) {
            val digits = compact.filter { it.isDigit() }
            if (digits.length in 4..8) return digits
        }
        val lower = compact.lowercase()
        if (otpKeywords.none { lower.contains(it) }) return null
        return otpInlineRegex.findAll(compact)
            .map { it.value }
            .firstOrNull { it.length in 4..8 }
    }

    /** Legacy emoji helper retained for older call sites. */
    fun getTypeEmoji(type: String): String = when (type) {
        ClipboardType.COLOR -> "🎨"
        ClipboardType.LINK, ClipboardType.URL -> "🔗"
        ClipboardType.SOCIAL -> "📱"
        ClipboardType.PHONE -> "📞"
        ClipboardType.OTP -> "🔐"
        ClipboardType.EMAIL -> "📧"
        ClipboardType.MATH, ClipboardType.MATHS -> "🧮"
        ClipboardType.PERSONAL -> "💜"
        ClipboardType.CODE -> "💻"
        ClipboardType.ADDRESS -> "📍"
        ClipboardType.CRYPTO -> "🪙"
        ClipboardType.CARD -> "💳"
        ClipboardType.SECRET -> "🔑"
        ClipboardType.IMAGE -> "🖼️"
        ClipboardType.FILE -> "📎"
        ClipboardType.MULTI -> "🗂️"
        else -> "📋"
    }

    /** Stable translation key suffix for a type (UI maps to strings). */
    fun typeKey(type: String): String = when (type.uppercase()) {
        ClipboardType.LINK, ClipboardType.URL -> "link"
        ClipboardType.SOCIAL -> "social"
        ClipboardType.PHONE -> "phone"
        ClipboardType.OTP -> "otp"
        ClipboardType.EMAIL -> "email"
        ClipboardType.MATH, ClipboardType.MATHS -> "math"
        ClipboardType.PERSONAL -> "personal"
        ClipboardType.CODE -> "code"
        ClipboardType.ADDRESS -> "address"
        ClipboardType.CRYPTO -> "crypto"
        ClipboardType.CARD -> "card"
        ClipboardType.SECRET -> "secret"
        ClipboardType.COLOR -> "color"
        ClipboardType.IMAGE -> "image"
        ClipboardType.FILE -> "file"
        ClipboardType.MULTI -> "multi"
        else -> "text"
    }

    // ── Detection internals ───────────────────────────────────────────────

    private fun detectType(trimmed: String, lower: String): String {
        // Security-sensitive shapes first.
        if (looksLikeOtp(trimmed, lower)) return ClipboardType.OTP
        if (looksLikeCard(trimmed)) return ClipboardType.CARD
        // A card-shaped number that fails Luhn is still never a phone number;
        // keep it as plain text (and mask it via the sensitivity pass).
        if (looksLikeCardShape(trimmed)) return ClipboardType.TEXT
        if (looksLikeSecret(trimmed)) return ClipboardType.SECRET

        if (colorRegex.matches(trimmed)) return ClipboardType.COLOR
        if (emailRegex.matches(trimmed)) return ClipboardType.EMAIL
        if (cryptoRegex.containsMatchIn(trimmed)) return ClipboardType.CRYPTO
        if (urlRegex.matches(trimmed) || urlAnywhereRegex.containsMatchIn(trimmed)) {
            return if (socialRegex.containsMatchIn(trimmed)) ClipboardType.SOCIAL else ClipboardType.LINK
        }
        if (phoneRegex.matches(trimmed) && trimmed.count { it.isDigit() } >= 7) {
            return ClipboardType.PHONE
        }
        if (looksLikeCode(trimmed, lower)) return ClipboardType.CODE
        if (looksLikeMath(trimmed)) return ClipboardType.MATH
        if (addressKeywords.count { lower.contains(it) } >= 2) return ClipboardType.ADDRESS
        if (personalKeywords.count { lower.contains(it) } >= 2) return ClipboardType.PERSONAL
        return ClipboardType.TEXT
    }

    private fun looksLikeOtp(trimmed: String, lower: String): Boolean {
        if (trimmed.length > 200) return false
        if (otpDigitsRegex.matches(trimmed)) {
            val digits = trimmed.filter { it.isDigit() }
            return digits.length in 4..8
        }
        if (otpKeywords.any { lower.contains(it) }) {
            return otpInlineRegex.containsMatchIn(trimmed)
        }
        return false
    }

    private fun looksLikeCard(trimmed: String): Boolean {
        if (!cardLikeRegex.matches(trimmed)) return false
        return luhnValid(trimmed)
    }

    /** 13–19 digit card shape, regardless of the Luhn checksum. */
    private fun looksLikeCardShape(trimmed: String): Boolean =
        cardLikeRegex.matches(trimmed) && trimmed.count { it.isDigit() } in 13..19

    private fun looksLikeNonSecretShape(trimmed: String): Boolean =
        urlAnywhereRegex.containsMatchIn(trimmed) ||
            emailRegex.matches(trimmed) ||
            colorRegex.matches(trimmed) ||
            cryptoRegex.containsMatchIn(trimmed) ||
            looksLikeCardShape(trimmed) ||
            (phoneRegex.matches(trimmed) && trimmed.count { it.isDigit() } in 7..15)

    private fun looksLikeSecret(trimmed: String): Boolean {
        if (trimmed.length > 2_000) return false
        if (secretLabelRegex.containsMatchIn(trimmed)) return true
        if (looksLikeNonSecretShape(trimmed)) return false
        // A bare high-entropy token (no spaces, mixed classes) is a secret.
        if (!trimmed.contains(' ') && !trimmed.contains('\n') &&
            trimmed.length in 10..128 &&
            highEntropyRegex.matches(trimmed) &&
            hasMixedClasses(trimmed)
        ) {
            return true
        }
        return false
    }

    private fun looksLikeCode(trimmed: String, lower: String): Boolean {
        if (jsonRegex.matches(trimmed) && (trimmed.contains(':') || trimmed.contains(','))) return true
        if (codeKeywords.count { lower.contains(it.lowercase()) } >= 2) return true
        if (trimmed.startsWith("git ") || trimmed.startsWith("npm ") ||
            trimmed.startsWith("docker ") || trimmed.startsWith("pip install")
        ) {
            return true
        }
        // Balanced braces/brackets with an assignment or call shape.
        val open = trimmed.count { it == '{' || it == '(' || it == '[' }
        val close = trimmed.count { it == '}' || it == ')' || it == ']' }
        return open >= 2 && open == close && (trimmed.contains('=') || trimmed.contains(';'))
    }

    private fun looksLikeMath(trimmed: String): Boolean {
        if (trimmed.length > 80) return false
        if (mathKeywordRegex.containsMatchIn(trimmed)) return true
        if (!mathRegex.matches(trimmed)) return false
        val hasOperator = trimmed.any { it in "+-*/=%^√" }
        return hasOperator && trimmed.any { it.isDigit() }
    }

    private fun sensitivityReason(
        trimmed: String,
        lower: String,
        isSensitiveExtra: Boolean,
        passwordVaultMatch: Boolean,
    ): String? {
        if (passwordVaultMatch) return ClipSensitivityReason.PASSWORD_VAULT
        if (looksLikeOtp(trimmed, lower)) return ClipSensitivityReason.OTP
        if (looksLikeCard(trimmed) || looksLikeCardShape(trimmed)) return ClipSensitivityReason.CARD
        if (secretLabelRegex.containsMatchIn(trimmed)) return ClipSensitivityReason.LABEL
        if (isSensitiveExtra) return ClipSensitivityReason.EXTRA
        if (looksLikeBareSecret(trimmed)) return ClipSensitivityReason.SECRET
        return null
    }

    private fun looksLikeBareSecret(trimmed: String): Boolean {
        if (trimmed.length > 200) return false
        if (trimmed.contains(' ') || trimmed.contains('\n')) return false
        if (trimmed.length !in 12..128) return false
        if (!highEntropyRegex.matches(trimmed)) return false
        if (looksLikeNonSecretShape(trimmed)) return false
        // Require at least two character classes and some digit/symbol entropy.
        return hasMixedClasses(trimmed) && trimmed.any { it.isDigit() }
    }

    private fun hasMixedClasses(value: String): Boolean {
        var classes = 0
        if (value.any { it.isLowerCase() }) classes++
        if (value.any { it.isUpperCase() }) classes++
        if (value.any { it.isDigit() }) classes++
        if (value.any { !it.isLetterOrDigit() }) classes++
        return classes >= 3
    }
}
