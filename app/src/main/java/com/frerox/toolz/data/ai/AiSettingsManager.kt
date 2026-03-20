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

private const val TAG              = "AiSettingsManager"
private const val PREFS_NAME       = "ai_settings"
private const val REMOTE_KEYS_URL  = "https://toolz-apis.vercel.app/api/keys"


private const val CACHE_TTL_MS         = 24L * 60L * 60L * 1_000L

private val PROVIDERS = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")

private val REVOKED_KEY_HASHES = setOf(
    "4381d9c3e1613c10fc7fb6d42fc3208052121e0797604934c5ce021acd1cff9c",
    "fd3e0d19cac374396d5426cc55d33d830b2410dc394626daa1209140127bb22e",
    "1cb5904a93b7cc80c3cfb9ff5cfc5f565f0057b20ca4eb80e23d9faef5e6f431",
    "f3b0517f3648dd224ee3c9ab5b5e5414d93b65a0f7f4db158d99e9bce16b3510",
    "ceb3196648e5f376e463271efb9bcd5d43fa9da8aa6f7b74606015d246910021",
    "24bc0856953c82907f5e8f0cf04291b4cb6d196b1a8510bc03eb504ed2793bb7",
)

enum class ApiKeySource { USER, REMOTE, NONE }
data class ResolvedApiKey(val value: String, val source: ApiKeySource)

@JsonClass(generateAdapter = true)
data class RemoteKeysResponse(
    @Json(name = "Gemini")     val gemini     : String? = null,
    @Json(name = "ChatGPT")    val chatGPT    : String? = null,
    @Json(name = "Groq")       val groq       : String? = null,
    @Json(name = "Claude")     val claude     : String? = null,
    @Json(name = "DeepSeek")   val deepSeek   : String? = null,
    @Json(name = "OpenRouter") val openRouter : String? = null,
) {
    fun get(p: String) = when (p) {
        "Gemini"     -> gemini.orEmpty().trim()
        "ChatGPT"    -> chatGPT.orEmpty().trim()
        "Groq"       -> groq.orEmpty().trim()
        "Claude"     -> claude.orEmpty().trim()
        "DeepSeek"   -> deepSeek.orEmpty().trim()
        "OpenRouter" -> openRouter.orEmpty().trim()
        else         -> ""
    }
    fun hasAnyKeys() = PROVIDERS.any { get(it).isNotBlank() }
}

