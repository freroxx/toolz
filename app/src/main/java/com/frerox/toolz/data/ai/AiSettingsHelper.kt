package com.frerox.toolz.data.ai

/**
 * Stateless helper — pure functions and constants only.
 */
object AiSettingsHelper {

    // ── Default (shared-quota) API keys ───────────────────────────────────
    private const val GEMINI_DEFAULT     = "AIzaSyD8CT-W3XyZ_L5l9C5VwvLyeShf_GSdi0w"
    private const val CHATGPT_DEFAULT    = "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"
    private const val GROQ_DEFAULT       = "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"
    private const val CLAUDE_DEFAULT     = "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"
    private const val DEEPSEEK_DEFAULT   = "sk-0511f53f860649cc935d4a5225ba05b4"
    private const val OPENROUTER_DEFAULT = "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"

    // ── Provider lists ─────────────────────────────────────────────────────

    val providers = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

    // ── Model catalogue ────────────────────────────────────────────────────

    fun getRecommendedModel(provider: String): String = when (provider) {
        "Gemini"     -> "gemini-2.0-flash"
        "ChatGPT"    -> "gpt-4o-mini"
        "Groq"       -> "llama-3.3-70b-versatile"
        "Claude"     -> "claude-sonnet-4-5"
        "DeepSeek"   -> "deepseek-chat"
        "OpenRouter" -> "openrouter/auto"
        else         -> "gemini-2.0-flash"
    }

