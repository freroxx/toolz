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

package com.frerox.toolz.data.ai

/**
 * Provider metadata + lightweight validation for the AI stack.
 *
 * Server-driven: [installCatalog] (called by [AiCatalogRepository] at app
 * start and on refresh) swaps every table below for the live payload from
 * `GET /api/models`. Until a catalog is installed — offline, first launch,
 * fetch failure — the private `bundled*` tables apply, which are the exact
 * values this file shipped with before the server feature existed.
 *
 * Public signatures are unchanged, so all existing callers work untouched.
 * [canonicalProvider] stays local: it defines stable provider identity and
 * must never depend on the network.
 */
object AiSettingsHelper {

    // ── Remote catalog state ─────────────────────────────────────────────

    @Volatile
    private var remote: AiCatalogResponse? = null

    /**
     * Install a server catalog. Ignores empty payloads. Only the newest
     * version wins — the repository enforces the version guard, this is a
     * second line of defense for direct callers/tests.
     */
    fun installCatalog(catalog: AiCatalogResponse) {
        if (catalog.providers.isEmpty()) return
        val current = remote
        if (current != null && catalog.version < current.version) return
        remote = catalog
    }

    /** True once a server catalog is active (Settings shows "updated …"). */
    fun isUsingRemoteCatalog(): Boolean = remote != null

    fun remoteCatalogVersion(): Int? = remote?.version

    fun remoteCatalogUpdatedAt(): String? = remote?.updatedAt?.takeIf { it.isNotBlank() }

    private fun normalizeKey(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    /** Find the remote entry by display name ("OpenCode Zen") or id ("opencode-zen"). */
    private fun remoteProvider(provider: String): AiCatalogProvider? {
        val catalog = remote ?: return null
        val want = normalizeKey(canonicalProvider(provider))
        return catalog.providers.firstOrNull {
            normalizeKey(it.displayName) == want || normalizeKey(it.id) == want
        }
    }

    val providers: List<String>
        get() = remote?.providers
            ?.map { it.displayName }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: bundledProviders

    private val bundledProviders = listOf(
        "Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter",
        "OpenCode Zen", "OpenCode Go"
    )

    /** Short aliases accepted anywhere a provider string is compared. */
    fun canonicalProvider(provider: String): String = when (provider.trim().lowercase()) {
        "zen", "opencode zen", "opencode-zen", "opencode/zen" -> "OpenCode Zen"
        "go", "opencode go", "opencode-go", "opencode/go" -> "OpenCode Go"
        "gemini", "google" -> "Gemini"
        "chatgpt", "openai" -> "ChatGPT"
        "groq" -> "Groq"
        "claude", "anthropic" -> "Claude"
        "deepseek" -> "DeepSeek"
        "openrouter" -> "OpenRouter"
        else -> provider
    }

    private val openAiCompatibleProviders = setOf(
        "ChatGPT", "Groq", "DeepSeek", "OpenRouter", "OpenCode Zen", "OpenCode Go"
    )

    /** Free-first recommended defaults (verified Sep 2026). */
    fun getRecommendedModel(provider: String): String {
        remoteProvider(provider)?.recommendedModel?.takeIf { it.isNotBlank() }?.let { return it }
        return bundledRecommendedModel(provider)
    }

    private fun bundledRecommendedModel(provider: String): String = when (canonicalProvider(provider)) {
        "Gemini" -> "gemini-2.5-flash-lite"
        "ChatGPT" -> "gpt-5-mini"
        "Groq" -> "openai/gpt-oss-20b"
        "Claude" -> "claude-haiku-4-5"
        "DeepSeek" -> "deepseek-chat"
        "OpenRouter" -> "nvidia/nemotron-3-nano-30b-a3b:free"
        "OpenCode Zen" -> "muse-spark-1.3-contributor-free"
        "OpenCode Go" -> "glm-5.3-flash"
        else -> "gemini-2.5-flash-lite"
    }

    /** Cheap/fast fallback used when the selected model 404s or rate-limits. */
    fun getFallbackModels(provider: String): List<String> {
        remoteProvider(provider)?.fallbackModels?.takeIf { it.isNotEmpty() }?.let { return it }
        return bundledFallbackModels(provider)
    }

    private fun bundledFallbackModels(provider: String): List<String> = when (canonicalProvider(provider)) {
        "Groq" -> listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "llama-3.3-70b-versatile")
        "Gemini" -> listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-3-flash")
        "ChatGPT" -> listOf("gpt-5-mini", "gpt-5-nano", "gpt-4o-mini")
        "Claude" -> listOf("claude-haiku-4-5", "claude-sonnet-4-5")
        "DeepSeek" -> listOf("deepseek-chat", "deepseek-reasoner")
        "OpenRouter" -> listOf(
            "nvidia/nemotron-3-nano-30b-a3b:free",
            "google/gemma-3-27b-it:free",
            "deepseek/deepseek-chat:free"
        )
        "OpenCode Zen" -> listOf(
            "muse-spark-1.3-contributor-free",
            "mimo-v2.5-free",
            "big-pickle",
            "deepseek-v4-flash"
        )
        "OpenCode Go" -> listOf("glm-5.3-flash", "deepseek-v4-flash", "mimo-v2.5")
        else -> emptyList()
    }

