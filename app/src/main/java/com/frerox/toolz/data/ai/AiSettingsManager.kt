package com.frerox.toolz.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiSettingsManager"
private const val PREFS_NAME = "ai_settings"
private const val REMOTE_KEYS_URL = "https://toolz-apis.vercel.app/api/keys"
private const val APP_TOKEN = "Fd9M5rs0ydhEz8YeegDzohZH"
private const val CACHE_TTL_MS = 24L * 60L * 60L * 1_000L

private val PROVIDERS = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

// SHA-256 fingerprints of the default keys that were previously shipped in the APK.
private val REVOKED_LEGACY_KEY_HASHES = setOf(
    "4381d9c3e1613c10fc7fb6d42fc3208052121e0797604934c5ce021acd1cff9c",
    "fd3e0d19cac374396d5426cc55d33d830b2410dc394626daa1209140127bb22e",
    "1cb5904a93b7cc80c3cfb9ff5cfc5f565f0057b20ca4eb80e23d9faef5e6f431",
    "f3b0517f3648dd224ee3c9ab5b5e5414d93b65a0f7f4db158d99e9bce16b3510",
    "ceb3196648e5f376e463271efb9bcd5d43fa9da8aa6f7b74606015d246910021",
    "24bc0856953c82907f5e8f0cf04291b4cb6d196b1a8510bc03eb504ed2793bb7",
)

enum class ApiKeySource { USER, REMOTE, NONE }

data class ResolvedApiKey(
    val value: String,
    val source: ApiKeySource,
)

@JsonClass(generateAdapter = true)
data class RemoteKeysResponse(
    @Json(name = "Gemini") val geminiUpper: String? = null,
    @Json(name = "gemini") val geminiLower: String? = null,
    @Json(name = "ChatGPT") val chatGptUpper: String? = null,
    @Json(name = "chatgpt") val chatGptLower: String? = null,
    @Json(name = "Groq") val groqUpper: String? = null,
    @Json(name = "groq") val groqLower: String? = null,
    @Json(name = "Claude") val claudeUpper: String? = null,
    @Json(name = "claude") val claudeLower: String? = null,
    @Json(name = "DeepSeek") val deepSeekUpper: String? = null,
    @Json(name = "deepseek") val deepSeekLower: String? = null,
    @Json(name = "OpenRouter") val openRouterUpper: String? = null,
    @Json(name = "openrouter") val openRouterLower: String? = null,
) {
    fun getGemini() = (geminiUpper ?: geminiLower ?: "").trim()
    fun getChatGPT() = (chatGptUpper ?: chatGptLower ?: "").trim()
    fun getGroq() = (groqUpper ?: groqLower ?: "").trim()
    fun getClaude() = (claudeUpper ?: claudeLower ?: "").trim()
    fun getDeepSeek() = (deepSeekUpper ?: deepSeekLower ?: "").trim()
    fun getOpenRouter() = (openRouterUpper ?: openRouterLower ?: "").trim()

    fun hasAnyKeys(): Boolean = listOf(
        getGemini(),
        getChatGPT(),
        getGroq(),
        getClaude(),
        getDeepSeek(),
        getOpenRouter(),
    ).any { it.isNotBlank() }
}

