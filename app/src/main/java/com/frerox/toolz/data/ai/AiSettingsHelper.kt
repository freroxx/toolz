package com.frerox.toolz.data.ai

import com.frerox.toolz.BuildConfig

/**
 * Stateless helper for AI provider metadata and lightweight validation.
 */
object AiSettingsHelper {

    val providers = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

    private val openAiCompatibleProviders = setOf("ChatGPT", "Groq", "DeepSeek", "OpenRouter")

    fun getRecommendedModel(provider: String): String = when (provider) {
        "Gemini" -> "gemini-2.0-flash"
        "ChatGPT" -> "gpt-4o-mini"
        "Groq" -> "llama-3.3-70b-versatile"
        "Claude" -> "claude-3-5-sonnet-20241022"
        "DeepSeek" -> "deepseek-chat"
        "OpenRouter" -> "google/gemini-2.0-flash-001"
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
        "ChatGPT" -> listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "o3-mini",
            "o1-mini",
            "o1",
            "gpt-4-turbo"
        )
        "Groq" -> listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "deepseek-r1-distill-llama-70b",
            "mixtral-8x7b-32768"
        )
        "Claude" -> listOf(
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229"
        )
        "DeepSeek" -> listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
        "OpenRouter" -> listOf(
            "google/gemini-2.0-flash-001",
            "anthropic/claude-3.5-sonnet",
            "openai/gpt-4o-mini",
            "deepseek/deepseek-chat",
            "meta-llama/llama-3.3-70b-instruct",
            "mistralai/mistral-7b-instruct",
            "google/gemini-2.0-pro-exp-02-05:free"
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
            "Claude" -> model.contains("sonnet", ignoreCase = true) || model.contains("opus", ignoreCase = true)
            "OpenRouter" -> {
                model.contains("gemini", ignoreCase = true) ||
                    model.contains("claude", ignoreCase = true) ||
                    model.contains("gpt-4", ignoreCase = true)
            }
            "DeepSeek" -> false
            else -> false
        }
    }

    fun supportsFiles(provider: String, model: String): Boolean {
        return when (provider) {
            "Gemini" -> true
            "Claude" -> true
            "ChatGPT" -> model.contains("gpt-4", ignoreCase = true) || model.contains("o1", ignoreCase = true) || model.contains("o3", ignoreCase = true)
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

    fun validateApiKey(provider: String, key: String): Boolean {
        val normalized = normalizeApiKeyInput(key)
        if (normalized.isBlank()) return true
        if (normalized.length < 8) return false

        return when (provider) {
            "Gemini" -> normalized.startsWith("AIza")
            "ChatGPT" -> normalized.startsWith("sk-")
            "Groq" -> normalized.startsWith("gsk_")
            "Claude" -> normalized.startsWith("sk-ant-")
            "DeepSeek" -> normalized.startsWith("sk-")
            "OpenRouter" -> normalized.startsWith("sk-or-")
            else -> true
        }
    }

    fun getDefaultKey(provider: String): String {
        val key = when (provider) {
            "Gemini" -> BuildConfig.GEMINI_DEFAULT
            "ChatGPT" -> BuildConfig.CHATGPT_DEFAULT
            "Groq" -> BuildConfig.GROQ_DEFAULT
            "OpenRouter" -> BuildConfig.OPENROUTER_DEFAULT
            "Claude" -> BuildConfig.CLAUDE_DEFAULT
            "DeepSeek" -> BuildConfig.DEEPSEEK_DEFAULT
            else -> ""
        }
        return if (isPlaceholder(key)) "" else key
    }

    fun isPlaceholder(key: String): Boolean {
        if (key.isBlank()) return true
        val k = key.uppercase()
        return k.contains("YOUR_") || k.contains("REPLACE_") || 
               k == "MISSING" || k == "DEFAULT" || k == "UNDEFINED" || 
               k == "NULL" || k == "API_KEY" || k.length < 10 ||
               k.contains("INSERT_") || k.contains("KEY_HERE")
    }

    fun isDefaultKey(key: String): Boolean {
        if (key.isBlank() || isPlaceholder(key)) return false
        return key == BuildConfig.GEMINI_DEFAULT ||
               key == BuildConfig.CHATGPT_DEFAULT ||
               key == BuildConfig.GROQ_DEFAULT ||
               key == BuildConfig.OPENROUTER_DEFAULT ||
               key == BuildConfig.CLAUDE_DEFAULT ||
               key == BuildConfig.DEEPSEEK_DEFAULT
    }

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
