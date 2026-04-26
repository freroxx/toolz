package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import com.frerox.toolz.MainActivity
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.frerox.toolz.R
import java.util.Locale

// ---------------------------------------------------------------------------
//  Pomodoro Timer — Glance Widget
//  2×2 compact  : ring bitmap + digital clock + play/pause
//  4×2 expanded : + daily goal card + skip / reset buttons
// ---------------------------------------------------------------------------

class PomodoroGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT  = DpSize(150.dp, 150.dp)
        private val EXPANDED = DpSize(280.dp, 150.dp)
    }

    override val sizeMode    = SizeMode.Responsive(setOf(COMPACT, EXPANDED))
    override val stateDefinition = PomodoroWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs        = getAppWidgetState<Preferences>(context, PomodoroWidgetStateDefinition, id)
        val mode         = prefs[PomodoroWidgetState.KEY_MODE]          ?: "WORK"
        val remainingMs  = prefs[PomodoroWidgetState.KEY_REMAINING_MS]  ?: 0f
        val totalMs      = prefs[PomodoroWidgetState.KEY_TOTAL_MS].takeIf { it != null && it > 0f }
                           ?: (25 * 60 * 1000f)
        val isRunning    = prefs[PomodoroWidgetState.KEY_IS_RUNNING]    ?: false
        val sessionsDone = prefs[PomodoroWidgetState.KEY_SESSIONS_DONE] ?: 0
        val sessionsGoal = prefs[PomodoroWidgetState.KEY_SESSIONS_GOAL] ?: 8
        val progress     = (1f - (remainingMs / totalMs)).coerceIn(0f, 1f)

        // Resolve dynamic colors from system (API 31+) for the Canvas ring
        val ringArgb  = if (Build.VERSION.SDK_INT >= 31) {
            if (mode == "WORK")
                context.resources.getColor(android.R.color.system_accent1_200, context.theme)
            else
                context.resources.getColor(android.R.color.system_accent3_200, context.theme)
        } else if (mode == "WORK") 0xFFB0A8FF.toInt() else 0xFFEFB8C8.toInt()

        val trackArgb = if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_neutral1_600, context.theme)
        } else 0xFF79747E.toInt()

        val bgArgb = if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_neutral1_900, context.theme)
        } else 0xFF1C1B1F.toInt()

        val ringBitmap = buildRingBitmap(progress, ringArgb, trackArgb, bgArgb, 300)
        val timeString = formatMillis(remainingMs.toLong())

        // Intent to open the Pomodoro screen in the app
        val openPomodoroIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "pomodoro")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        provideContent {
            GlanceTheme {
                val size       = LocalSize.current
                val isExpanded = size.width >= EXPANDED.width

                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(28.dp)
                        .clickable(actionStartActivity(openPomodoroIntent)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isExpanded) {
                        ExpandedPomodoroContent(
                            timeString = timeString, mode = mode,
                            isRunning = isRunning, sessionsDone = sessionsDone,
                            sessionsGoal = sessionsGoal, ringBitmap = ringBitmap,
                        )
                    } else {
                        CompactPomodoroContent(
                            timeString = timeString, mode = mode,
                            isRunning = isRunning, ringBitmap = ringBitmap,
                        )
                    }
                }
            }
        }
    }

    private fun buildRingBitmap(
        progress: Float, ringColor: Int, trackColor: Int, bgColor: Int, sizePx: Int,
    ): Bitmap {
        val bmp    = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = sizePx * 0.12f // Thicker ring for better visibility
        val inset  = stroke / 2f + sizePx * 0.03f

        // Draw background circle
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL })

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = trackColor; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
            alpha = 40 // Make track subtle
        }
        canvas.drawArc(RectF(inset, inset, sizePx - inset, sizePx - inset), -90f, 360f, false, trackPaint)

        if (progress > 0.005f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(RectF(inset, inset, sizePx - inset, sizePx - inset),
                -90f, progress * 360f, false, progressPaint)
        }
        return bmp
    }

    private fun formatMillis(ms: Long): String {
        val total = ((ms + 999) / 1000).coerceAtLeast(0)
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
    }
}

