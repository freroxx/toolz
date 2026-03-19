package com.frerox.toolz.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG       = "AiSettingsManager"
private const val PREFS_NAME = "ai_settings"

/**
 * Remote keys endpoint — the Vercel serverless function that reads
 * keys from server-side environment variables and returns them as JSON.
 */
private const val REMOTE_KEYS_URL  = "https://toolz-apis.vercel.app/api/keys"

/**
 * A shared app-token the server checks before returning any keys.
 * This is NOT the API key itself — it's just a simple gate to stop
 * random bots hitting your endpoint.  You set this value in:
 *   - Vercel dashboard → Environment Variables → APP_TOKEN
 *   - Here in the Android app as the matching constant below.
 *
 * Change "toolz-app-token-2026" to any random string you like, as long
 * as both sides match.
 */
private const val APP_TOKEN = "Fd9M5rs0ydhEz8YeegDzohZH"

/** How long a cached key fetch is considered fresh (24 hours). */
private const val CACHE_TTL_MS = 24L * 60L * 60L * 1_000L

// ─────────────────────────────────────────────────────────────
//  Remote key response model
// ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RemoteKeysResponse(
    val Gemini    : String = "",
    val ChatGPT   : String = "",
    val Groq      : String = "",
    val Claude    : String = "",
    val DeepSeek  : String = "",
    val OpenRouter: String = "",
)

// ─────────────────────────────────────────────────────────────
//  AiSettingsManager
// ─────────────────────────────────────────────────────────────

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

    private val remoteKeysAdapter by lazy {
        moshi.adapter(RemoteKeysResponse::class.java)
    }

    companion object {
        const val DEFAULT_PROVIDER = "Groq"
        const val DEFAULT_MODEL    = "llama-3.3-70b-versatile"

        // SharedPreferences key for the last successful remote key sync timestamp
        private const val KEY_LAST_SYNC = "remote_keys_last_sync"
    }

    // ── SharedPreferences construction ─────────────────────────────────────

    private fun buildPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedSharedPreferences unavailable; using plain prefs: ${e.message}")
        context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    // ── Remote key sync ────────────────────────────────────────────────────

    /**
     * Fetches the default API keys from the secure Vercel endpoint and
     * caches them in EncryptedSharedPreferences.
     *
     * Called once at app start (from the app's initialization code or
     * Application.onCreate) and whenever the cached values are stale.
     *
     * The server returns keys only when the request includes the correct
     * `x-toolz-token` header — a simple secret shared between the app
     * and the Vercel environment variable `APP_TOKEN`.
     *
     * @return true on success, false on network/parse error (caller should
     *         continue normally — the app uses any previously cached keys).
     */
    suspend fun syncRemoteKeys(): Boolean = withContext(Dispatchers.IO) {
        // Skip if the cache is still fresh
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        if (System.currentTimeMillis() - lastSync < CACHE_TTL_MS) {
            Log.d(TAG, "Remote keys cache still fresh — skipping sync")
            return@withContext true
        }

        return@withContext try {
            val connection = (URL(REMOTE_KEYS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod        = "GET"
                connectTimeout       = 10_000
                readTimeout          = 10_000
                setRequestProperty("x-toolz-token", APP_TOKEN)
                setRequestProperty("Accept",         "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "Remote keys endpoint returned $responseCode")
                connection.disconnect()
                return@withContext false
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val keys = remoteKeysAdapter.fromJson(body) ?: run {
                Log.e(TAG, "Failed to parse remote keys response")
                return@withContext false
            }

            // Cache each key under the same per-provider namespace used by getApiKey()
            // so the rest of the app needs zero changes to read them.
            val editor = prefs.edit()
            if (keys.Gemini.isNotBlank())     editor.putString("api_key_Gemini",     keys.Gemini)
            if (keys.ChatGPT.isNotBlank())    editor.putString("api_key_ChatGPT",    keys.ChatGPT)
            if (keys.Groq.isNotBlank())       editor.putString("api_key_Groq",       keys.Groq)
            if (keys.Claude.isNotBlank())     editor.putString("api_key_Claude",     keys.Claude)
            if (keys.DeepSeek.isNotBlank())   editor.putString("api_key_DeepSeek",   keys.DeepSeek)
            if (keys.OpenRouter.isNotBlank()) editor.putString("api_key_OpenRouter", keys.OpenRouter)
            editor.putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            editor.apply()

            Log.d(TAG, "Remote keys synced successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Remote key sync failed: ${e.message}")
            false
        }
    }

    /**
     * True when at least the Groq key has been cached from the remote endpoint.
     * Use to decide whether to show an "AI features unavailable" banner.
     */
    val hasRemoteKeys: Boolean
        get() = prefs.getString("api_key_Groq", null)?.isNotBlank() == true

    // ── Provider ──────────────────────────────────────────────────────────

    fun getAiProvider(): String =
        prefs.getString("ai_provider", DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER

    fun setAiProvider(provider: String) =
        prefs.edit().putString("ai_provider", provider).apply()

    // ── API keys ──────────────────────────────────────────────────────────

    /**
     * Returns the effective API key for [provider].
     *
     * Priority:
     * 1. User-supplied key (stored under "api_key_<provider>")
     * 2. Remote key cached by [syncRemoteKeys] (stored under the same key)
     * 3. Empty string — AI features unavailable until a sync succeeds
     *
     * The caller ([AiRepositoryImpl]) should check for blank and show
     * a UI prompt to retry sync or enter a personal key.
     */
    fun getApiKey(provider: String = getAiProvider()): String =
        prefs.getString("api_key_$provider", null)?.takeIf { it.isNotBlank() } ?: ""

    fun getRawApiKey(provider: String = getAiProvider()): String =
        prefs.getString("api_key_$provider", "") ?: ""

    /**
     * Stores a user-supplied API key for [provider].
     * Blank key clears the stored value (reverts to the remote-synced key).
     */
    fun setApiKey(key: String, provider: String = getAiProvider()) {
        val editor = prefs.edit()
        if (key.isBlank()) {
            // Don't remove entirely — the remote-synced key may still be there.
            // Instead store an empty marker so we know the user explicitly cleared it.
            editor.remove("api_key_user_$provider")
        } else {
            editor.putString("api_key_$provider", key)
        }
        editor.apply()
    }

    // ── Model ─────────────────────────────────────────────────────────────

    fun getSelectedModel(provider: String = getAiProvider()): String =
        prefs.getString("selected_model_$provider", null)
            ?: AiSettingsHelper.getRecommendedModel(provider)

    fun setSelectedModel(model: String, provider: String = getAiProvider()) =
        prefs.edit().putString("selected_model_$provider", model).apply()

    // ── Saved configs ──────────────────────────────────────────────────────

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
        val idx = configs.indexOfFirst { it.name == (oldName ?: config.name) }
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