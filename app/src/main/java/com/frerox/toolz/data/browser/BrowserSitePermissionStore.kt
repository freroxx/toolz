package com.frerox.toolz.data.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BrowserSitePermission { ASK, ALLOW, DENY }

enum class BrowserPermissionType { CAMERA, MICROPHONE, NOTIFICATION, GEOLOCATION }

/** Local per-origin permission decisions for camera/mic/notification/geolocation. */
@Singleton
class BrowserSitePermissionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("browser_site_permissions_v1", Context.MODE_PRIVATE)

    // Legacy key support (v1 stored as permission_<host>)
    private fun legacyKey(origin: String): String {
        val host = extractHost(origin)
        return "permission_${host.lowercase()}"
    }

    fun decision(origin: String): BrowserSitePermission = decisionFor(origin, BrowserPermissionType.CAMERA)

    fun decisionFor(origin: String, type: BrowserPermissionType): BrowserSitePermission = runCatching {
        val host = extractHost(origin)
        val key = keyFor(host, type)
        val raw = prefs.getString(key, null)
            ?: prefs.getString(legacyKey(origin), null) // fallback to v1
            ?: BrowserSitePermission.ASK.name
        BrowserSitePermission.valueOf(raw)
    }.getOrDefault(BrowserSitePermission.ASK)

    fun setDecision(origin: String, decision: BrowserSitePermission) {
        // Keep legacy for camera/mic generic, plus per-type
        setDecisionFor(origin, BrowserPermissionType.CAMERA, decision)
        setDecisionFor(origin, BrowserPermissionType.MICROPHONE, decision)
    }

    fun setDecisionFor(origin: String, type: BrowserPermissionType, decision: BrowserSitePermission) {
        val host = extractHost(origin)
        prefs.edit().putString(keyFor(host, type), decision.name).apply()
        // Also update generic legacy key for backward compat
        if (type == BrowserPermissionType.CAMERA || type == BrowserPermissionType.MICROPHONE) {
            prefs.edit().putString(legacyKey(origin), decision.name).apply()
        }
    }

    fun clear(origin: String) {
        val host = extractHost(origin)
        BrowserPermissionType.entries.forEach { type ->
            prefs.edit().remove(keyFor(host, type)).apply()
        }
        prefs.edit().remove(legacyKey(origin)).apply()
    }

    fun clearAll() { prefs.edit().clear().apply() }

    fun getAllOrigins(): Set<String> {
        return prefs.all.keys.mapNotNull { key ->
            when {
                key.startsWith("permission_") -> {
                    val remainder = key.removePrefix("permission_")
                    // key format: host or host_type ; extract host part
                    val host = remainder.substringBefore("_").takeIf { it.isNotBlank() } ?: remainder
                    // Need to reconstruct host: for keys with type suffix, host is before last underscore if suffix is type
                    val typeSuffix = BrowserPermissionType.entries.map { it.name.lowercase() }
                    val isTyped = typeSuffix.any { remainder.endsWith("_${it}") }
                    if (isTyped) remainder.substringBeforeLast("_") else remainder
                }
                else -> null
            }
        }.toSet()
    }

    fun getAllPermissions(): Map<String, Map<BrowserPermissionType, BrowserSitePermission>> {
        val result = mutableMapOf<String, MutableMap<BrowserPermissionType, BrowserSitePermission>>()
        // Scan all keys
        prefs.all.keys.forEach { key ->
            if (!key.startsWith("permission_")) return@forEach
            val remainder = key.removePrefix("permission_")
            val type = BrowserPermissionType.entries.find { remainder.endsWith("_${it.name.lowercase()}") }
            if (type != null) {
                val host = remainder.removeSuffix("_${type.name.lowercase()}")
                val perm = runCatching { BrowserSitePermission.valueOf(prefs.getString(key, "ASK")!!) }.getOrDefault(BrowserSitePermission.ASK)
                if (perm != BrowserSitePermission.ASK) {
                    result.getOrPut(host) { mutableMapOf() }[type] = perm
                }
            } else {
                // legacy generic
                val host = remainder
                val perm = runCatching { BrowserSitePermission.valueOf(prefs.getString(key, "ASK")!!) }.getOrDefault(BrowserSitePermission.ASK)
                if (perm != BrowserSitePermission.ASK) {
                    result.getOrPut(host) { mutableMapOf() }[BrowserPermissionType.CAMERA] = perm
                    result.getOrPut(host) { mutableMapOf() }[BrowserPermissionType.MICROPHONE] = perm
                }
            }
        }
        return result
    }

    fun getPermissionsForOrigin(origin: String): Map<BrowserPermissionType, BrowserSitePermission> {
        val host = extractHost(origin)
        return BrowserPermissionType.entries.associateWith { decisionFor(host, it) }
            .filterValues { it != BrowserSitePermission.ASK }
    }

    private fun keyFor(host: String, type: BrowserPermissionType): String = "permission_${host.lowercase()}_${type.name.lowercase()}"

    private fun extractHost(origin: String): String {
        return runCatching { java.net.URI(origin).host }.getOrNull()?.lowercase()?.removePrefix("www.")
            ?: origin.lowercase().removePrefix("www.").substringBefore("/").substringBefore(":")
    }

    private fun key(origin: String): String = keyFor(extractHost(origin), BrowserPermissionType.CAMERA)
}