    fun getModels(provider: String): List<String> {
        remoteProvider(provider)?.models?.takeIf { it.isNotEmpty() }?.let { return it }
        return bundledModels(provider)
    }

    private fun bundledModels(provider: String): List<String> = when (canonicalProvider(provider)) {
        "Gemini" -> listOf(
            // Free-tier friendly first, then capable tiers (verified 2026 lineup)
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-3-flash",
            "gemini-3.1-pro",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash",
            "gemini-3.1-flash-lite",
            "gemini-3.0-pro",
            "gemini-3.0-flash"
        )
        "ChatGPT" -> listOf(
            "gpt-5-mini",
            "gpt-5-nano",
            "gpt-5",
            "gpt-5.6-luna",
            "gpt-5.6-sol",
            "gpt-5.6-terra",
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
            // Production models only (console.groq.com/docs/models, Sep 2026).
            // llama-3.1-8b-instant DISCONTINUED — replaced by openai/gpt-oss-20b.
            // Deprecated llama-4-*, mixtral-8x7b-32768, r1-distill etc. removed — they 404.
            "openai/gpt-oss-20b",
            "openai/gpt-oss-120b",
            "llama-3.3-70b-versatile",
            "groq/compound",
            "groq/compound-mini",
            "allam-2-7b",
            // Preview / eval models (may be gated)
            "qwen/qwen3.6-27b",
            "qwen/qwen3.8-27b",
            "minimaxai/minimax-m2.7",
            "openai/gpt-oss-safeguard-20b"
        )
        "Claude" -> listOf(
            "claude-haiku-4-5",
            "claude-sonnet-4-5",
            "claude-sonnet-4-6",
            "claude-sonnet-5",
            "claude-opus-4-5",
            "claude-opus-4-6",
            "claude-opus-4-7",
            "claude-opus-4-8",
            "claude-opus-5",
            "claude-fable-5",
            "claude-fable-5-1"
        )
        "DeepSeek" -> listOf(
            "deepseek-chat",
            "deepseek-reasoner",
            "deepseek-v4-flash",
            "deepseek-v4-pro",
            "deepseek-v4.1-flash"
        )
        "OpenRouter" -> listOf(
            // Free (:free) models first — $0 on OpenRouter (Sep 2026)
            "nvidia/nemotron-3-nano-30b-a3b:free",
            "nvidia/nemotron-3-super-120b-a12b:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "nvidia/nemotron-3.5-lightning:free",
            "google/gemma-3-27b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            "deepseek/deepseek-chat:free",
            "dots-studio/dots-3-note-preview:free",
            "liquid/lfm-2.5-2.6b:free",
            // Paid / pay-as-you-go fallbacks
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "anthropic/claude-sonnet-5",
            "anthropic/claude-fable-5",
            "anthropic/claude-opus-4-8",
            "openai/gpt-5.6-sol",
            "openai/gpt-5.6-terra",
            "openai/gpt-5.6-luna",
            "openai/gpt-5.4-mini",
            "google/gemini-3.5-flash",
            "google/gemini-3.1-pro",
            "deepseek/deepseek-v4-pro",
            "deepseek/deepseek-v4-flash",
            "meta-llama/llama-3.3-70b-instruct",
            "meta-llama/llama-3.1-8b-instruct"
        )
        "OpenCode Zen" -> listOf(
            // FREE (pay-as-you-go $0) — https://opencode.ai/docs/zen, live /v1/models Sep 2026
            "muse-spark-1.3-contributor-free",
            "muse-spark-1.2-contributor-free",
            "mimo-v2.5-free",
            "big-pickle",
            "ling-3.0-flash-fin-free",
            "nemotron-3-ultra-free",
            "nemotron-3.5-lightning-free",
            "deepseek-v4-flash-free",
            // Cheap + strong open coding models (chat/completions compatible)
            "deepseek-v4-flash",
            "deepseek-v4-pro",
            "glm-5.3-flash",
            "glm-5.3",
            "glm-5.2",
            "glm-5.1",
            "kimi-k2.6",
            "kimi-k2.7-code",
            "kimi-k3",
            "qwen3.6-plus",
            "qwen3.7-plus",
            "minimax-m3",
            "minimax-m2.7",
            "gpt-5.6-luna",
            "gemini-3.5-flash",
            "gemini-3-flash",
            "claude-sonnet-5",
            "claude-haiku-4-5",
            "grok-4.5",
            "muse-spark-1.3"
        )
        "OpenCode Go" -> listOf(
            // $10/mo subscription — https://opencode.ai/docs/go (chat/completions compatible subset)
            "glm-5.3-flash",
            "glm-5.3",
            "glm-5.2",
            "glm-5.1",
            "kimi-k3",
            "kimi-k2.7-code",
            "kimi-k2.6",
            "longcat-2.0",
            "deepseek-v4.1-flash",
            "deepseek-v4-pro",
            "deepseek-v4-flash",
            "deepseek-v4-flash-vision-exp",
            "mimo-v2.5",
            "mimo-v2.5-pro",
            "minimax-m3",
            "minimax-m2.7",
            "muse-spark-1.3-contributor",
            "muse-spark-1.2-contributor",
            "qwen3.8-max",
            "qwen3.8-flash",
            "qwen3.7-max",
            "qwen3.7-plus",
            "qwen3.6-plus",
            "hy3",
            "hy4-preview",
            "grok-4.6",
            "gpt-5.6-luna"
        )
        else -> emptyList()
    }