@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    companion object {
        const val DEFAULT_PROVIDER = "Groq"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

        private const val KEY_LAST_SYNC = "remote_keys_last_sync"
        private const val KEY_LEGACY_MIGRATION_DONE = "revoked_default_key_migration_v1"
        private const val KEY_STORED_KEY_SCRUB_DONE = "revoked_default_key_scrub_v2"
        private const val KEY_SAVED_CONFIGS = "saved_configs"

        private fun userKey(provider: String) = "api_key_user_$provider"
        private fun remoteKey(provider: String) = "api_key_remote_$provider"
        private fun legacyKey(provider: String) = "api_key_$provider"
    }

    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private val configListAdapter by lazy {
        moshi.adapter<List<AiConfig>>(
            Types.newParameterizedType(List::class.java, AiConfig::class.java)
        )
    }

    private val remoteKeysAdapter by lazy {
        moshi.adapter(RemoteKeysResponse::class.java)
    }

    init {
        migrateLegacyKeysIfNeeded()
        scrubStoredKeysIfNeeded()
    }

    private fun buildPrefs(): SharedPreferences = try {
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

    suspend fun syncRemoteKeys(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        if (!force && System.currentTimeMillis() - lastSync < CACHE_TTL_MS && hasRemoteKeys) {
            Log.d(TAG, "Remote keys cache still fresh - skipping sync")
            return@withContext true
        }

        Log.d(TAG, "Syncing remote keys from $REMOTE_KEYS_URL")
        val authAttempts = listOf<Pair<String, (HttpURLConnection) -> Unit>>(
            "x-toolz-token" to { connection ->
                connection.setRequestProperty("x-toolz-token", APP_TOKEN)
            },
            "Authorization Bearer" to { connection ->
                connection.setRequestProperty("Authorization", "Bearer $APP_TOKEN")
            },
            "x-app-token" to { connection ->
                connection.setRequestProperty("x-app-token", APP_TOKEN)
            },
        )

        var lastFailure = "Unknown sync failure"
        for ((label, applyAuth) in authAttempts) {
            val outcome = fetchRemoteKeys(label, applyAuth)
            if (outcome == null) {
                lastFailure = "Failed to parse remote keys response using $label"
                continue
            }
            if (!outcome.hasAnyKeys()) {
                lastFailure = "Remote keys response via $label was empty"
                continue
            }

            cacheRemoteKeys(outcome)
            val missingProviders = buildList {
                if (outcome.getGemini().isBlank()) add("Gemini")
                if (outcome.getChatGPT().isBlank()) add("ChatGPT")
                if (outcome.getGroq().isBlank()) add("Groq")
                if (outcome.getClaude().isBlank()) add("Claude")
                if (outcome.getDeepSeek().isBlank()) add("DeepSeek")
                if (outcome.getOpenRouter().isBlank()) add("OpenRouter")
            }
            if (missingProviders.isNotEmpty()) {
                Log.w(TAG, "Remote key sync succeeded but some providers are empty: ${missingProviders.joinToString()}")
            }
            Log.d(TAG, "Remote keys synced successfully via $label")
            return@withContext true
        }

        Log.e(TAG, "Remote key sync failed: $lastFailure")
        false
    }

    val hasRemoteKeys: Boolean
        get() = PROVIDERS.any { provider ->
            getRemoteKey(provider).isNotBlank()
        }

    fun getAiProvider(): String =
        prefs.getString("ai_provider", DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER

    fun setAiProvider(provider: String) =
        prefs.edit().putString("ai_provider", provider).apply()

    fun resolveApiKey(provider: String = getAiProvider()): ResolvedApiKey {
        val user = sanitizeStoredKeyForAccess(userKey(provider))
        if (!user.isNullOrBlank()) {
            return ResolvedApiKey(user, ApiKeySource.USER)
        }

        val remote = sanitizeStoredKeyForAccess(remoteKey(provider))
        if (!remote.isNullOrBlank()) {
            return ResolvedApiKey(remote, ApiKeySource.REMOTE)
        }

        return ResolvedApiKey("", ApiKeySource.NONE)
    }

    fun getApiKey(provider: String = getAiProvider()): String =
        resolveApiKey(provider).value

    fun getRemoteKey(provider: String): String =
        sanitizeStoredKeyForAccess(remoteKey(provider)).orEmpty()

    fun hasUserApiKey(provider: String = getAiProvider()): Boolean =
        !sanitizeStoredKeyForAccess(userKey(provider)).isNullOrBlank()

    fun isUsingRemoteKey(provider: String = getAiProvider()): Boolean =
        resolveApiKey(provider).source == ApiKeySource.REMOTE

    fun getRawApiKey(provider: String = getAiProvider()): String =
        sanitizeStoredKeyForAccess(userKey(provider)).orEmpty()

    fun setApiKey(key: String, provider: String = getAiProvider()) {
        val sanitized = sanitizeUserKey(key)
        val editor = prefs.edit()
        editor.remove(legacyKey(provider))
        if (sanitized.isBlank()) {
            editor.remove(userKey(provider))
        } else {
            editor.putString(userKey(provider), sanitized)
        }
        editor.apply()
    }

    fun invalidateRemoteKey(provider: String, expectedValue: String? = null) {
        val current = prefs.getString(remoteKey(provider), null)?.trim().orEmpty()
        if (current.isBlank()) return
        if (expectedValue != null && current != expectedValue.trim()) return

        prefs.edit().remove(remoteKey(provider)).apply()
        Log.w(TAG, "Invalidated cached remote key for $provider after auth failure")
    }

    suspend fun refreshRemoteKeyAfterAuthFailure(
        provider: String,
        failedValue: String,
    ): ResolvedApiKey {
        invalidateRemoteKey(provider, failedValue)
        syncRemoteKeys(force = true)
        return resolveApiKey(provider)
    }

    fun getSelectedModel(provider: String = getAiProvider()): String =
        prefs.getString("selected_model_$provider", null)
            ?: AiSettingsHelper.getRecommendedModel(provider)

    fun setSelectedModel(model: String, provider: String = getAiProvider()) =
        prefs.edit().putString("selected_model_$provider", model).apply()

    fun getSavedConfigs(): List<AiConfig> {
        val stored = loadSavedConfigsInternal()
        val sanitized = stored.map(::sanitizeConfig)
        if (sanitized != stored) {
            persistConfigs(sanitized)
        }
        return sanitized
    }

    fun saveConfig(config: AiConfig, oldName: String? = null) {
        val sanitized = sanitizeConfig(config)
        val configs = getSavedConfigs().toMutableList()
        val idx = configs.indexOfFirst { it.name == (oldName ?: sanitized.name) }
        if (idx != -1) configs[idx] = sanitized else configs.add(sanitized)
        persistConfigs(configs)
    }

    fun saveAllConfigs(configs: List<AiConfig>) =
        persistConfigs(configs.map(::sanitizeConfig))

    fun deleteConfig(name: String) {
        persistConfigs(getSavedConfigs().filter { it.name != name })
    }

    fun applyConfig(config: AiConfig) {
        val sanitized = sanitizeConfig(config)
        setAiProvider(sanitized.provider)
        setApiKey(sanitized.apiKey, sanitized.provider)
        setSelectedModel(sanitized.model, sanitized.provider)
    }

    fun isUsingDefaultKey(provider: String = getAiProvider()): Boolean =
        !hasUserApiKey(provider) && getRemoteKey(provider).isNotBlank()

    fun isConfigured(): Boolean = true

    private fun migrateLegacyKeysIfNeeded() {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATION_DONE, false)) return

        val editor = prefs.edit()
        PROVIDERS.forEach { provider ->
            val legacyValue = prefs.getString(legacyKey(provider), null)?.trim().orEmpty()
            if (legacyValue.isBlank()) {
                editor.remove(legacyKey(provider))
                return@forEach
            }

            if (isRevokedLegacyKey(legacyValue)) {
                Log.w(TAG, "Removing revoked legacy default key for $provider")
                editor.remove(legacyKey(provider))
                return@forEach
            }

            if (!hasUserApiKey(provider)) {
                Log.d(TAG, "Migrating legacy user key into user slot for $provider")
                editor.putString(userKey(provider), legacyValue)
            }
            editor.remove(legacyKey(provider))
        }

        val storedConfigs = loadSavedConfigsInternal()
        val sanitizedConfigs = storedConfigs.map(::sanitizeConfig)
        if (sanitizedConfigs != storedConfigs) {
            val json = runCatching { configListAdapter.toJson(sanitizedConfigs) }.getOrNull()
            if (json != null) {
                editor.putString(KEY_SAVED_CONFIGS, json)
            }
        }

        editor.putBoolean(KEY_LEGACY_MIGRATION_DONE, true).apply()
    }

    private fun scrubStoredKeysIfNeeded() {
        if (prefs.getBoolean(KEY_STORED_KEY_SCRUB_DONE, false)) return

        val editor = prefs.edit()
        PROVIDERS.forEach { provider ->
            scrubStoredKey(editor, userKey(provider), "user")
            scrubStoredKey(editor, remoteKey(provider), "remote")
            scrubStoredKey(editor, legacyKey(provider), "legacy")
        }
        editor.putBoolean(KEY_STORED_KEY_SCRUB_DONE, true).apply()
    }

    private fun loadSavedConfigsInternal(): List<AiConfig> {
        val json = prefs.getString(KEY_SAVED_CONFIGS, null) ?: return emptyList()
        return try {
            configListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize saved configs: ${e.message}")
            emptyList()
        }
    }

    private fun persistConfigs(configs: List<AiConfig>) {
        val json = try {
            configListAdapter.toJson(configs.map(::sanitizeConfig))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize configs: ${e.message}")
            return
        }
        prefs.edit().putString(KEY_SAVED_CONFIGS, json).apply()
    }

    private fun cacheRemoteKeys(keys: RemoteKeysResponse) {
        val editor = prefs.edit()
        cacheRemoteKey(editor, "Gemini", keys.getGemini())
        cacheRemoteKey(editor, "ChatGPT", keys.getChatGPT())
        cacheRemoteKey(editor, "Groq", keys.getGroq())
        cacheRemoteKey(editor, "Claude", keys.getClaude())
        cacheRemoteKey(editor, "DeepSeek", keys.getDeepSeek())
        cacheRemoteKey(editor, "OpenRouter", keys.getOpenRouter())
        editor.putLong(KEY_LAST_SYNC, System.currentTimeMillis())
        editor.apply()
    }

    private fun cacheRemoteKey(
        editor: SharedPreferences.Editor,
        provider: String,
        value: String,
    ) {
        val sanitized = sanitizeKeyValue(value)
        if (sanitized.isBlank()) {
            editor.remove(remoteKey(provider))
        } else {
            editor.putString(remoteKey(provider), sanitized)
        }
    }

    private fun fetchRemoteKeys(
        label: String,
        applyAuth: (HttpURLConnection) -> Unit,
    ): RemoteKeysResponse? {
        return try {
            val connection = (URL(REMOTE_KEYS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                applyAuth(this)
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText()
                Log.w(TAG, "Remote keys endpoint via $label returned $responseCode: $errorBody")
                connection.disconnect()
                return null
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            Log.d(TAG, "Received remote keys payload via $label (length: ${body.length})")
            remoteKeysAdapter.fromJson(body)
        } catch (e: Exception) {
            Log.w(TAG, "Remote key sync attempt via $label failed: ${e.message}")
            null
        }
    }

    private fun sanitizeConfig(config: AiConfig): AiConfig {
        val sanitizedKey = sanitizeKeyValue(config.apiKey)
        return if (sanitizedKey == config.apiKey) config else config.copy(apiKey = sanitizedKey)
    }

    private fun sanitizeUserKey(key: String): String =
        sanitizeKeyValue(key)

    private fun sanitizeKeyValue(key: String): String {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return ""
        return if (isRevokedLegacyKey(trimmed)) "" else trimmed
    }

    private fun sanitizeStoredKeyForAccess(prefKey: String): String? {
        val value = prefs.getString(prefKey, null)?.trim()
        if (value.isNullOrBlank()) return null
        if (!isRevokedLegacyKey(value)) return value

        prefs.edit().remove(prefKey).apply()
        Log.w(TAG, "Removed revoked stored key from $prefKey during access")
        return null
    }

    private fun scrubStoredKey(
        editor: SharedPreferences.Editor,
        prefKey: String,
        label: String,
    ) {
        val value = prefs.getString(prefKey, null)?.trim().orEmpty()
        if (value.isBlank()) {
            editor.remove(prefKey)
            return
        }
        if (isRevokedLegacyKey(value)) {
            Log.w(TAG, "Removing revoked $label key from $prefKey")
            editor.remove(prefKey)
        }
    }

    private fun isRevokedLegacyKey(key: String): Boolean =
        key.isNotBlank() && sha256(key) in REVOKED_LEGACY_KEY_HASHES

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
