package com.frerox.toolz.ui.screens.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.frerox.toolz.ui.theme.ToolzTheme
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────
//  Intent key constants — used by both the alarm broadcaster
//  and by CalendarAlarmScheduler when building the PendingIntent
// ─────────────────────────────────────────────────────────────
object AlarmIntentKeys {
    const val EVENT_ID    = "event_id"
    const val EVENT_TITLE = "event_title"
    const val EVENT_TIME  = "event_time"
}

/** Snooze durations available to the user (minutes). */
private val SNOOZE_OPTIONS = listOf(5, 10, 15, 30)

class EventAlarmActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Make the activity show over the lock screen and wake the display ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Keep screen on while alarm is visible
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Acquire a WakeLock so CPU stays alive long enough to render the UI
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "toolz:AlarmWakeLock"
        ).also { it.acquire(60_000L /* 60 s max */ ) }

        // Edge-to-edge so the gradient fills the entire screen
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val eventId    = intent.getIntExtra(AlarmIntentKeys.EVENT_ID, -1)
        val eventTitle = intent.getStringExtra(AlarmIntentKeys.EVENT_TITLE) ?: "Scheduled Event"
        val eventTime  = intent.getLongExtra(AlarmIntentKeys.EVENT_TIME, System.currentTimeMillis())

        // Back press → same as Dismiss
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { dismissAlarm() }
        })

        setContent {
            ToolzTheme {
                AlarmScreen(
                    eventTitle = eventTitle,
                    eventTime  = eventTime,
                    onDismiss  = { dismissAlarm() },
                    onSnooze   = { minutes -> snoozeAlarm(eventId, eventTitle, eventTime, minutes) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    private fun dismissAlarm() {
        releaseWakeLock()
        finish()
    }

    /**
     * Schedules a new one-shot alarm [minutes] from now with the same
     * event data, then dismisses this instance.
     */
    private fun snoozeAlarm(eventId: Int, title: String, originalTime: Long, minutes: Int) {
        val snoozeTime = System.currentTimeMillis() + (minutes * 60_000L)

        val intent = Intent(this, EventAlarmActivity::class.java).apply {
            putExtra(AlarmIntentKeys.EVENT_ID,    eventId)
            putExtra(AlarmIntentKeys.EVENT_TITLE, title)
            putExtra(AlarmIntentKeys.EVENT_TIME,  originalTime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            // Use a unique request code per snooze to avoid clobbering other alarms
            (eventId * 1000 + minutes),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
        }

        dismissAlarm()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}

// ─────────────────────────────────────────────────────────────
//  Alarm Screen UI
// ─────────────────────────────────────────────────────────────

@Composable
fun AlarmScreen(
    eventTitle: String,
    eventTime: Long,
    onDismiss: () -> Unit,
    onSnooze: (minutes: Int) -> Unit,
) {
    var showSnoozeOptions by remember { mutableStateOf(false) }

    val formattedTime = remember(eventTime) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(eventTime))
    }
    val formattedDate = remember(eventTime) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(eventTime))
    }

    // Pulsing animation for the bell icon ring
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue  = 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF1A0533),
                        0.5f to Color(0xFF2D1060),
                        1.0f to Color(0xFF3D1A7A),
                    )
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {

            // ── Top section: visual ───────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Time display
                Text(
                    text  = formattedTime,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 80.sp,
                    letterSpacing = (-2).sp
                )
                Text(
                    text  = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal
                )

                Spacer(Modifier.height(48.dp))

                // Animated bell icon with pulsing rings
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulse ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(ringScale)
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                    )
                    // Middle ring
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(ringScale * 0.95f)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    )
                    // Inner fill
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF7C4DFF).copy(alpha = 0.8f),
                                        Color(0xFF651FFF).copy(alpha = 0.9f),
                                    )
                                ),
                                CircleShape
                            )
                    )
                    // Bell icon
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .scale(iconScale),
                        tint = Color.White
                    )
                }

                Spacer(Modifier.height(40.dp))

                // Label
                Text(
                    text  = "REMINDER",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFBB86FC),
                    letterSpacing = 4.sp
                )

                Spacer(Modifier.height(12.dp))

                // Event title
                Text(
                    text      = eventTitle,
                    style     = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color     = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )
            }

            // ── Bottom section: actions ───────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Snooze options (expanded on demand)
                if (showSnoozeOptions) {
                    Surface(
                        color  = Color.White.copy(alpha = 0.08f),
                        shape  = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Snooze for…",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SNOOZE_OPTIONS.forEach { minutes ->
                                    OutlinedButton(
                                        onClick = { onSnooze(minutes) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, Color.White.copy(alpha = 0.25f)
                                        )
                                    ) {
                                        Text(
                                            "${minutes}m",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Snooze toggle button
                OutlinedButton(
                    onClick  = { showSnoozeOptions = !showSnoozeOptions },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape    = RoundedCornerShape(18.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border   = androidx.compose.foundation.BorderStroke(
                        1.5.dp, Color.White.copy(alpha = 0.25f)
                    )
                ) {
                    Icon(
                        Icons.Rounded.Snooze, null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (showSnoozeOptions) "Hide Snooze Options" else "Snooze",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                }

                // Dismiss button
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape    = RoundedCornerShape(18.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = Color(0xFF3D1A7A)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Dismiss",
                        fontWeight = FontWeight.Black,
                        fontSize   = 18.sp
                    )
                }

                // Safe area bottom padding
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}