    /** Models that cost $0 / are on a generous free tier. Used for badges. */
    fun isFreeModel(provider: String, model: String): Boolean {
        val m = model.lowercase()
        // Universal rules — safe for models the catalog hasn't seen yet.
        if (m.endsWith(":free") || m.endsWith("-free")) return true
        if (m in setOf("big-pickle", "allam-2-7b")) return true
        val remoteEntry = remoteProvider(provider)
        if (remoteEntry != null) {
            return remoteEntry.freeModels.any { it.equals(model, ignoreCase = true) }
        }
        return bundledIsFreeModel(provider, model)
    }

    private fun bundledIsFreeModel(provider: String, model: String): Boolean {
        val m = model.lowercase()
        if (canonicalProvider(provider) == "OpenCode Zen") {
            return m in setOf(
                "muse-spark-1.3-contributor-free", "muse-spark-1.2-contributor-free",
                "mimo-v2.5-free", "big-pickle", "ling-3.0-flash-fin-free",
                "nemotron-3-ultra-free", "nemotron-3.5-lightning-free", "deepseek-v4-flash-free"
            )
        }
        if (canonicalProvider(provider) == "Groq") {
            // Groq has a free dev tier for all production models (rate-limited)
            return true
        }
        if (canonicalProvider(provider) == "Gemini") {
            return m.contains("flash-lite") || m == "gemini-2.5-flash-lite" || m == "gemini-2.5-flash"
        }
        return false
    }