@Composable
private fun CompactPomodoroContent(
    timeString: String, mode: String, isRunning: Boolean, ringBitmap: Bitmap,
) {
    Box(modifier = GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        Image(provider = ImageProvider(ringBitmap), contentDescription = null,
            modifier = GlanceModifier.fillMaxSize())
        
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(modeLabel(mode), maxLines = 1, style = TextStyle(
                color = if (mode == "WORK") GlanceTheme.colors.primary else GlanceTheme.colors.tertiary, 
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            
            Spacer(GlanceModifier.height(4.dp))
            
            Text(timeString, style = TextStyle(
                color = GlanceTheme.colors.onSurface, fontSize = 32.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            
            Spacer(GlanceModifier.height(10.dp))
            
            val pkg = androidx.glance.LocalContext.current.packageName
            val receiver = android.content.ComponentName(pkg, "com.frerox.toolz.widget.glance.PomodoroWidgetReceiver")
            val toggleIntent = Intent(POMODORO_ACTION_TOGGLE).apply { component = receiver }

            Box(modifier = GlanceModifier.size(44.dp).cornerRadius(22.dp)
                    .background(if (mode == "WORK") GlanceTheme.colors.primary else GlanceTheme.colors.tertiary)
                    .clickable(actionSendBroadcast(toggleIntent)),
                contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                    contentDescription = null, modifier = GlanceModifier.size(24.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(
                        if (mode == "WORK") GlanceTheme.colors.onPrimary else GlanceTheme.colors.onTertiary
                    ))
            }
        }
    }
}

@Composable
private fun ExpandedPomodoroContent(
    timeString: String, mode: String, isRunning: Boolean,
    sessionsDone: Int, sessionsGoal: Int, ringBitmap: Bitmap,
) {
    Row(modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Ring on left
        Box(modifier = GlanceModifier.size(120.dp), contentAlignment = Alignment.Center) {
            Image(provider = ImageProvider(ringBitmap), contentDescription = null,
                modifier = GlanceModifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxSize()) {
                Text(modeLabel(mode), style = TextStyle(
                    color = if (mode == "WORK") GlanceTheme.colors.primary else GlanceTheme.colors.tertiary, 
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                Text(timeString, style = TextStyle(
                    color = GlanceTheme.colors.onSurface, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
        }

        Spacer(GlanceModifier.width(16.dp))

        // Right: goal + buttons
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically) {
            // Daily goal chip
            Box(modifier = GlanceModifier.fillMaxWidth().cornerRadius(16.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    Text("DAILY FOCUS GOAL", style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold))
                    Text("$sessionsDone / $sessionsGoal sessions", style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold))
                }
            }

            Spacer(GlanceModifier.height(14.dp))

            // Control row
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val pkg = androidx.glance.LocalContext.current.packageName
                val receiver = android.content.ComponentName(pkg, "com.frerox.toolz.widget.glance.PomodoroWidgetReceiver")
                val resetIntent = Intent(POMODORO_ACTION_RESET).apply { component = receiver }
                val toggleIntent = Intent(POMODORO_ACTION_TOGGLE).apply { component = receiver }
                val skipIntent = Intent(POMODORO_ACTION_SKIP).apply { component = receiver }

                // Reset Button
                Box(modifier = GlanceModifier.size(42.dp).cornerRadius(21.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionSendBroadcast(resetIntent)),
                    contentAlignment = Alignment.Center) {
                    Image(provider = ImageProvider(R.drawable.ic_widget_reset),
                        contentDescription = "Reset", modifier = GlanceModifier.size(20.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
                }
                
                Spacer(GlanceModifier.width(12.dp))
                
                // Play/Pause Button
                Box(modifier = GlanceModifier.size(52.dp).cornerRadius(26.dp)
                        .background(if (mode == "WORK") GlanceTheme.colors.primary else GlanceTheme.colors.tertiary)
                        .clickable(actionSendBroadcast(toggleIntent)),
                    contentAlignment = Alignment.Center) {
                    Image(provider = ImageProvider(if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                        contentDescription = null, modifier = GlanceModifier.size(26.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(
                            if (mode == "WORK") GlanceTheme.colors.onPrimary else GlanceTheme.colors.onTertiary
                        ))
                }
                
                Spacer(GlanceModifier.width(12.dp))
                
                // Skip Button
                Box(modifier = GlanceModifier.size(42.dp).cornerRadius(21.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionSendBroadcast(skipIntent)),
                    contentAlignment = Alignment.Center) {
                    Image(provider = ImageProvider(R.drawable.ic_widget_next),
                        contentDescription = "Skip", modifier = GlanceModifier.size(20.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
                }
            }
        }
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "SHORT_BREAK" -> "SHORT BREAK"
    "LONG_BREAK"  -> "LONG BREAK"
    else          -> "WORK"
}

// Broadcast action constants
const val POMODORO_ACTION_TOGGLE = "com.frerox.toolz.WIDGET_POMO_TOGGLE"
const val POMODORO_ACTION_RESET  = "com.frerox.toolz.WIDGET_POMO_RESET"
const val POMODORO_ACTION_SKIP   = "com.frerox.toolz.WIDGET_POMO_SKIP"
