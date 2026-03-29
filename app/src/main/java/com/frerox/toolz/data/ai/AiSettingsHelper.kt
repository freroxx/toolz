package com.frerox.toolz.data.ai

/**
 * Stateless helper for AI provider metadata and lightweight validation.
 *
 * Default keys are not stored in the APK. They are fetched at runtime and
 * cached by [AiSettingsManager].
 */
object AiSettingsHelper {

    val providers = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

    private val openAiCompatibleProviders = setOf("ChatGPT", "Groq", "DeepSeek", "OpenRouter")

    fun getRecommendedModel(provider: String): String = when (provider) {
        "Gemini" -> "gemini-2.0-flash"
        "ChatGPT" -> "gpt-4o-mini"
        "Groq" -> "llama-3.3-70b-versatile"
        "Claude" -> "claude-sonnet-4-5"
        "DeepSeek" -> "deepseek-chat"
        "OpenRouter" -> "openrouter/auto"
        else -> "gemini-2.0-flash"
    }

    fun getModels(provider: String): List<String> = when (provider) {
        "Gemini" -> listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.0-pro-exp-02-05",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
        "ChatGPT" -> listOf("gpt-4o-mini", "gpt-4o", "o3-mini", "o1-mini", "o1", "gpt-4-turbo")
        "Groq" -> listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "deepseek-r1-distill-llama-70b",
            "mixtral-8x7b-32768",
            "qwen-2.5-coder-32b"
        )
        "Claude" -> listOf(
            "claude-opus-4-5",
            "claude-sonnet-4-5",
            "claude-haiku-4-5",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022"
        )
        "DeepSeek" -> listOf("deepseek-chat", "deepseek-reasoner", "deepseek-vl2", "deepseek-vl2-tiny")
        "OpenRouter" -> listOf(
            "openrouter/auto",
            "openrouter/free",
            "google/gemini-2.0-flash-001",
            "anthropic/claude-sonnet-4-5",
            "openai/gpt-4o-mini",
            "deepseek/deepseek-r1",
            "meta-llama/llama-3.3-70b-instruct",
            "mistralai/mistral-7b-instruct-v0.1"
        )
        else -> emptyList()
    }

    fun supportsVision(provider: String, model: String): Boolean {
        if (model.contains("vision", ignoreCase = true) || model.contains("vl", ignoreCase = true)) {
            return true
        }
        return when (provider) {
            "Gemini" -> true
            "ChatGPT" -> model.contains("gpt-4o", ignoreCase = true) || model.contains("gpt-4-turbo", ignoreCase = true)
            "Claude" -> true
            "OpenRouter" -> {
                model.contains("free", ignoreCase = true) ||
                    model.contains("auto", ignoreCase = true) ||
                    model.contains("gemini", ignoreCase = true) ||
                    model.contains("claude", ignoreCase = true) ||
                    model.contains("gpt-4", ignoreCase = true)
            }
            "DeepSeek" -> model.contains("vl", ignoreCase = true)
            else -> false
        }
    }

    fun supportsFiles(provider: String, model: String): Boolean {
        return when (provider) {
            "Gemini" -> true
            "Claude" -> true
            "ChatGPT" -> model.contains("gpt-4", ignoreCase = true) || model.contains("o1", ignoreCase = true)
            "OpenRouter" -> model.contains("claude", ignoreCase = true) || model.contains("gemini", ignoreCase = true)
            else -> false
        }
    }

    fun isOpenAiCompatible(provider: String): Boolean = provider in openAiCompatibleProviders

    fun getChatCompletionUrl(provider: String): String? = when (provider) {
        "ChatGPT" -> "https://api.openai.com/v1/chat/completions"
        "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
        "DeepSeek" -> "https://api.deepseek.com/v1/chat/completions"
        "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
        else -> null
    }

    fun getProviderDescription(provider: String): String =
        detailedInfo[provider] ?: "General-purpose AI provider."

    fun getApiKeyPlaceholder(provider: String): String = when (provider) {
        "Gemini" -> "AIza..."
        "ChatGPT" -> "sk-..."
        "Groq" -> "gsk_..."
        "Claude" -> "sk-ant-..."
        "DeepSeek" -> "sk-..."
        "OpenRouter" -> "sk-or-..."
        else -> ""
    }

    fun getApiKeyUrl(provider: String): String = when (provider) {
        "Gemini" -> "https://aistudio.google.com/app/apikey"
        "ChatGPT" -> "https://platform.openai.com/api-keys"
        "Groq" -> "https://console.groq.com/keys"
        "Claude" -> "https://console.anthropic.com/settings/keys"
        "DeepSeek" -> "https://platform.deepseek.com/api_keys"
        "OpenRouter" -> "https://openrouter.ai/keys"
        else -> ""
    }

    fun normalizeApiKeyInput(raw: String): String =
        raw.trim().removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'")

    /**
     * Relaxed validation to prevent false negatives while still catching
     * obviously incomplete keys.
     */
    fun validateApiKey(provider: String, key: String): Boolean {
        val normalized = normalizeApiKeyInput(key)
        if (normalized.isBlank()) return true
        if (normalized.length < 8) return false

        return when (provider) {
            "Gemini" -> normalized.startsWith("AIza") || normalized.length > 20
            "ChatGPT" -> normalized.startsWith("sk-")
            "Groq" -> normalized.startsWith("gsk_") || normalized.startsWith("sk-")
            "Claude" -> normalized.startsWith("sk-ant-") || normalized.startsWith("sk-")
            "DeepSeek" -> normalized.startsWith("sk-")
            "OpenRouter" -> normalized.startsWith("sk-or-") || normalized.startsWith("sk-")
            else -> true
        }
    }

    fun getDefaultKey(provider: String): String = ""

    fun isDefaultKey(key: String): Boolean = false

    val tutorials: Map<String, List<String>> = mapOf(
        "Gemini" to listOf(
            "Go to Google AI Studio (aistudio.google.com)",
            "Sign in with your Google Account",
            "Click 'Get API key' in the left sidebar",
            "Click 'Create API key in new project'",
            "Copy the key and paste it here"
        ),
        "ChatGPT" to listOf(
            "Go to OpenAI Platform (platform.openai.com)",
            "Sign in or create an account",
            "Navigate to 'API Keys' in the dashboard",
            "Click '+ Create new secret key'",
            "Copy your key immediately because it will not be shown again"
        ),
        "Groq" to listOf(
            "Go to Groq Console (console.groq.com)",
            "Sign in with your account",
            "Click 'API Keys' in the sidebar",
            "Click 'Create API Key' and name it 'Toolz'",
            "Copy the generated key"
        ),
        "Claude" to listOf(
            "Go to Anthropic Console (console.anthropic.com)",
            "Sign in or create an account",
            "Go to 'Settings' -> 'API Keys'",
            "Click 'Create Key' and name it 'Toolz'",
            "Copy the key and paste it above"
        ),
        "DeepSeek" to listOf(
            "Go to DeepSeek Platform (platform.deepseek.com)",
            "Sign in or create an account",
            "Navigate to 'API Keys' in the sidebar",
            "Click 'Create API Key'",
            "Copy the key and paste it here"
        ),
        "OpenRouter" to listOf(
            "Go to OpenRouter (openrouter.ai)",
            "Sign in or create an account",
            "Navigate to 'Keys' in your settings",
            "Click 'Create Key'",
            "Copy the key and paste it here"
        )
    )

    val detailedInfo: Map<String, String> = mapOf(
        "Gemini" to "Google's fast multimodal assistant. Great for images and quick answers.",
        "ChatGPT" to "OpenAI models with strong balance across writing, coding, and reasoning.",
        "Groq" to "Very low-latency inference that feels especially fast in chat.",
        "Claude" to "Anthropic models with strong writing quality, reasoning, and long context.",
        "DeepSeek" to "Open models that are especially good for coding and cost-efficient reasoning.",
        "OpenRouter" to "A model hub that gives you one place to try several providers."
    )

    const val disclaimerText =
        "Higher-tier models are smarter but consume more tokens. Flash and mini models are best for everyday use."

    const val apiKeySuggestion =
        "Using your own API key gives you the best availability and the most predictable experience."
}