    /** Retired model IDs that now 404 — auto-migrated to a live equivalent. */
    fun migrateRetiredModel(provider: String, model: String): String? {
        remoteProvider(provider)?.retiredModels?.let { retired ->
            retired.entries.firstOrNull { it.key.equals(model, ignoreCase = true) }?.let { return it.value }
            // Provider known remotely but model not listed as retired → no migration.
            // (Do NOT fall through to bundled: the server is authoritative.)
            return null
        }
        return bundledMigrateRetiredModel(provider, model)
    }

    private fun bundledMigrateRetiredModel(provider: String, model: String): String? = when (canonicalProvider(provider)) {
        "Groq" -> when (model) {
            "llama-3.1-8b-instant",
            "llama-3.1-8b-versatile",
            "llama3-8b-8192",
            "meta-llama/llama-4-behemoth-288b",
            "meta-llama/llama-4-maverick-17b",
            "meta-llama/llama-4-scout-17b",
            "meta-llama/llama-4-maverick-17b-128e-instruct",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-3.3-70b-instruct",
            "meta-llama/llama-3.1-8b-instruct" -> "openai/gpt-oss-20b"
            "deepseek/deepseek-r1-distill-70b",
            "deepseek-r1-distill-llama-70b" -> "openai/gpt-oss-20b"
            "qwen/qwen3-vl-32b-instruct",
            "qwen/qwen3-32b" -> "qwen/qwen3.6-27b"
            "mistral/mistral-saba-24b",
            "mixtral-8x7b-32768" -> "llama-3.3-70b-versatile"
            "moonshotai/kimi-k2-instruct" -> "openai/gpt-oss-120b"
            else -> null
        }
        else -> null
    }

    fun supportsVision(provider: String, model: String): Boolean {
        val p = canonicalProvider(provider)
        if (model.contains("vision", ignoreCase = true) || model.contains("vl", ignoreCase = true)) {
            return true
        }
        remoteProvider(provider)?.let { entry ->
            // Empty string in the list = provider-wide support (matches all).
            return entry.visionSubstrings.any { sub -> model.contains(sub, ignoreCase = true) }
        }
        return bundledSupportsVision(p, model)
    }

    private fun bundledSupportsVision(p: String, model: String): Boolean {
        return when (p) {
            "Gemini" -> true
            "ChatGPT" -> model.contains("gpt-5", ignoreCase = true) || model.contains("gpt-4", ignoreCase = true) || model.contains("o", ignoreCase = true)
            "Claude" -> true // All Claude 4/5 models natively support vision
            "OpenRouter" -> {
                model.contains("gemini", ignoreCase = true) ||
                        model.contains("claude", ignoreCase = true) ||
                        model.contains("gpt-5", ignoreCase = true) ||
                        model.contains("gpt-4", ignoreCase = true) ||
                        model.contains("vision", ignoreCase = true) ||
                        model.contains("qwen", ignoreCase = true) ||
                        model.contains("gemma", ignoreCase = true) ||
                        model.contains("nemotron", ignoreCase = true)
            }
            "OpenCode Zen" -> {
                model.contains("vision", ignoreCase = true) ||
                        model.contains("claude", ignoreCase = true) ||
                        model.contains("gemini", ignoreCase = true) ||
                        model.contains("gpt", ignoreCase = true) ||
                        model.contains("grok", ignoreCase = true) ||
                        model.contains("kimi", ignoreCase = true) ||
                        model.contains("qwen", ignoreCase = true) ||
                        model.contains("glm", ignoreCase = true) ||
                        model.contains("minimax", ignoreCase = true) ||
                        model.contains("muse-spark", ignoreCase = true) ||
                        model.contains("deepseek-v4-flash", ignoreCase = true)
            }
            "OpenCode Go" -> {
                model.contains("vision", ignoreCase = true) ||
                        model.contains("glm", ignoreCase = true) ||
                        model.contains("kimi", ignoreCase = true) ||
                        model.contains("qwen", ignoreCase = true) ||
                        model.contains("deepseek-v4-flash", ignoreCase = true) ||
                        model.contains("mimo", ignoreCase = true) ||
                        model.contains("muse-spark", ignoreCase = true)
            }
            "Groq" -> model.contains("gpt-oss", ignoreCase = true) ||
                    model.contains("qwen", ignoreCase = true) ||
                    model.contains("compound", ignoreCase = true)
            "DeepSeek" -> model.contains("v4-flash", ignoreCase = true)
            else -> false
        }
    }

