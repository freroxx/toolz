/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.skin

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import com.frerox.toolz.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a Home-screen pin request, so the UI can say what happened. */
sealed interface PinResult {
    object Pinned : PinResult
    object Updated : PinResult
    object Unsupported : PinResult
    object Failed : PinResult
}

/**
 * Pins a preset (or the custom photo) to the Home screen as a shortcut whose
 * icon is the real Toolz art.
 *
 * Launcher icon packs win against manifest `activity-alias` tiles, but launchers
 * render pinned shortcut icons from the app-supplied bitmap, so this is the only
 * way to show a chosen Toolz icon while a pack is active.
 *
 * Shortcut ids are stable per preset + shape, so long-pressing the same icon
 * again updates the existing pin instead of stacking a duplicate. The intent
 * targets [MainActivity] directly — never the enabled alias — because the alias
 * is disabled as soon as the user picks another preset, which would leave the
 * pin opening a dead component.
 */
@Singleton
class HomeShortcutPinner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val PREFIX = "toolz_tile_"
        const val CUSTOM_PHOTO_ID = "toolz_custom_photo"

        /** `grape-haze` + OCTAGON -> `toolz_tile_grape_haze`; CIRCLE adds `_circle`. */
        fun shortcutId(presetId: String, shape: String?): String {
            val san = IconCatalog.find(presetId).id.replace('-', '_')
            return PREFIX + san + if (LauncherIconManager.isCircleShape(shape)) "_circle" else ""
        }

        fun isPresetShortcut(id: String?): Boolean = id?.startsWith(PREFIX) == true

        /** Inverse of [shortcutId]; null when the id is not a known preset pin. */
        fun presetIdFromShortcut(id: String?): String? {
            if (!isPresetShortcut(id)) return null
            val san = id!!.removePrefix(PREFIX).removeSuffix("_circle")
            if (san.isEmpty()) return null
            val candidate = san.replace('_', '-')
            return IconCatalog.BUILT_IN.firstOrNull { it.id == candidate }?.id
        }
    }

    suspend fun pinPreset(presetId: String, shape: String? = null): PinResult {
        val spec = IconCatalog.find(presetId)
        val bitmap = PresetTileRenderer.render(context, spec.id, shape) ?: return PinResult.Failed
        return publish(
            id = shortcutId(spec.id, shape),
            label = "${spec.name} · Toolz",
            icon = Icon.createWithAdaptiveBitmap(bitmap),
        )
    }

    suspend fun pinCustomPhoto(): PinResult = withContext(Dispatchers.IO) {
        try {
            val file = CustomIconManager.fileFor(context)
            if (!file.exists() || file.length() == 0L) return@withContext PinResult.Failed
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return@withContext PinResult.Failed
            publish(CUSTOM_PHOTO_ID, "Toolz", Icon.createWithAdaptiveBitmap(bitmap))
        } catch (_: Exception) {
            PinResult.Failed
        }
    }

    /** Re-render every already-pinned preset with [shape] (circle/octagon rescale). */
    suspend fun refreshPinnedPresets(shape: String? = null) = withContext(Dispatchers.IO) {
        try {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return@withContext
            val pinned = try { sm.pinnedShortcuts } catch (_: Exception) { return@withContext }
            val updates = pinned.mapNotNull { info ->
                val presetId = presetIdFromShortcut(info.id) ?: return@mapNotNull null
                val bitmap = PresetTileRenderer.render(context, presetId, shape)
                    ?: return@mapNotNull null
                buildInfo(
                    id = info.id,
                    label = "${IconCatalog.find(presetId).name} · Toolz",
                    icon = Icon.createWithAdaptiveBitmap(bitmap),
                )
            }
            if (updates.isNotEmpty()) {
                try { sm.updateShortcuts(updates) } catch (_: Exception) { }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun publish(id: String, label: String, icon: Icon): PinResult =
        withContext(Dispatchers.IO) {
            try {
                val sm = context.getSystemService(ShortcutManager::class.java)
                    ?: return@withContext PinResult.Failed
                if (!sm.isRequestPinShortcutSupported) return@withContext PinResult.Unsupported
                val info = buildInfo(id, label, icon)
                val alreadyPinned = try {
                    sm.pinnedShortcuts.any { it.id == id }
                } catch (_: Exception) {
                    false
                }
                if (alreadyPinned) {
                    sm.updateShortcuts(listOf(info))
                    PinResult.Updated
                } else {
                    if (sm.requestPinShortcut(info, null)) PinResult.Pinned else PinResult.Failed
                }
            } catch (_: Exception) {
                PinResult.Failed
            }
        }

    private fun buildInfo(id: String, label: String, icon: Icon): ShortcutInfo {
        val launch = Intent(Intent.ACTION_MAIN).apply {
            setClassName(context, MainActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return ShortcutInfo.Builder(context, id)
            .setShortLabel("Toolz")
            .setLongLabel(label)
            .setIcon(icon)
            .setIntent(launch)
            .setLongLived(true)
            .build()
    }
}
