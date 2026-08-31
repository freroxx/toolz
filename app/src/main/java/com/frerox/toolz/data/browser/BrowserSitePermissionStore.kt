package com.frerox.toolz.data.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BrowserSitePermission { ASK, ALLOW, DENY }

/** Local per-origin permission decisions for camera and microphone. */
@Singleton
class BrowserSitePermissionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("browser_site_permissions_v1", Context.MODE_PRIVATE)

    fun decision(origin: String): BrowserSitePermission = runCatching {
        BrowserSitePermission.valueOf(prefs.getString(key(origin), BrowserSitePermission.ASK.name) ?: BrowserSitePermission.ASK.name)
    }.getOrDefault(BrowserSitePermission.ASK)

    fun setDecision(origin: String, decision: BrowserSitePermission) {
        prefs.edit().putString(key(origin), decision.name).apply()
    }

    fun clear(origin: String) { prefs.edit().remove(key(origin)).apply() }
    fun clearAll() { prefs.edit().clear().apply() }

    private fun key(origin: String): String {
        val host = runCatching { java.net.URI(origin).host }.getOrNull()
            ?: origin.removePrefix("www.")
        return "permission_${host.lowercase()}"
    }
}