    fun supportsFiles(provider: String, model: String): Boolean {
        remoteProvider(provider)?.let { entry ->
            val subs = entry.filesSubstrings
            // null = provider flag applies to every model; otherwise substring-gated.
            return if (subs != null) subs.any { sub -> model.contains(sub, ignoreCase = true) }
            else entry.supportsFiles
        }
        return bundledSupportsFiles(provider, model)
    }

    private fun bundledSupportsFiles(provider: String, model: String): Boolean {
        return when (canonicalProvider(provider)) {
            "Gemini" -> true
            "Claude" -> true
            "ChatGPT" -> model.contains("gpt-5", ignoreCase = true) || model.contains("gpt-4", ignoreCase = true) || model.contains("o", ignoreCase = true)
            "OpenRouter" -> model.contains("claude", ignoreCase = true) || model.contains("gemini", ignoreCase = true) || model.contains("gpt-5", ignoreCase = true) || model.contains("gpt-4", ignoreCase = true)
            "OpenCode Zen", "OpenCode Go" -> true // gateway accepts attachments; model may ignore
            else -> false
        }
    }

    fun isOpenAiCompatible(provider: String): Boolean {
        remoteProvider(provider)?.let { return it.openAiCompatible }
        return canonicalProvider(provider) in openAiCompatibleProviders
    }

    fun getChatCompletionUrl(provider: String): String? {
        // NOTE: null is meaningful here (native-SDK providers) — a known
        // remote provider returns its field as-is, even when null.
        remoteProvider(provider)?.let { return it.chatCompletionUrl }
        return bundledChatCompletionUrl(provider)
    }

    private fun bundledChatCompletionUrl(provider: String): String? = when (canonicalProvider(provider)) {
        "ChatGPT" -> "https://api.openai.com/v1/chat/completions"
        "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
        "DeepSeek" -> "https://api.deepseek.com/v1/chat/completions"
        "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
        "OpenCode Zen" -> "https://opencode.ai/zen/v1/chat/completions"
        "OpenCode Go" -> "https://opencode.ai/zen/go/v1/chat/completions"
        else -> null
    }

    fun getModelsUrl(provider: String): String? {
        remoteProvider(provider)?.let { return it.modelsUrl }
        return bundledModelsUrl(provider)
    }

    private fun bundledModelsUrl(provider: String): String? = when (canonicalProvider(provider)) {
        "ChatGPT" -> "https://api.openai.com/v1/models"
        "Groq" -> "https://api.groq.com/openai/v1/models"
        "DeepSeek" -> "https://api.deepseek.com/v1/models"
        "OpenRouter" -> "https://openrouter.ai/api/v1/models"
        "OpenCode Zen" -> "https://opencode.ai/zen/v1/models"
        "OpenCode Go" -> "https://opencode.ai/zen/go/v1/models"
        else -> null
    }

    fun getProviderDescription(provider: String): String {
        remoteProvider(provider)?.description?.takeIf { it.isNotBlank() }?.let { return it }
        return bundledDetailedInfo[canonicalProvider(provider)] ?: "General-purpose AI provider."
    }

    fun getApiKeyPlaceholder(provider: String): String {
        remoteProvider(provider)?.keyPlaceholder?.takeIf { it.isNotBlank() }?.let { return it }
        return bundledApiKeyPlaceholder(provider)
    }

    private fun bundledApiKeyPlaceholder(provider: String): String = when (canonicalProvider(provider)) {
        "Gemini" -> "AIza..."
        "ChatGPT" -> "sk-..."
        "Groq" -> "gsk_..."
        "Claude" -> "sk-ant-..."
        "DeepSeek" -> "sk-..."
        "OpenRouter" -> "sk-or-..."
        "OpenCode Zen" -> "Paste key from opencode.ai/auth"
        "OpenCode Go" -> "Paste key from opencode.ai/auth"
        else -> ""
    }

