package com.frerox.toolz.widget.glance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
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
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import java.util.Locale

class PomodoroGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT = DpSize(150.dp, 150.dp)
        private val EXPANDED = DpSize(300.dp, 160.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED))
    override val stateDefinition = PomodoroWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, PomodoroWidgetStateDefinition, id)
        val mode = prefs[PomodoroWidgetState.KEY_MODE] ?: "WORK"
        val remainingMs = prefs[PomodoroWidgetState.KEY_REMAINING_MS] ?: 25 * 60 * 1000f
        val totalMs = prefs[PomodoroWidgetState.KEY_TOTAL_MS]?.takeIf { it > 0f } ?: 25 * 60 * 1000f
        val isRunning = prefs[PomodoroWidgetState.KEY_IS_RUNNING] ?: false
        val sessionsDone = prefs[PomodoroWidgetState.KEY_SESSIONS_DONE] ?: 0
        val sessionsGoal = prefs[PomodoroWidgetState.KEY_SESSIONS_GOAL] ?: 8
        val elapsedProgress = (1f - remainingMs / totalMs).coerceIn(0f, 1f)
        val goalProgress = (sessionsDone.toFloat() / sessionsGoal.coerceAtLeast(1)).coerceIn(0f, 1f)

        val palette = PomodoroWidgetPalette.resolve(context, mode)
        val ringBitmap = buildProgressBitmap(
            progress = elapsedProgress,
            ringColor = palette.accent,
            trackColor = palette.track,
            fillColor = palette.surface,
            sizePx = 320,
        )
        val goalBitmap = buildProgressBitmap(
            progress = goalProgress,
            ringColor = palette.secondary,
            trackColor = palette.track,
            fillColor = palette.surfaceVariant,
            sizePx = 120,
        )

        val openPomodoroIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "pomodoro")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        provideContent {
            GlanceTheme {
                val context = LocalContext.current
                val isExpanded = LocalSize.current.width >= EXPANDED.width
                
                // Chronometer setup
                val remoteViews = RemoteViews(context.packageName, R.layout.widget_chronometer)
                val base = if (isRunning) {
                    SystemClock.elapsedRealtime() + remainingMs.toLong()
                } else {
                    SystemClock.elapsedRealtime()
                }
                remoteViews.setChronometer(R.id.pomodoro_chronometer, base, null, isRunning)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    remoteViews.setChronometerCountDown(R.id.pomodoro_chronometer, true)
                }

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(48.dp) // ExtraLargeExpressiveShape
                        .clickable(actionStartActivity(openPomodoroIntent)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isExpanded) {
                        ExpandedPomodoroContent(
                            mode = mode,
                            isRunning = isRunning,
                            sessionsDone = sessionsDone,
                            sessionsGoal = sessionsGoal,
                            ringBitmap = ringBitmap,
                            goalBitmap = goalBitmap,
                            chronometerView = remoteViews,
                            remainingMs = remainingMs.toLong()
                        )
                    } else {
                        CompactPomodoroContent(
                            mode = mode,
                            isRunning = isRunning,
                            ringBitmap = ringBitmap,
                            chronometerView = remoteViews,
                            remainingMs = remainingMs.toLong()
                        )
                    }
                }
            }
        }
    }

    private fun buildProgressBitmap(
        progress: Float,
        ringColor: Int,
        trackColor: Int,
        fillColor: Int,
        sizePx: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = sizePx * 0.105f
        val inset = stroke / 2f + sizePx * 0.035f
        val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)

        canvas.drawCircle(
            sizePx / 2f,
            sizePx / 2f,
            sizePx / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            },
        )
        canvas.drawArc(
            bounds,
            -90f,
            360f,
            false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = trackColor
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                alpha = 72
            },
        )
        canvas.drawArc(
            bounds,
            -90f,
            progress * 360f,
            false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            },
        )
        return bitmap
    }
}

fun formatWidgetMillis(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d", (total / 60).toInt(), (total % 60).toInt())
}

@Composable
private fun CompactPomodoroContent(
    mode: String,
    isRunning: Boolean,
    ringBitmap: Bitmap,
    chronometerView: RemoteViews,
    remainingMs: Long
) {
    val actions = rememberPomodoroActions()
    Box(modifier = GlanceModifier.fillMaxSize().padding(9.dp), contentAlignment = Alignment.Center) {
        Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhasePill(mode = mode, isRunning = isRunning)
            Spacer(GlanceModifier.height(7.dp))
            if (isRunning) {
                AndroidRemoteViews(chronometerView)
            } else {
                Text(
                    text = formatWidgetMillis(remainingMs),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(9.dp))
            WidgetIconButton(
                icon = if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                contentDescription = if (isRunning) "Pause Pomodoro" else "Start Pomodoro",
                action = actions.toggle,
                prominent = true,
                mode = mode,
            )
        }
    }
}

