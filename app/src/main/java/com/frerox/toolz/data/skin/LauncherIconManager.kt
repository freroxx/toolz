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
 * Each preset ships two baked tiles: the octagon mark (`.Icon_<san>`) and the
 * circle mark (`.Icon_<san>_circle`). [shape] picks the variant, so Circle
 * genuinely reshapes the home-screen tile instead of being in-app only.
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

        fun isCircleShape(shape: String?): Boolean =
            IconShape.parse(shape) == IconShape.CIRCLE

        fun aliasName(presetId: String, shape: String? = null): String =
            ".Icon_${aliasSuffix(presetId)}${if (isCircleShape(shape)) "_circle" else ""}"

        fun allAliasNames(): List<String> =
            IconCatalog.BUILT_IN.flatMap {
                val san = it.id.replace('-', '_')
                listOf(".Icon_$san", ".Icon_${san}_circle")
            }.distinct()

        /** Ground truth for notifications: which tile alias is currently enabled. */
        fun enabledAlias(context: Context): String? = try {
            val pm = context.packageManager
            val pkg = context.packageName
            allAliasNames().firstOrNull {
                try {
                    pm.getComponentEnabledSetting(ComponentName(pkg, pkg + it)) ==
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } catch (_: Exception) { false }
            }
        } catch (_: Exception) { null }

        /** Preset id behind an alias name (`.Icon_grape_haze_circle` -> `grape-haze`). */
        fun presetIdForAlias(alias: String?): String {
            val san = (alias ?: "").removePrefix(".Icon_").removeSuffix("_circle")
            val id = san.replace('_', '-')
            return IconCatalog.find(id).id.takeIf { it == id } ?: DEFAULT_PRESET_ID
        }
    }

    /** Enable the alias for [presetId] + [shape], disable every other icon alias. */
    suspend fun apply(presetId: String, shape: String? = null) = withContext(Dispatchers.IO) {
        val spec = IconCatalog.find(presetId)
        // Mirror the pick to SharedPreferences so NotificationHelper (which can't
        // touch DataStore synchronously) renders notification art for this preset.
        try {
            context.getSharedPreferences("toolz_icon", Context.MODE_PRIVATE).edit()
                .putString("preset_id", spec.id)
                .putString("shape", if (isCircleShape(shape)) "CIRCLE" else "OCTAGON")
                .putInt("body_argb", spec.bodyStart.toInt())
                .apply()
        } catch (_: Exception) { }
        val target = aliasName(presetId, shape)
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
        // Wake ongoing notifications so the shade follows the new tile immediately.
        try { com.frerox.toolz.util.IconChangeBus.notifyChanged() } catch (_: Exception) { }
    }

    /** Re-assert the persisted preset + shape (boot, update, cold start). */
    suspend fun ensureApplied(presetId: String, shape: String? = null) = apply(presetId, shape)

    /**
     * Custom device photo: there is no manifest alias for it, so the launcher
     * keeps the nearest built-in alias enabled while every in-app surface
     * (marks, widgets, notification art via the mirrored prefs below) renders
     * the photo. Returns false when no photo file exists (caller falls back).
     */
    suspend fun applyCustomPhoto(shape: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (!CustomIconManager.exists(context)) return@withContext false
        val fallback = DEFAULT_PRESET_ID
        try {
            context.getSharedPreferences("toolz_icon", Context.MODE_PRIVATE).edit()
                .putString("preset_id", IconCatalog.CUSTOM_ID)
                .putString("shape", if (isCircleShape(shape)) "CIRCLE" else "OCTAGON")
                .putInt("body_argb", IconCatalog.find(fallback).bodyStart.toInt())
                .apply()
        } catch (_: Exception) { }
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            val target = aliasName(fallback, shape)
            for (alias in allAliasNames()) {
                try {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, pkg + alias),
                        if (alias == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        try { com.frerox.toolz.util.IconChangeBus.notifyChanged() } catch (_: Exception) { }
        true
    }
}
