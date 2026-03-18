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

/**
 * Manages AI provider settings and saved configurations.
 */
@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private val configListAdapter by lazy {
        moshi.adapter<List<AiConfig>>(
            Types.newParameterizedType(List::class.java, AiConfig::class.java)
        )
    }

    companion object {
        const val DEFAULT_PROVIDER = "Groq"
        const val DEFAULT_MODEL    = "llama-3.3-70b-versatile"
    }

    private fun buildPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable; using plain prefs: ${e.message}")
            context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    fun getAiProvider(): String =
        prefs.getString("ai_provider", DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER

    fun setAiProvider(provider: String) =
        prefs.edit().putString("ai_provider", provider).apply()

    fun getApiKey(provider: String = getAiProvider()): String {
        val stored = prefs.getString("api_key_$provider", null)
        return if (stored.isNullOrBlank()) AiSettingsHelper.getDefaultKey(provider) else stored
    }

    fun getRawApiKey(provider: String = getAiProvider()): String =
        prefs.getString("api_key_$provider", "") ?: ""

    fun setApiKey(key: String, provider: String = getAiProvider()) {
        val editor = prefs.edit()
        if (key.isBlank()) {
            editor.remove("api_key_$provider")
        } else {
            editor.putString("api_key_$provider", key)
        }
        editor.apply()
    }

    fun getSelectedModel(provider: String = getAiProvider()): String =
        prefs.getString("selected_model_$provider", null)
            ?: AiSettingsHelper.getRecommendedModel(provider)

    fun setSelectedModel(model: String, provider: String = getAiProvider()) =
        prefs.edit().putString("selected_model_$provider", model).apply()

    fun getSavedConfigs(): List<AiConfig> {
        val json = prefs.getString("saved_configs", null) ?: return emptyList()
        return try {
            configListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize saved configs: ${e.message}")
            emptyList()
        }
    }

    fun saveConfig(config: AiConfig, oldName: String? = null) {
        val configs = getSavedConfigs().toMutableList()
        val targetName = oldName ?: config.name
        val idx = configs.indexOfFirst { it.name == targetName }
        if (idx != -1) configs[idx] = config else configs.add(config)
        persistConfigs(configs)
    }

    fun saveAllConfigs(configs: List<AiConfig>) = persistConfigs(configs)

    fun deleteConfig(name: String) {
        persistConfigs(getSavedConfigs().filter { it.name != name })
    }

    fun applyConfig(config: AiConfig) {
        setAiProvider(config.provider)
        setApiKey(config.apiKey, config.provider)
        setSelectedModel(config.model, config.provider)
    }

    fun isUsingDefaultKey(provider: String = getAiProvider()): Boolean =
        prefs.getString("api_key_$provider", null).isNullOrBlank()

    fun isConfigured(): Boolean = true

    private fun persistConfigs(configs: List<AiConfig>) {
        val json = try {
            configListAdapter.toJson(configs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize configs: ${e.message}")
            return
        }
        prefs.edit().putString("saved_configs", json).apply()
    }
}