    fun getApiKeyUrl(provider: String): String {
        remoteProvider(provider)?.keyUrl?.takeIf { it.isNotBlank() }?.let { return it }
        return bundledApiKeyUrl(provider)
    }

    private fun bundledApiKeyUrl(provider: String): String = when (canonicalProvider(provider)) {
        "Gemini" -> "https://aistudio.google.com/app/apikey"
        "ChatGPT" -> "https://platform.openai.com/api-keys"
        "Groq" -> "https://console.groq.com/keys"
        "Claude" -> "https://console.anthropic.com/settings/keys"
        "DeepSeek" -> "https://platform.deepseek.com/api_keys"
        "OpenRouter" -> "https://openrouter.ai/keys"
        "OpenCode Zen" -> "https://opencode.ai/auth"
        "OpenCode Go" -> "https://opencode.ai/auth"
        else -> ""
    }

    fun normalizeApiKeyInput(raw: String): String =
        raw.trim().removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'")

    fun validateApiKey(provider: String, key: String): Boolean {
        val normalized = normalizeApiKeyInput(key)
        if (normalized.isBlank()) return true
        if (normalized.length < 8) return false

        remoteProvider(provider)?.let { entry ->
            // null prefix (OpenCode keys) = any key long enough.
            val prefix = entry.keyPrefix
            return if (prefix.isNullOrBlank()) true else normalized.startsWith(prefix)
        }
        return bundledValidateApiKey(provider, normalized)
    }

