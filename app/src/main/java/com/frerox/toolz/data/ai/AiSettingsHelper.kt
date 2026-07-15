package com.frerox.toolz.data.ai

/**
 * Stateless helper for AI provider metadata and lightweight validation.
 */
object AiSettingsHelper {

    val providers = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

    private val openAiCompatibleProviders = setOf("ChatGPT", "Groq", "DeepSeek", "OpenRouter")

    fun getRecommendedModel(provider: String): String = when (provider) {
        "Gemini" -> "gemini-3.0-flash"
        "ChatGPT" -> "gpt-5.4-mini"
        "Groq" -> "llama-3.3-70b-versatile"
        "Claude" -> "claude-sonnet-4-6"
        "DeepSeek" -> "deepseek-chat"
        "OpenRouter" -> "anthropic/claude-sonnet-4-6"
        else -> "gemini-3.0-flash"
    }

    fun getModels(provider: String): List<String> = when (provider) {
        "Gemini" -> listOf(
            "gemini-3.0-pro",
            "gemini-3.0-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        )
        "ChatGPT" -> listOf(
            "gpt-5.5",
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-5.4-nano",
            "o4-mini",
            "o3",
            "gpt-4o",
            "gpt-4o-mini"
        )
        "Groq" -> listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "llama-3.2-90b-vision-preview",
            "llama-3.2-11b-vision-preview",
            "deepseek-r1-distill-llama-70b",
            "mixtral-8x7b-32768"
        )
        "Claude" -> listOf(
            "claude-opus-4-7",
            "claude-opus-4-6",
            "claude-sonnet-4-6",
            "claude-haiku-4-5"
        )
        "DeepSeek" -> listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
        "OpenRouter" -> listOf(
            "anthropic/claude-sonnet-4-6",
            "anthropic/claude-opus-4-7",
            "openai/gpt-5.5",
            "openai/gpt-5.4-mini",
            "google/gemini-3.0-flash",
            "google/gemini-3.0-pro",
            "deepseek/deepseek-chat",
            "meta-llama/llama-3.3-70b-instruct"
        )
        else -> emptyList()
    }

    fun supportsVision(provider: String, model: String): Boolean {
        if (model.contains("vision", ignoreCase = true) || model.contains("vl", ignoreCase = true)) {
            return true
        }
        return when (provider) {
            "Gemini" -> true
            "ChatGPT" -> model.contains("gpt-5", ignoreCase = true) || model.contains("gpt-4", ignoreCase = true) || model.contains("o", ignoreCase = true)
            "Claude" -> true // All Claude 4 models natively support vision
            "OpenRouter" -> {
                model.contains("gemini", ignoreCase = true) ||
                        model.contains("claude", ignoreCase = true) ||
                        model.contains("gpt-5", ignoreCase = true) ||
                        model.contains("gpt-4", ignoreCase = true) ||
                        model.contains("vision", ignoreCase = true)
            }
            "DeepSeek" -> false
            else -> false
        }
    }

    fun supportsFiles(provider: String, model: String): Boolean {
        return when (provider) {
            "Gemini" -> true
            "Claude" -> true
            "ChatGPT" -> model.contains("gpt-5", ignoreCase = true) || model.contains("gpt-4", ignoreCase = true) || model.contains("o", ignoreCase = true)
            "OpenRouter" -> model.contains("claude", ignoreCase = true) || model.contains("gemini", ignoreCase = true) || model.contains("gpt-5", ignoreCase = true) || model.contains("gpt-4", ignoreCase = true)
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

    fun isPlaceholder(key: String): Boolean {
        if (key.isBlank()) return true
        val k = key.uppercase()
        return k.contains("YOUR_") || k.contains("REPLACE_") ||
                k == "MISSING" || k == "DEFAULT" || k == "UNDEFINED" ||
                k == "NULL" || k == "API_KEY" || k.length < 10 ||
                k.contains("INSERT_") || k.contains("KEY_HERE")
    }

    fun isKnownModel(provider: String, model: String): Boolean {
        return getModels(provider).contains(model)
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
        "Gemini" to "Google's fast multimodal assistant. Class-leading context window lengths and strong speed.",
        "ChatGPT" to "OpenAI's latest GPT-5 and o-series models, providing industry-leading reasoning and generation.",
        "Groq" to "Extremely low-latency inference using LPUs. Feels virtually instantaneous in chat.",
        "Claude" to "Anthropic's 4-series models offering top-tier writing, coding, reasoning, and enormous context.",
        "DeepSeek" to "Powerful and extremely cost-efficient open-source models with top-tier math and coding.",
        "OpenRouter" to "A universal API hub that gives you a single place to access almost any model."
    )

    const val disclaimerText =
        "Higher-tier models are smarter but consume more tokens. Flash, Haiku, and Mini models are usually best for everyday mobile use."

    const val apiKeySuggestion =
        "Using your own API key gives you the best availability and the most predictable experience."
}