    fun getModels(provider: String): List<String> = when (provider) {
        "Gemini"  -> listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.0-pro-exp-02-05",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
        )
        "ChatGPT" -> listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "o3-mini",
            "o1-mini",
            "o1",
            "gpt-4-turbo",
        )
        "Groq"    -> listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "deepseek-r1-distill-llama-70b",
            "mixtral-8x7b-32768",
            "qwen-2.5-coder-32b",
        )
        "Claude"  -> listOf(
            "claude-opus-4-5",
            "claude-sonnet-4-5",
            "claude-haiku-4-5",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
        )
        "DeepSeek" -> listOf(
            "deepseek-chat",
            "deepseek-reasoner",
            "deepseek-vl2",
            "deepseek-vl2-tiny",
        )
        "OpenRouter" -> listOf(
            "openrouter/auto",
            "openrouter/free",
            "google/gemini-2.0-flash-001",
            "anthropic/claude-sonnet-4-5",
            "openai/gpt-4o-mini",
            "deepseek/deepseek-r1",
            "meta-llama/llama-3.3-70b-instruct",
            "mistralai/mistral-7b-instruct-v0.1",
        )
        else -> emptyList()
    }

    // ── Vision capability ─────────────────────────────────────────────────

    fun supportsVision(provider: String, model: String): Boolean {
        if (model.contains("vision", ignoreCase = true) || model.contains("vl", ignoreCase = true)) return true
        return when (provider) {
            "Gemini"     -> true
            "ChatGPT"    -> model.contains("gpt-4o") || model.contains("gpt-4-turbo")
            "Claude"     -> true   // all Claude 3+ models support vision
            "OpenRouter" -> model.contains("free")    || model.contains("auto") ||
                    model.contains("gemini")  || model.contains("claude") ||
                    model.contains("gpt-4")
            "DeepSeek"   -> model.contains("vl")
            else         -> false
        }
    }

    // ── API key helpers ───────────────────────────────────────────────────

    fun getApiKeyPlaceholder(provider: String): String = when (provider) {
        "Gemini"     -> "AIza…"
        "ChatGPT"    -> "sk-…"
        "Groq"       -> "gsk_…"
        "Claude"     -> "sk-ant-…"
        "DeepSeek"   -> "sk-…"
        "OpenRouter" -> "sk-or-…"
        else         -> ""
    }

    fun getApiKeyUrl(provider: String): String = when (provider) {
        "Gemini"     -> "https://aistudio.google.com/app/apikey"
        "ChatGPT"    -> "https://platform.openai.com/api-keys"
        "Groq"       -> "https://console.groq.com/keys"
        "Claude"     -> "https://console.anthropic.com/settings/keys"
        "DeepSeek"   -> "https://platform.deepseek.com/api_keys"
        "OpenRouter" -> "https://openrouter.ai/keys"
        else         -> ""
    }

    /**
     * Validates the format of a user-supplied key.
     * An empty string or a known default key is treated as valid (uses shared quota).
     */
    fun validateApiKey(provider: String, key: String): Boolean {
        if (key.isBlank()) return true
        if (isDefaultKey(key)) return true
        val regex = when (provider) {
            "Gemini"     -> Regex("^AIza[0-9A-Za-z\\-_]{35}$")
            "ChatGPT"    -> Regex("^sk-[0-9A-Za-z\\-_]{32,}$")
            "Groq"       -> Regex("^gsk_[0-9A-Za-z]{48,}$")
            "Claude"     -> Regex("^sk-ant-api[0-9]{2}-[0-9A-Za-z\\-_]{80,}$")
            "DeepSeek"   -> Regex("^sk-[0-9a-f]{32}$")
            "OpenRouter" -> Regex("^sk-or-v1-[0-9a-f]{64}$")
            else         -> Regex(".*")
        }
        return regex.matches(key)
    }

    fun isDefaultKey(key: String): Boolean = key in setOf(
        GEMINI_DEFAULT, CHATGPT_DEFAULT, GROQ_DEFAULT,
        CLAUDE_DEFAULT, DEEPSEEK_DEFAULT, OPENROUTER_DEFAULT,
    )

    /**
     * Returns the default (shared-quota) key for a provider.
     * Returns an empty string if the build was made without default keys configured.
     */
    fun getDefaultKey(provider: String): String = when (provider) {
        "Gemini"     -> GEMINI_DEFAULT
        "ChatGPT"    -> CHATGPT_DEFAULT
        "Groq"       -> GROQ_DEFAULT
        "Claude"     -> CLAUDE_DEFAULT
        "DeepSeek"   -> DEEPSEEK_DEFAULT
        "OpenRouter" -> OPENROUTER_DEFAULT
        else         -> ""
    }

    // ── Tutorial content ──────────────────────────────────────────────────

    val tutorials: Map<String, List<String>> = mapOf(
        "Gemini" to listOf(
            "Go to Google AI Studio (aistudio.google.com)",
            "Sign in with your Google Account",
            "Click 'Get API key' in the left sidebar",
            "Click 'Create API key in new project'",
            "Copy the key and paste it here",
        ),
        "ChatGPT" to listOf(
            "Go to OpenAI Platform (platform.openai.com)",
            "Sign in or create an account",
            "Navigate to 'API Keys' in the dashboard",
            "Click '+ Create new secret key'",
            "Copy your key immediately — it won't be shown again",
        ),
        "Groq" to listOf(
            "Go to Groq Console (console.groq.com)",
            "Sign in with your account",
            "Click 'API Keys' in the sidebar",
            "Click 'Create API Key' and name it 'Toolz'",
            "Copy the generated key",
        ),
        "Claude" to listOf(
            "Go to Anthropic Console (console.anthropic.com)",
            "Sign in or create an account",
            "Go to 'Settings' → 'API Keys'",
            "Click 'Create Key' and name it 'Toolz'",
            "Copy the key and paste it above",
        ),
        "DeepSeek" to listOf(
            "Go to DeepSeek Platform (platform.deepseek.com)",
            "Sign in or create an account",
            "Navigate to 'API Keys' in the sidebar",
            "Click 'Create API Key'",
            "Copy the key and paste it here",
        ),
        "OpenRouter" to listOf(
            "Go to OpenRouter (openrouter.ai)",
            "Sign in or create an account",
            "Navigate to 'Keys' in your settings",
            "Click 'Create Key'",
            "Copy the key and paste it here",
        ),
    )

    val detailedInfo: Map<String, String> = mapOf(
        "Gemini"     to "Google's latest AI. Gemini 2.0 Flash is optimized for speed and efficiency.",
        "ChatGPT"    to "OpenAI's models. GPT-4o Mini offers a great balance of intelligence and cost.",
        "Groq"       to "Ultra-fast inference via LPUs. Best for real-time conversations.",
        "Claude"     to "Anthropic's safety-focused models. Excellent at coding, reasoning, and long context.",
        "DeepSeek"   to "High-performance open models. Strong coding and reasoning at low cost.",
        "OpenRouter" to "Unified access to 100+ models. Use 'openrouter/free' for zero-cost access.",
    )

    const val disclaimerText =
        "Higher-tier models are smarter but consume significantly more tokens. " +
                "Flash/Mini models are recommended for everyday tasks."

    const val apiKeySuggestion =
        "Using your own API key ensures 100% availability and faster response times."
}