    private fun bundledValidateApiKey(provider: String, normalized: String): Boolean {
        return when (canonicalProvider(provider)) {
            "Gemini" -> normalized.startsWith("AIza")
            "ChatGPT" -> normalized.startsWith("sk-")
            "Groq" -> normalized.startsWith("gsk_")
            "Claude" -> normalized.startsWith("sk-ant-")
            "DeepSeek" -> normalized.startsWith("sk-")
            "OpenRouter" -> normalized.startsWith("sk-or-")
            // Zen / Go keys have no stable prefix — accept anything long enough
            "OpenCode Zen", "OpenCode Go" -> normalized.length >= 8
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

    /**
     * Setup tutorials by provider. Remote entries override bundled ones;
     * bundled fills any gaps (and covers brand-new providers if the server
     * ever omits a tutorial).
     */
    val tutorials: Map<String, List<String>>
        get() = bundledTutorials + remoteTutorials()

    private fun remoteTutorials(): Map<String, List<String>> =
        remote?.providers
            ?.filter { it.displayName.isNotBlank() && it.tutorial.isNotEmpty() }
            ?.associate { it.displayName to it.tutorial }
            ?: emptyMap()

    private val bundledTutorials: Map<String, List<String>> = mapOf(
        "Gemini" to listOf(
            "Open Google AI Studio (aistudio.google.com) and sign in with your Google account",
            "Click 'Get API key' in the left sidebar",
            "Click 'Create API key', then pick or create a Google Cloud project",
            "Copy the key (starts with AIza...) and paste it here",
            "Free tier is generous and needs no credit card; if a key stops working, just create a fresh one"
        ),
        "ChatGPT" to listOf(
            "Open platform.openai.com and sign in (or create an account)",
            "Add billing: Settings → Billing → payment method (API usage is pay-as-you-go, separate from a Plus subscription)",
            "Open the API Keys page (platform.openai.com/api-keys)",
            "Click '+ Create new secret key', name it Toolz",
            "Copy the key NOW (starts with sk-...) — it is never shown again — and paste it here",
            "Optional: cap spending under Billing → Limits"
        ),
        "Groq" to listOf(
            "Open console.groq.com and sign in (Google or GitHub works)",
            "Open 'API Keys' in the left sidebar",
            "Click 'Create API Key', name it Toolz, Submit",
            "Copy the key (starts with gsk_...) and paste it here — free tier, no card needed",
            "Free tier has daily request limits that reset automatically; on 429 errors wait a bit or pick a lighter model"
        ),
        "Claude" to listOf(
            "Open console.anthropic.com and sign in (or create an account)",
            "Add billing: Settings → Billing → add a card (API is pay-as-you-go)",
            "Go to Settings → API Keys (console.anthropic.com/settings/keys)",
            "Click 'Create Key' and name it Toolz",
            "Copy the key (starts with sk-ant-...) and paste it here"
        ),
        "DeepSeek" to listOf(
            "Open platform.deepseek.com and sign in (or create an account)",
            "Top up a small balance — usage is pay-per-use and very cheap",
            "Open 'API Keys' in the sidebar (platform.deepseek.com/api_keys)",
            "Click 'Create API Key' and name it Toolz",
            "Copy the key (starts with sk-...) and paste it here"
        ),
        "OpenRouter" to listOf(
            "Open openrouter.ai and sign in (Google or GitHub works)",
            "Optional: add credits for paid models — models ending in :free work with zero balance",
            "Open Keys (openrouter.ai/keys)",
            "Click 'Create Key' and name it Toolz",
            "Copy the key (starts with sk-or-...) and paste it here"
        ),
        "OpenCode Zen" to listOf(
            "Open opencode.ai/auth and sign in",
            "Add billing details / buy credits (pay-as-you-go; the FREE models cost $0)",
            "Copy your API key from the dashboard and paste it here",
            "Pick a FREE model to start: muse-spark-1.3-contributor-free, mimo-v2.5-free or big-pickle",
            "Track usage in the same dashboard; enable balance fallback to keep going past Go limits"
        ),
        "OpenCode Go" to listOf(
            "Open opencode.ai/auth and sign in",
            "Subscribe to OpenCode Go ($10/month) — one subscription per workspace",
            "Copy your Go API key from the dashboard and paste it here",
            "Pick a Go model (e.g. glm-5.3-flash) and chat",
            "Watch the 5-hour / weekly / monthly usage meters in the console so caps never surprise you"
        )
    )

    val detailedInfo: Map<String, String>
        get() = bundledDetailedInfo + remoteDescriptions()

    private fun remoteDescriptions(): Map<String, String> =
        remote?.providers
            ?.filter { it.displayName.isNotBlank() && it.description.isNotBlank() }
            ?.associate { it.displayName to it.description }
            ?: emptyMap()

    private val bundledDetailedInfo: Map<String, String> = mapOf(
        "Gemini" to "Google's fast multimodal assistant. Class-leading context window lengths and strong speed. Flash-Lite is free-tier friendly.",
        "ChatGPT" to "OpenAI's latest GPT-5 and o-series models, providing industry-leading reasoning and generation.",
        "Groq" to "Extremely low-latency inference using LPUs. Free dev tier. NOTE: llama-4 / mixtral / llama-3.1-instant IDs were retired — use openai/gpt-oss-20b.",
        "Claude" to "Anthropic's 4/5-series models offering top-tier writing, coding, reasoning, and enormous context.",
        "DeepSeek" to "Powerful and extremely cost-efficient open-source models with top-tier math and coding. deepseek-chat is free to start.",
        "OpenRouter" to "A universal API hub that gives you a single place to access almost any model. Models ending in :free cost $0.",
        "OpenCode Zen" to "Curated gateway by the OpenCode team (opencode.ai/zen). Pay-as-you-go + several FREE models (Big Pickle, MiMo-V2.5 Free, Muse Spark Contributor Free). OpenAI-compatible: https://opencode.ai/zen/v1/chat/completions",
        "OpenCode Go" to "Low-cost \$10/mo subscription for reliable open coding models (GLM, Kimi, DeepSeek V4, Qwen, Muse Spark). OpenAI-compatible: https://opencode.ai/zen/go/v1/chat/completions"
    )

    val disclaimerText: String
        get() = remote?.disclaimerText?.takeIf { it.isNotBlank() } ?: bundledDisclaimerText

    val apiKeySuggestion: String
        get() = remote?.apiKeySuggestion?.takeIf { it.isNotBlank() } ?: bundledApiKeySuggestion

    private const val bundledDisclaimerText =
        "Higher-tier models are smarter but consume more tokens. Flash, Haiku, and Mini models are usually best for everyday mobile use."

    private const val bundledApiKeySuggestion =
        "Using your own API key gives you the best availability and the most predictable experience."
}