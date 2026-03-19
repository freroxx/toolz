package com.frerox.toolz.data.ai

/**
 * Stateless helper — pure functions and constants only.
 *
 * DEFAULT KEYS ARE NO LONGER STORED HERE.
 *
 * Previously the six API keys were hardcoded `private const val` fields,
 * which meant they were baked into the compiled APK and extractable by
 * anyone who ran apktool / jadx on the release build.
 *
 * They are now fetched at runtime from the secure Vercel endpoint
 * (toolz-apis.vercel.app/api/keys) and cached in EncryptedSharedPreferences
 * by [AiSettingsManager.syncRemoteKeys].
 */
object AiSettingsHelper {

    val providers = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

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
        "Gemini"     -> listOf("gemini-2.0-flash","gemini-2.0-flash-lite","gemini-2.0-pro-exp-02-05","gemini-1.5-flash","gemini-1.5-pro")
        "ChatGPT"    -> listOf("gpt-4o-mini","gpt-4o","o3-mini","o1-mini","o1","gpt-4-turbo")
        "Groq"       -> listOf("llama-3.3-70b-versatile","llama-3.1-8b-instant","deepseek-r1-distill-llama-70b","mixtral-8x7b-32768","qwen-2.5-coder-32b")
        "Claude"     -> listOf("claude-opus-4-5","claude-sonnet-4-5","claude-haiku-4-5","claude-3-5-sonnet-20241022","claude-3-5-haiku-20241022")
        "DeepSeek"   -> listOf("deepseek-chat","deepseek-reasoner","deepseek-vl2","deepseek-vl2-tiny")
        "OpenRouter" -> listOf("openrouter/auto","openrouter/free","google/gemini-2.0-flash-001","anthropic/claude-sonnet-4-5","openai/gpt-4o-mini","deepseek/deepseek-r1","meta-llama/llama-3.3-70b-instruct","mistralai/mistral-7b-instruct-v0.1")
        else         -> emptyList()
    }

    fun supportsVision(provider: String, model: String): Boolean {
        if (model.contains("vision", ignoreCase = true) || model.contains("vl", ignoreCase = true)) return true
        return when (provider) {
            "Gemini"     -> true
            "ChatGPT"    -> model.contains("gpt-4o") || model.contains("gpt-4-turbo")
            "Claude"     -> true
            "OpenRouter" -> model.contains("free") || model.contains("auto") || model.contains("gemini") || model.contains("claude") || model.contains("gpt-4")
            "DeepSeek"   -> model.contains("vl")
            else         -> false
        }
    }

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
     * Relaxed validation to prevent "Invalid Key" errors.
     * Only basic length check to ensure it's not a tiny fragment.
     */
    fun validateApiKey(provider: String, key: String): Boolean {
        if (key.isBlank()) return true

        // Very short keys are definitely invalid, but some experimental 
        // keys can be short. 8 characters is a safe minimum.
        if (key.length < 8) return false

        // Relaxed prefix checks — many providers change their formats.
        // We only check for common starting patterns but don't fail if they differ.
        return when (provider) {
            "Gemini"     -> key.startsWith("AIza") || key.length > 20
            "ChatGPT"    -> key.startsWith("sk-")
            "Groq"       -> key.startsWith("gsk_") || key.startsWith("sk-")
            "Claude"     -> key.startsWith("sk-ant-") || key.startsWith("sk-")
            "DeepSeek"   -> key.startsWith("sk-")
            "OpenRouter" -> key.startsWith("sk-or-") || key.startsWith("sk-")
            else         -> true
        }
    }

    /** No hardcoded defaults — returns empty string until synced from server. */
    fun getDefaultKey(provider: String): String = ""

    /** No hardcoded defaults exist anymore. Always returns false. */
    fun isDefaultKey(key: String): Boolean = false

    val tutorials: Map<String, List<String>> = mapOf(
        "Gemini"     to listOf("Go to Google AI Studio (aistudio.google.com)","Sign in with your Google Account","Click 'Get API key' in the left sidebar","Click 'Create API key in new project'","Copy the key and paste it here"),
        "ChatGPT"    to listOf("Go to OpenAI Platform (platform.openai.com)","Sign in or create an account","Navigate to 'API Keys' in the dashboard","Click '+ Create new secret key'","Copy your key immediately — it won't be shown again"),
        "Groq"       to listOf("Go to Groq Console (console.groq.com)","Sign in with your account","Click 'API Keys' in the sidebar","Click 'Create API Key' and name it 'Toolz'","Copy the generated key"),
        "Claude"     to listOf("Go to Anthropic Console (console.anthropic.com)","Sign in or create an account","Go to 'Settings' → 'API Keys'","Click 'Create Key' and name it 'Toolz'","Copy the key and paste it above"),
        "DeepSeek"   to listOf("Go to DeepSeek Platform (platform.deepseek.com)","Sign in or create an account","Navigate to 'API Keys' in the sidebar","Click 'Create API Key'","Copy the key and paste it here"),
        "OpenRouter" to listOf("Go to OpenRouter (openrouter.ai)","Sign in or create an account","Navigate to 'Keys' in your settings","Click 'Create Key'","Copy the key and paste it here"),
    )

    val detailedInfo: Map<String, String> = mapOf(
        "Gemini"     to "Google's latest AI. Gemini 2.0 Flash is optimized for speed and efficiency.",
        "ChatGPT"    to "OpenAI's models. GPT-4o Mini offers a great balance of intelligence and cost.",
        "Groq"       to "Ultra-fast inference via LPUs. Best for real-time conversations.",
        "Claude"     to "Anthropic's safety-focused models. Excellent at coding, reasoning, and long context.",
        "DeepSeek"   to "High-performance open models. Strong coding and reasoning at low cost.",
        "OpenRouter" to "Unified access to 100+ models. Use 'openrouter/free' for zero-cost access.",
    )

    const val disclaimerText    = "Higher-tier models are smarter but consume significantly more tokens. Flash/Mini models are recommended for everyday tasks."
    const val apiKeySuggestion  = "Using your own API key ensures 100% availability and faster response times."
}
