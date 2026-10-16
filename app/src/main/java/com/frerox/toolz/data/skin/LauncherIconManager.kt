/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.skin

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switches the launcher icon by enabling exactly one `.Icon_*`
 * activity-alias and disabling the rest (PackageManager component toggle).
 * MainActivity itself carries no LAUNCHER filter — the aliases are the
 * only home-screen entry points, so there is never a duplicate icon.
 *
 * Fail-open everywhere: launchers refresh on their own schedule, and some
 * take a few seconds to pick up the change.
 */
@Singleton
class LauncherIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val DEFAULT_PRESET_ID = IconCatalog.DEFAULT_ID

        /** Alias suffix per preset id (mirrors the manifest `.Icon_<san>` names). */
        fun aliasSuffix(presetId: String): String =
            IconCatalog.find(presetId).id.replace('-', '_')

        fun aliasName(presetId: String): String = ".Icon_${aliasSuffix(presetId)}"

        fun allAliasNames(): List<String> =
            IconCatalog.BUILT_IN.map { ".Icon_${it.id.replace('-', '_')}" }.distinct()
    }

    /** Enable the alias for [presetId], disable every other icon alias. */
    suspend fun apply(presetId: String) = withContext(Dispatchers.IO) {
        // Mirror the pick to SharedPreferences so NotificationHelper (which can't
        // touch DataStore synchronously) renders notification art for this preset.
        try {
            context.getSharedPreferences("toolz_icon", Context.MODE_PRIVATE).edit()
                .putString("preset_id", IconCatalog.find(presetId).id)
                .apply()
        } catch (_: Exception) { }
        val target = aliasName(presetId)
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            var changed = false
            for (alias in allAliasNames()) {
                val want = if (alias == target) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                val current = try {
                    pm.getComponentEnabledSetting(ComponentName(pkg, pkg + alias))
                } catch (_: Exception) {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                }
                if (current != want) {
                    try {
                        pm.setComponentEnabledSetting(
                            ComponentName(pkg, pkg + alias),
                            want,
                            PackageManager.DONT_KILL_APP,
                        )
                        changed = true
                    } catch (_: Exception) { }
                }
            }
            // Unknown ids (e.g. server "custom") fall back to the default alias,
            // which is already enabled above when nothing matched.
            if (!changed) Unit
        } catch (_: Exception) { }
    }

    /** Re-assert the persisted preset (boot, update, cold start). */
    suspend fun ensureApplied(presetId: String) = apply(presetId)
}
