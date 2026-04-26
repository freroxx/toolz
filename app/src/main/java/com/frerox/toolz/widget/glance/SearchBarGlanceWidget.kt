package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R

// ---------------------------------------------------------------------------
//  Quick Search Bar — Glance Widget  (4×1 pill)
// ---------------------------------------------------------------------------

class SearchBarGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_NORMAL = DpSize(260.dp, 56.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SIZE_NORMAL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val openSearchIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "search")
            putExtra("auto_focus_search", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val voiceIntent = Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        }

        provideContent {
            GlanceTheme {
                SearchBarContent(openSearchIntent = openSearchIntent, voiceIntent = voiceIntent)
            }
        }
    }
}

@Composable
private fun SearchBarContent(openSearchIntent: Intent, voiceIntent: Intent) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(32.dp) // Premium rounded pill
            .clickable(actionStartActivity(openSearchIntent)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Search icon badge - replaced "Z" with search icon
            Box(
                modifier = GlanceModifier.size(34.dp).cornerRadius(10.dp)
                    .background(GlanceTheme.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_search),
                    contentDescription = "Search",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary)
                )
            }

            Spacer(GlanceModifier.width(16.dp))

            // Hint text (fills remaining space)
            Text(
                text = "Search with Toolz…",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium),
            )

            Spacer(GlanceModifier.width(8.dp))

            // Vertical divider - more subtle
            Box(modifier = GlanceModifier.width(1.dp).height(20.dp)
                    .background(GlanceTheme.colors.outline)) {}

            Spacer(GlanceModifier.width(12.dp))

            // Mic button - refined
            Box(
                modifier = GlanceModifier.size(40.dp).cornerRadius(20.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(actionStartActivity(voiceIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Image(provider = ImageProvider(R.drawable.ic_widget_mic),
                    contentDescription = "Voice Search",
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer))
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Receiver
// ---------------------------------------------------------------------------

class SearchBarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SearchBarGlanceWidget()
}
