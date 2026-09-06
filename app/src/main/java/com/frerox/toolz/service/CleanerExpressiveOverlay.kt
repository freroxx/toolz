/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.format.Formatter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.frerox.toolz.R

class CleanerExpressiveOverlay(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onExitRequested()
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var isAttached: Boolean = false

    private var tvBadge: TextView? = null
    private var ivAppIcon: ImageView? = null
    private var tvAppName: TextView? = null
    private var tvAppStatus: TextView? = null
    private var pbProgress: ProgressBar? = null
    private var tvProgressCount: TextView? = null
    private var btnExit: Button? = null

    fun show(totalApps: Int) {
        if (isAttached && overlayView != null) return

        try {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.layout_cleaner_overlay, null)

            // Block touches across the entire screen so Settings cannot be tapped
            view.setOnTouchListener { _, _ -> true }

            tvBadge = view.findViewById(R.id.tv_overlay_badge)
            ivAppIcon = view.findViewById(R.id.iv_app_icon)
            tvAppName = view.findViewById(R.id.tv_app_name)
            tvAppStatus = view.findViewById(R.id.tv_app_status)
            pbProgress = view.findViewById(R.id.pb_clean_progress)
            tvProgressCount = view.findViewById(R.id.tv_progress_count)
            btnExit = view.findViewById(R.id.btn_exit)

            btnExit?.setOnClickListener {
                listener.onExitRequested()
            }

            if (totalApps <= 1) {
                pbProgress?.isIndeterminate = true
                tvProgressCount?.text = "Clearing app cache…"
            } else {
                pbProgress?.isIndeterminate = false
                pbProgress?.progress = 0
                tvProgressCount?.text = "0 of $totalApps"
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            windowManager.addView(view, params)
            overlayView = view
            isAttached = true
        } catch (_: Exception) {}
    }

    fun updateApp(
        pkg: String,
        appName: String,
        index: Int,
        total: Int,
        freedBytes: Long = 0L
    ) {
        if (!isAttached || overlayView == null) return

        try {
            val icon: Drawable? = try {
                context.packageManager.getApplicationIcon(pkg)
            } catch (_: Exception) {
                ContextCompat.getDrawable(context, R.drawable.ic_shortcut_file_cleaner)
            }
            ivAppIcon?.setImageDrawable(icon)
        } catch (_: Exception) {}

        tvAppName?.text = appName

        if (total > 1) {
            tvBadge?.text = "AUTO-CLEARING CACHES"
            tvBadge?.setTextColor(Color.parseColor("#7BA7F7"))
            if (freedBytes > 0L) {
                tvAppStatus?.text = "Freed ${Formatter.formatFileSize(context, freedBytes)} • Clearing cache…"
            } else {
                tvAppStatus?.text = "Clearing cache…"
            }
            val percent = (((index + 1).toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
            pbProgress?.isIndeterminate = false
            pbProgress?.progress = percent
            tvProgressCount?.text = "${index + 1} of $total ($percent%)"
        } else {
            tvBadge?.text = "CLEARING APP CACHE"
            tvBadge?.setTextColor(Color.parseColor("#7BA7F7"))
            tvAppStatus?.text = "Clearing cache…"
            pbProgress?.isIndeterminate = true
            tvProgressCount?.text = "Clearing app cache…"
        }
    }

    fun showDone(clearedCount: Int, freedBytes: Long = 0L) {
        if (!isAttached || overlayView == null) return
        tvBadge?.text = "FINISHED"
        tvBadge?.setTextColor(Color.parseColor("#10B981"))
        tvAppName?.text = "Cache Cleared"
        val freedText = if (freedBytes > 0L) " (${Formatter.formatFileSize(context, freedBytes)} freed)" else ""
        tvAppStatus?.text = if (clearedCount > 1) "$clearedCount apps cleared$freedText ✓" else "App cache cleared$freedText ✓"
        pbProgress?.isIndeterminate = false
        pbProgress?.progress = 100
        tvProgressCount?.text = "Returning to Toolz…"
    }

    fun hide() {
        if (!isAttached || overlayView == null) return
        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) {}
        overlayView = null
        isAttached = false
    }
}