@Composable
private fun ExpandedPomodoroContent(
    mode: String,
    isRunning: Boolean,
    sessionsDone: Int,
    sessionsGoal: Int,
    ringBitmap: Bitmap,
    goalBitmap: Bitmap,
    chronometerView: RemoteViews,
    remainingMs: Long
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.size(130.dp), contentAlignment = Alignment.Center) {
            Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                PhasePill(mode = mode, isRunning = isRunning)
                Spacer(GlanceModifier.height(6.dp))
                if (isRunning) {
                    AndroidRemoteViews(chronometerView)
                } else {
                    Text(
                        text = formatWidgetMillis(remainingMs),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(GlanceModifier.width(14.dp))

        Column(
            modifier = GlanceModifier.fillMaxHeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Image(provider = ImageProvider(goalBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                    Text(
                        text = sessionsDone.coerceAtMost(99).toString(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "Daily focus",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = "$sessionsDone of $sessionsGoal sessions",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(GlanceModifier.height(16.dp))

            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val actions = rememberPomodoroActions()
                WidgetIconButton(
                    icon = R.drawable.ic_widget_reset,
                    contentDescription = "Reset Pomodoro",
                    action = actions.reset,
                    prominent = false,
                    mode = mode,
                )
                Spacer(GlanceModifier.width(10.dp))
                WidgetIconButton(
                    icon = if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    contentDescription = if (isRunning) "Pause Pomodoro" else "Start Pomodoro",
                    action = actions.toggle,
                    prominent = true,
                    mode = mode,
                )
                Spacer(GlanceModifier.width(10.dp))
                WidgetIconButton(
                    icon = R.drawable.ic_widget_next,
                    contentDescription = "Skip Pomodoro phase",
                    action = actions.skip,
                    prominent = false,
                    mode = mode,
                )
            }
        }
    }
}

@Composable
private fun PhasePill(mode: String, isRunning: Boolean) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(14.dp)
            .background(if (mode == "WORK") GlanceTheme.colors.primaryContainer else GlanceTheme.colors.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${modeLabel(mode)} • ${if (isRunning) "LIVE" else "READY"}",
            style = TextStyle(
                color = if (mode == "WORK") GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onTertiaryContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetIconButton(
    icon: Int,
    contentDescription: String,
    action: Intent,
    prominent: Boolean,
    mode: String,
) {
    val buttonSize = if (prominent) 50.dp else 42.dp
    val background = when {
        prominent && mode == "WORK" -> GlanceTheme.colors.primary
        prominent -> GlanceTheme.colors.tertiary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val foreground = when {
        prominent && mode == "WORK" -> GlanceTheme.colors.onPrimary
        prominent -> GlanceTheme.colors.onTertiary
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    Box(
        modifier = GlanceModifier
            .size(buttonSize)
            .cornerRadius(buttonSize / 2)
            .background(background)
            .clickable(actionSendBroadcast(action)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(if (prominent) 25.dp else 20.dp),
            colorFilter = ColorFilter.tint(foreground),
        )
    }
}

@Composable
private fun rememberPomodoroActions(): PomodoroWidgetActions {
    val pkg = LocalContext.current.packageName
    val receiver = ComponentName(pkg, "com.frerox.toolz.widget.glance.PomodoroWidgetReceiver")
    return PomodoroWidgetActions(
        toggle = Intent(POMODORO_ACTION_TOGGLE).apply { component = receiver },
        reset = Intent(POMODORO_ACTION_RESET).apply { component = receiver },
        skip = Intent(POMODORO_ACTION_SKIP).apply { component = receiver },
    )
}

private data class PomodoroWidgetActions(
    val toggle: Intent,
    val reset: Intent,
    val skip: Intent,
)

private data class PomodoroWidgetPalette(
    val accent: Int,
    val secondary: Int,
    val track: Int,
    val surface: Int,
    val surfaceVariant: Int,
) {
    companion object {
        fun resolve(context: Context, mode: String): PomodoroWidgetPalette {
            val accent = if (Build.VERSION.SDK_INT >= 31) {
                context.resources.getColor(
                    if (mode == "WORK") android.R.color.system_accent1_200 else android.R.color.system_accent3_200,
                    context.theme,
                )
            } else if (mode == "WORK") {
                0xFFD0BCFF.toInt()
            } else {
                0xFFEFB8C8.toInt()
            }
            val secondary = if (Build.VERSION.SDK_INT >= 31) {
                context.resources.getColor(android.R.color.system_accent2_200, context.theme)
            } else {
                0xFFCCC2DC.toInt()
            }
            val track = if (Build.VERSION.SDK_INT >= 31) {
                context.resources.getColor(android.R.color.system_neutral2_700, context.theme)
            } else {
                0xFF49454F.toInt()
            }
            val surface = if (Build.VERSION.SDK_INT >= 31) {
                context.resources.getColor(android.R.color.system_neutral1_900, context.theme)
            } else {
                0xFF1C1B1F.toInt()
            }
            val surfaceVariant = if (Build.VERSION.SDK_INT >= 31) {
                context.resources.getColor(android.R.color.system_neutral2_800, context.theme)
            } else {
                0xFF49454F.toInt()
            }
            return PomodoroWidgetPalette(accent, secondary, track, surface, surfaceVariant)
        }
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "SHORT_BREAK" -> "SHORT"
    "LONG_BREAK" -> "LONG"
    else -> "FOCUS"
}

const val POMODORO_ACTION_TOGGLE = "com.frerox.toolz.WIDGET_POMO_TOGGLE"
const val POMODORO_ACTION_RESET = "com.frerox.toolz.WIDGET_POMO_RESET"
const val POMODORO_ACTION_SKIP = "com.frerox.toolz.WIDGET_POMO_SKIP"
