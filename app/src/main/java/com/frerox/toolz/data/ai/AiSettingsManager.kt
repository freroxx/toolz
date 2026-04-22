package com.frerox.toolz.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiSettingsManager"
private const val PREFS_NAME = "ai_settings"

private val PROVIDERS = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

enum class ApiKeySource { USER, DEFAULT, REMOTE, NONE }
data class ResolvedApiKey(val value: String, val source: ApiKeySource)

@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    companion object {
        const val DEFAULT_PROVIDER = "Groq"
        private const val KEY_SAVED_CONFIGS = "saved_configs"
        private const val KEY_DYNAMIC_PROMPTS = "dynamic_prompts_enabled"
        private const val KEY_NEVER_SHOW_PROMPTS = "never_show_prompts"
        private const val KEY_EDITED_PROMPTS = "edited_prompts"
        private const val KEY_PROMPT_FORMAT = "prompt_format"
        private fun userKey(p: String) = "api_key_user_$p"
        private fun remoteKey(p: String) = "api_key_remote_$p"
    }

    private val prefs: SharedPreferences by lazy { buildPrefs() }
    private val configListAdapter by lazy {
        moshi.adapter<List<AiConfig>>(Types.newParameterizedType(List::class.java, AiConfig::class.java))
    }
    private val stringListAdapter by lazy {
        moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
    }
    private val mapAdapter by lazy {
        moshi.adapter<Map<String, String>>(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))
    }

    // ── Settings ───────────────────────────────────────────────────────────

    fun isDynamicPromptsEnabled(): Boolean = prefs.getBoolean(KEY_DYNAMIC_PROMPTS, true)
    fun setDynamicPromptsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_DYNAMIC_PROMPTS, enabled).apply()

    fun getPromptFormat(): String = prefs.getString(KEY_PROMPT_FORMAT, "medium") ?: "medium"
    fun setPromptFormat(format: String) = prefs.edit().putString(KEY_PROMPT_FORMAT, format).apply()

    fun getNeverShowPrompts(): List<String> {
        val json = prefs.getString(KEY_NEVER_SHOW_PROMPTS, null) ?: return emptyList()
        return try { stringListAdapter.fromJson(json) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    fun addNeverShowPrompt(prompt: String) {
        val list = getNeverShowPrompts().toMutableList()
        if (!list.contains(prompt)) {
            list.add(prompt)
            prefs.edit().putString(KEY_NEVER_SHOW_PROMPTS, stringListAdapter.toJson(list)).apply()
        }
    }

    fun getEditedPrompts(): Map<String, String> {
        val json = prefs.getString(KEY_EDITED_PROMPTS, null) ?: return emptyMap()
        return try { mapAdapter.fromJson(json) ?: emptyMap() } catch (e: Exception) { emptyMap() }
    }

    fun saveEditedPrompt(original: String, edited: String) {
        val map = getEditedPrompts().toMutableMap()
        map[original] = edited
        prefs.edit().putString(KEY_EDITED_PROMPTS, mapAdapter.toJson(map)).apply()
    }

    fun resetPromptsData() {
        prefs.edit()
            .remove(KEY_NEVER_SHOW_PROMPTS)
            .remove(KEY_EDITED_PROMPTS)
            .apply()
    }

    private fun buildPrefs(): SharedPreferences = try {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, PREFS_NAME, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedSharedPreferences unavailable: ${e.message}")
        context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    // ── Key resolution ─────────────────────────────────────────────────────

    fun resolveApiKey(provider: String = getAiProvider()): ResolvedApiKey {
        // 1. Check user-provided key
        prefs.getString(userKey(provider), null)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { return ResolvedApiKey(it, ApiKeySource.USER) }
        
        // 2. Check remote key (synced from Toolz server)
        prefs.getString(remoteKey(provider), null)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { return ResolvedApiKey(it, ApiKeySource.REMOTE) }

        // 3. Check default key from BuildConfig (hardcoded)
        AiSettingsHelper.getDefaultKey(provider).takeIf { it.isNotBlank() }
            ?.let { return ResolvedApiKey(it, ApiKeySource.DEFAULT) }
            
        return ResolvedApiKey("", ApiKeySource.NONE)
    }

    fun resolveApiKeyWithRemoteSync(provider: String = getAiProvider()): ResolvedApiKey {
        return resolveApiKey(provider)
    }

    fun syncRemoteKeys(force: Boolean = false): Boolean {
        // Placeholder for a remote key hehehe, I hope this works
        // In a real scenario, this would fetch from a Firebase config or a custom API
        return false
    }

    fun refreshRemoteKeys() {
        syncRemoteKeys(force = true)
    }

    fun retrySyncKeys() {
        syncRemoteKeys(force = true)
    }

    fun invalidateRemoteKey(provider: String, key: String) {
        if (prefs.getString(remoteKey(provider), null) == key) {
            prefs.edit().remove(remoteKey(provider)).apply()
        }
    }

    fun refreshRemoteKeyAfterAuthFailure(provider: String, failedKey: String): ResolvedApiKey {
        invalidateRemoteKey(provider, failedKey)
        syncRemoteKeys(force = true)
        return resolveApiKey(provider)
    }

    fun getApiKey(provider: String = getAiProvider()): String = resolveApiKey(provider).value
    fun hasUserApiKey(provider: String = getAiProvider()): Boolean = !prefs.getString(userKey(provider), null).isNullOrBlank()
    
    fun isUsingDefaultKey(provider: String = getAiProvider()): Boolean {
        val source = resolveApiKey(provider).source
        return source == ApiKeySource.DEFAULT || source == ApiKeySource.REMOTE
    }

    fun getRawApiKey(provider: String = getAiProvider()): String = prefs.getString(userKey(provider), "").orEmpty()

    fun setApiKey(key: String, provider: String = getAiProvider()) {
        val s = key.trim()
        prefs.edit().apply {
            if (s.isBlank()) remove(userKey(provider)) else putString(userKey(provider), s)
        }.apply()
    }

    fun setRemoteApiKey(key: String, provider: String) {
        val s = key.trim()
        if (s.isBlank()) {
            prefs.edit().remove(remoteKey(provider)).apply()
        } else {
            prefs.edit().putString(remoteKey(provider), s).apply()
        }
    }

    // ── Provider / model ───────────────────────────────────────────────────

    fun getAiProvider(): String = prefs.getString("ai_provider", DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER
    fun setAiProvider(provider: String) = prefs.edit().putString("ai_provider", provider).apply()
    
    fun getSelectedModel(provider: String = getAiProvider()): String =
        prefs.getString("selected_model_$provider", null) ?: AiSettingsHelper.getRecommendedModel(provider)
        
    fun setSelectedModel(model: String, provider: String = getAiProvider()) =
        prefs.edit().putString("selected_model_$provider", model).apply()

    // ── Saved configs ──────────────────────────────────────────────────────

    fun getSavedConfigs(): List<AiConfig> {
        val json = prefs.getString(KEY_SAVED_CONFIGS, null) ?: return emptyList()
        return try { configListAdapter.fromJson(json) ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }

    fun saveConfig(config: AiConfig, oldName: String? = null) {
        val configs = getSavedConfigs().toMutableList()
        val idx = configs.indexOfFirst { it.name == (oldName ?: config.name) }
        if (idx != -1) configs[idx] = config else configs.add(config)
        persistConfigs(configs)
    }

    fun saveAllConfigs(configs: List<AiConfig>) = persistConfigs(configs)
    
    fun deleteConfig(name: String) = persistConfigs(getSavedConfigs().filter { it.name != name })
    
    fun applyConfig(config: AiConfig) {
        setAiProvider(config.provider)
        setApiKey(config.apiKey, config.provider)
        setSelectedModel(config.model, config.provider)
    }

    private fun persistConfigs(configs: List<AiConfig>) {
        try { prefs.edit().putString(KEY_SAVED_CONFIGS, configListAdapter.toJson(configs)).apply() }
        catch (e: Exception) { Log.e(TAG, "Config serialize: ${e.message}") }
    }

    fun isConfigured(): Boolean = PROVIDERS.any { resolveApiKey(it).source != ApiKeySource.NONE }
}
