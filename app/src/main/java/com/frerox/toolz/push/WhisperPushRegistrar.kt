/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.push

import android.content.Context
import android.util.Log
import com.frerox.toolz.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-UX UnifiedPush registration (F-Droid swap: replaces the implicit FCM
 * auto-init token flow with an explicit [ensureRegistered] call on app start and
 * sign-in; outside coroutines so MainActivity/ViewModel call sites stay simple).
 *
 * Selection policy mirrors the connector's own preference (its docs state that
 * external distributors are favored over embedded ones): a distributor the user
 * installed on purpose (ntfy, NextPush, Sunup, ...) wins; with none installed,
 * the bundled embedded distributor inside THIS app is chosen, so push works with
 * zero setup. That embedded path requires Google Play Services or microG — on
 * de-Googled devices without any distributor, registration simply never starts.
 *
 * No picker dialog is shown: Toolz embeds a distributor, so the "choose an app
 * to ship your push" Android screen is unnecessary friction here.
 */
@Singleton
class WhisperPushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureRegistered() {
        runCatching {
            val availableDistributors = UnifiedPush.getDistributors(context)
            val currentDistributor = UnifiedPush.getAckDistributor(context)
                ?: UnifiedPush.getSavedDistributor(context)

            if (!currentDistributor.isNullOrBlank() && availableDistributors.contains(currentDistributor)) {
                // Previously configured distributor is still installed: preserve it.
                register()
                return
            }

            when (val resolved = UnifiedPush.resolveDefaultDistributor(context)) {
                is ResolvedDistributor.Found -> {
                    // User's default distributor (or a single unambiguous one) —
                    // the exact flow the connector docs recommend.
                    UnifiedPush.saveDistributor(context, resolved.packageName)
                    register()
                }

                is ResolvedDistributor.ToSelect -> {
                    // Several distributors installed, none is a default yet.
                    // Auto-pick: explicit externals first, else our embedded one.
                    val chosen = availableDistributors.firstOrNull { it != context.packageName }
                        ?: availableDistributors.firstOrNull()
                        ?: return
                    UnifiedPush.saveDistributor(context, chosen)
                    register()
                }

                ResolvedDistributor.NoneAvailable -> {
                    // No push capability on this device (no Google services, no
                    // external distributor app). Whisper still works in-app over
                    // realtime; background wake-ups are simply not available.
                }
            }
        }.onFailure { Log.w(TAG, "UnifiedPush registration skipped", it) }
    }

    private fun register() {
        val vapid = BuildConfig.WHISPER_VAPID_PUBLIC_KEY
        val appName = context.getString(com.frerox.toolz.R.string.app_name)
        runCatching {
            if (vapid.isBlank()) {
                // Blank VAPID is valid for plain distributors (ntfy/NextPush);
                // RFC 8291 distributors will report VAPID_REQUIRED and gracefully
                // fail — documented degradation rather than a hard crash.
                UnifiedPush.register(context, messageForDistributor = appName)
            } else {
                UnifiedPush.register(
                    context,
                    messageForDistributor = appName,
                    // RFC 8292 public key, base64url, uncompressed form (87 chars) —
                    // exactly what VapidNotValidException would otherwise reject.
                    vapid = vapid,
                )
            }
        }.onFailure { Log.w(TAG, "UnifiedPush register() failed", it) }
    }

    companion object {
        private const val TAG = "WhisperPush"
    }
}
