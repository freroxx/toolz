package com.frerox.toolz.shortcuts

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R

/**
 * Manages Tool Shortcuts – 48 tools pinnable to home screen.
 *
 * Static shortcuts (res/xml/shortcuts.xml) cover 4 most-used tools and appear on long-press of launcher.
 * This manager enables the remaining 44 (and all 48) as **pinned shortcuts** – uncapped by OS,
 * user explicitly pins via "Add to Home Screen" in the app. Each pinned shortcut deep-links directly
 * to the tool via MainActivity.EXTRA_NAVIGATE_TO + EXTRA_FROM_SHORTCUT (triggers exit confirmation).
 */
object ToolShortcutManager {

    /**
     * Build a ShortcutInfoCompat for a tool definition.
     */
    fun buildShortcutInfo(context: Context, def: ToolShortcutDef): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, def.route)
            putExtra(ToolShortcutDefinitions.EXTRA_FROM_SHORTCUT, true)
            putExtra(ToolShortcutDefinitions.EXTRA_SHORTCUT_ID, def.id)
            // Ensures shortcut launch creates a fresh task that skips dashboard history
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val label = context.getString(def.labelRes)
        val longLabel = try {
            context.getString(def.descriptionRes)
        } catch (_: Exception) {
            label
        }

        return ShortcutInfoCompat.Builder(context, def.id)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, def.iconRes))
            .setIntent(intent)
            .setRank(def.rank)
            .build()
    }

    /**
     * Check if the launcher supports pinned shortcuts (Android 8+ and launcher implements it).
     */
    fun isPinnedSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Request to pin a tool shortcut to the home screen.
     * Shows system pin dialog. Calls [onResult] with true if user confirmed (when callback invoked).
     * Note: Some launchers don't invoke callback – we optimistically show toast.
     */
    fun requestPinShortcut(
        context: Context,
        def: ToolShortcutDef,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (!isPinnedSupported(context)) {
            Toast.makeText(
                context,
                context.getString(R.string.st_Shortcut_NotSupported),
                Toast.LENGTH_SHORT
            ).show()
            onResult?.invoke(false)
            return false
        }

        val info = buildShortcutInfo(context, def)

        // Use ShortcutManagerCompat – system shows confirmation dialog
        val success = ShortcutManagerCompat.requestPinShortcut(context, info, null)
        if (!success) {
            // Launcher may have silently denied – still inform
            Toast.makeText(
                context,
                context.getString(R.string.st_Shortcut_PinFailed),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Optimistic feedback; actual pin may be pending user confirmation in launcher
            Toast.makeText(
                context,
                context.getString(R.string.st_Shortcut_Pinning, context.getString(def.labelRes)),
                Toast.LENGTH_SHORT
            ).show()
        }
        onResult?.invoke(success)
        return success
    }

    /**
     * Request pin by route string. Returns false if route not found.
     */
    fun requestPinByRoute(context: Context, route: String): Boolean {
        val def = ToolShortcutDefinitions.findByRoute(route)
            ?: return false
        return requestPinShortcut(context, def)
    }

    /**
     * Helper to publish dynamic shortcuts (optional) – not needed for pinned shortcuts,
     * but can keep top-4 recent as dynamic if desired.
     */
    fun publishDynamicShortcuts(context: Context, recentRoutes: List<String> = emptyList()) {
        // For now we keep static shortcuts only; dynamic publishing is optional.
        // This could be called with user's recentTools to update launcher dynamic list.
        // Ensure we don't exceed OS limit (5).
        val recentDefs = recentRoutes.mapNotNull { ToolShortcutDefinitions.findByRoute(it) }
            .take(2)
        val static = ToolShortcutDefinitions.staticShortcuts.take(3)
        val combined = (recentDefs + static).distinctBy { it.id }.take(4)
        val infos = combined.map { buildShortcutInfo(context, it) }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, infos)
        } catch (_: Exception) {
            // Ignore – launcher may not support or limit exceeded
        }
    }
}