@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    companion object {
        const val DEFAULT_PROVIDER = "Groq"
        const val DEFAULT_MODEL    = "llama-3.3-70b-versatile"
        private const val KEY_LAST_SYNC          = "remote_keys_last_sync"
        private const val KEY_LEGACY_DONE        = "revoked_default_key_migration_v1"
        private const val KEY_SCRUB_DONE         = "revoked_default_key_scrub_v2"
        private const val KEY_SAVED_CONFIGS      = "saved_configs"
        private fun userKey(p: String)   = "api_key_user_$p"
        private fun remoteKey(p: String) = "api_key_remote_$p"
        private fun legacyKey(p: String) = "api_key_$p"
    }

    private val prefs: SharedPreferences by lazy { buildPrefs() }
    private val configListAdapter by lazy {
        moshi.adapter<List<AiConfig>>(Types.newParameterizedType(List::class.java, AiConfig::class.java))
    }
    private val remoteKeysAdapter by lazy { moshi.adapter(RemoteKeysResponse::class.java) }

    init {
        migrateLegacyKeysIfNeeded()
        scrubStoredKeysIfNeeded()
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

    // ── Remote sync ────────────────────────────────────────────────────────

    /**
     * Fetches default API keys from the Vercel endpoint.
     * Single plain GET — no auth header required.
     */
    suspend fun syncRemoteKeys(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        if (!force && System.currentTimeMillis() - lastSync < CACHE_TTL_MS && hasRemoteKeys) {
            Log.d(TAG, "Cache fresh — skipping sync")
            return@withContext true
        }

        Log.d(TAG, "Fetching keys from $REMOTE_KEYS_URL")

        return@withContext try {
            val conn = (URL(REMOTE_KEYS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout    = 10_000
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                Log.e(TAG, "HTTP $code from keys endpoint: $err")
                conn.disconnect()
                return@withContext false
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val keys = remoteKeysAdapter.fromJson(body)
            if (keys == null || !keys.hasAnyKeys()) {
                Log.e(TAG, "Empty or unparseable keys response: ${body.take(200)}")
                return@withContext false
            }

            val ed = prefs.edit()
            PROVIDERS.forEach { p ->
                val v = sanitizeKeyValue(keys.get(p))
                if (v.isNotBlank()) ed.putString(remoteKey(p), v) else ed.remove(remoteKey(p))
            }
            ed.putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()

            Log.d(TAG, "Keys synced: ${PROVIDERS.filter { keys.get(it).isNotBlank() }.joinToString()}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.message}")
            false
        }
    }

    // ── Key resolution ─────────────────────────────────────────────────────

    val hasRemoteKeys: Boolean get() = PROVIDERS.any { getRemoteKey(it).isNotBlank() }

    fun resolveApiKey(provider: String = getAiProvider()): ResolvedApiKey {
        sanitizeStoredKeyForAccess(userKey(provider))?.takeIf { it.isNotBlank() }
            ?.let { return ResolvedApiKey(it, ApiKeySource.USER) }
        sanitizeStoredKeyForAccess(remoteKey(provider))?.takeIf { it.isNotBlank() }
            ?.let { return ResolvedApiKey(it, ApiKeySource.REMOTE) }
        return ResolvedApiKey("", ApiKeySource.NONE)
    }

    suspend fun resolveApiKeyWithRemoteSync(
        provider: String = getAiProvider(),
        forceRefreshRemote: Boolean = false,
    ): ResolvedApiKey {
        val current = resolveApiKey(provider)
        if (current.source == ApiKeySource.USER) return current
        if (current.source == ApiKeySource.REMOTE && !forceRefreshRemote) return current
        syncRemoteKeys(force = forceRefreshRemote || getRemoteKey(provider).isBlank())
        return resolveApiKey(provider)
    }

    fun getApiKey(provider: String = getAiProvider()): String = resolveApiKey(provider).value
    fun getRemoteKey(provider: String): String = sanitizeStoredKeyForAccess(remoteKey(provider)).orEmpty()
    fun hasUserApiKey(provider: String = getAiProvider()): Boolean = !sanitizeStoredKeyForAccess(userKey(provider)).isNullOrBlank()
    fun isUsingRemoteKey(provider: String = getAiProvider()): Boolean = resolveApiKey(provider).source == ApiKeySource.REMOTE
    fun getRawApiKey(provider: String = getAiProvider()): String = sanitizeStoredKeyForAccess(userKey(provider)).orEmpty()

    fun setApiKey(key: String, provider: String = getAiProvider()) {
        val s = sanitizeKeyValue(key)
        prefs.edit().apply {
            remove(legacyKey(provider))
            if (s.isBlank()) remove(userKey(provider)) else putString(userKey(provider), s)
        }.apply()
    }

    fun invalidateRemoteKey(provider: String, expectedValue: String? = null) {
        val cur = prefs.getString(remoteKey(provider), null)?.trim().orEmpty()
        if (cur.isBlank()) return
        if (expectedValue != null && cur != expectedValue.trim()) return
        prefs.edit().remove(remoteKey(provider)).apply()
        Log.w(TAG, "Invalidated remote key for $provider")
    }

    suspend fun refreshRemoteKeyAfterAuthFailure(provider: String, failedValue: String): ResolvedApiKey {
        invalidateRemoteKey(provider, failedValue)
        return resolveApiKeyWithRemoteSync(provider, forceRefreshRemote = true)
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
        val s = loadSavedConfigsInternal(); val san = s.map(::sanitizeConfig)
        if (san != s) persistConfigs(san); return san
    }
    fun saveConfig(config: AiConfig, oldName: String? = null) {
        val san = sanitizeConfig(config); val configs = getSavedConfigs().toMutableList()
        val idx = configs.indexOfFirst { it.name == (oldName ?: san.name) }
        if (idx != -1) configs[idx] = san else configs.add(san); persistConfigs(configs)
    }
    fun saveAllConfigs(configs: List<AiConfig>) = persistConfigs(configs.map(::sanitizeConfig))
    fun deleteConfig(name: String) = persistConfigs(getSavedConfigs().filter { it.name != name })
    fun applyConfig(config: AiConfig) {
        val s = sanitizeConfig(config); setAiProvider(s.provider); setApiKey(s.apiKey, s.provider); setSelectedModel(s.model, s.provider)
    }
    fun isUsingDefaultKey(provider: String = getAiProvider()): Boolean = !hasUserApiKey(provider) && getRemoteKey(provider).isNotBlank()
    fun isConfigured(): Boolean = PROVIDERS.any { resolveApiKey(it).source != ApiKeySource.NONE }

    // ── Migration / scrub ──────────────────────────────────────────────────

    private fun migrateLegacyKeysIfNeeded() {
        if (prefs.getBoolean(KEY_LEGACY_DONE, false)) return
        val ed = prefs.edit()
        PROVIDERS.forEach { p ->
            val v = prefs.getString(legacyKey(p), null)?.trim().orEmpty()
            if (v.isBlank() || isRevokedKey(v)) ed.remove(legacyKey(p))
            else if (!hasUserApiKey(p)) { ed.putString(userKey(p), v); ed.remove(legacyKey(p)) }
            else ed.remove(legacyKey(p))
        }
        runCatching { configListAdapter.toJson(loadSavedConfigsInternal().map(::sanitizeConfig)) }
            .getOrNull()?.let { ed.putString(KEY_SAVED_CONFIGS, it) }
        ed.putBoolean(KEY_LEGACY_DONE, true).apply()
    }

    private fun scrubStoredKeysIfNeeded() {
        if (prefs.getBoolean(KEY_SCRUB_DONE, false)) return
        val ed = prefs.edit()
        PROVIDERS.forEach { p ->
            listOf(userKey(p), remoteKey(p), legacyKey(p)).forEach { pk ->
                val v = prefs.getString(pk, null)?.trim().orEmpty()
                if (v.isNotBlank() && isRevokedKey(v)) { Log.w(TAG, "Scrubbing $pk"); ed.remove(pk) }
            }
        }
        ed.putBoolean(KEY_SCRUB_DONE, true).apply()
    }

    private fun loadSavedConfigsInternal(): List<AiConfig> {
        val json = prefs.getString(KEY_SAVED_CONFIGS, null) ?: return emptyList()
        return try { configListAdapter.fromJson(json) ?: emptyList() }
        catch (e: Exception) { Log.e(TAG, "Config deserialize: ${e.message}"); emptyList() }
    }

    private fun persistConfigs(configs: List<AiConfig>) {
        try { prefs.edit().putString(KEY_SAVED_CONFIGS, configListAdapter.toJson(configs.map(::sanitizeConfig))).apply() }
        catch (e: Exception) { Log.e(TAG, "Config serialize: ${e.message}") }
    }

    private fun sanitizeConfig(c: AiConfig): AiConfig { val k = sanitizeKeyValue(c.apiKey); return if (k == c.apiKey) c else c.copy(apiKey = k) }
    private fun sanitizeKeyValue(key: String): String { val t = key.trim(); return if (t.isBlank() || isRevokedKey(t)) "" else t }
    private fun sanitizeStoredKeyForAccess(pk: String): String? {
        val v = prefs.getString(pk, null)?.trim(); if (v.isNullOrBlank()) return null
        if (!isRevokedKey(v)) return v; prefs.edit().remove(pk).apply(); return null
    }
    private fun isRevokedKey(key: String): Boolean = key.isNotBlank() && sha256(key) in REVOKED_KEY_HASHES
    private fun sha256(v: String): String = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
}
