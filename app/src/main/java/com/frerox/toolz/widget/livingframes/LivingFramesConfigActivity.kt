/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.widget.livingframes

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.worker.LivingFramesRenderWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Required by android:configure. Binding the exact appWidgetId here is what
 * makes "pin set A to this home-screen slot" deterministic; the old flow
 * scanned for any unbound widget and could attach the newest set instead.
 */
class LivingFramesConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val expectedProvider = ComponentName(this, LivingFramesWidgetReceiver::class.java)
        val actualProvider = runCatching {
            AppWidgetManager.getInstance(this)?.getAppWidgetInfo(appWidgetId)?.provider
        }.getOrNull()
        if (actualProvider != null) {
            if (actualProvider != expectedProvider) {
                finish()
                return
            }
        } else {
            // The configure activity is exported, so a caller could invent an
            // ID. When provider metadata is unavailable, require the ID to be
            // one of the currently bound Living Widgets instead.
            val liveIds = LivingFramesWidgetHelper.allWidgetIdsOrNull(this)
            if (liveIds == null || appWidgetId !in liveIds) {
                finish()
                return
            }
        }
        setResult(Activity.RESULT_CANCELED)

        val repo = EntryPointAccessors.fromApplication(
            applicationContext,
            LivingFramesEntryPoint::class.java
        ).livingFramesRepository()

        setContent {
            ToolzTheme {
                ConfigScreen(
                    repo = repo,
                    appWidgetId = appWidgetId,
                    onCancel = { finish() },
                    onUse = { frame ->
                        if (!repo.bindWidget(appWidgetId, frame.id)) {
                            false
                        } else {
                            LivingFramesSlideshow.cancel(applicationContext, appWidgetId)
                            // The old set's pause point must not freeze the
                            // new set on an unrelated frame.
                            LivingFramesPause.clear(applicationContext, appWidgetId)
                            LivingFramesWidgetHelper.clearPendingPin(applicationContext)
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(
                                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                                    appWidgetId
                                )
                            )
                            LivingFramesRenderWorker.enqueue(applicationContext, appWidgetId)
                            finish()
                            true
                        }
                    },
                    onOpenToolz = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                putExtra(
                                    MainActivity.EXTRA_NAVIGATE_TO,
                                    Screen.LivingWidgets.route
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    repo: LivingFramesRepository,
    appWidgetId: Int,
    onCancel: () -> Unit,
    onUse: (LivingFrame) -> Boolean,
    onOpenToolz: () -> Unit
) {
    var frames by remember { mutableStateOf<List<LivingFrame>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    // Only file references: Coil decodes the 64 dp tiles lazily and recycles
    // them with the composition instead of holding every cover in memory.
    var covers by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
    var selected by remember { mutableStateOf<LivingFrame?>(null) }
    var bindFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.ensureLoaded()
        loadFailed = repo.isLoadFailed()
        val loaded = repo.snapshot()
        frames = loaded
        // Read the binding only after the repository has loaded. Before that,
        // a reconfiguration after process death looked unbound and showed no
        // selected set even though the widget was still attached.
        selected = loaded.firstOrNull { it.id == repo.boundFrame(appWidgetId) }
        covers = withContext(Dispatchers.IO) {
            loaded.associate { frame ->
                val cover = repo.coverFile(frame.id)
                frame.id to (if (cover.isFile) cover else repo.frameFile(frame.id, 0))
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                stringResource(R.string.lw_config_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.lw_config_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(18.dp))

            val list = frames
            if (list == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(
                                if (loadFailed) R.string.lw_config_load_failed
                                else R.string.lw_config_empty
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(
                                if (loadFailed) R.string.lw_config_load_failed_body
                                else R.string.lw_config_empty_body
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TextButton(onClick = onOpenToolz, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.lw_open_living_widgets))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { frame ->
                        ConfigRow(
                            frame = frame,
                            cover = covers[frame.id],
                            selected = selected?.id == frame.id,
                            onClick = {
                                bindFailed = false
                                selected = frame
                            }
                        )
                    }
                }
            }

            if (bindFailed) {
                Text(
                    stringResource(R.string.lw_config_bind_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.lw_cancel)) }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        selected?.let { frame ->
                            bindFailed = !onUse(frame)
                        }
                    },
                    enabled = selected != null
                ) {
                    Text(stringResource(R.string.lw_config_use))
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(
    frame: LivingFrame,
    cover: File?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("◐", style = MaterialTheme.typography.headlineMedium)
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = frame.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                frame.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                configSummary(frame),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Text(
                stringResource(R.string.lw_selected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun configSummary(frame: LivingFrame): String = when (frame.type) {
    LivingFrameType.GIF -> stringResource(
        R.string.lw_config_summary,
        stringResource(R.string.lw_type_gif),
        pluralStringResource(
            R.plurals.lw_frames_count,
            frame.frameCount,
            frame.frameCount
        )
    )
    LivingFrameType.VIDEO -> stringResource(
        R.string.lw_config_summary,
        stringResource(R.string.lw_type_video),
        pluralStringResource(
            R.plurals.lw_frames_count,
            frame.frameCount,
            frame.frameCount
        )
    )
    LivingFrameType.PHOTO -> stringResource(R.string.lw_type_photo)
    LivingFrameType.SLIDESHOW -> stringResource(
        R.string.lw_config_summary,
        stringResource(R.string.lw_type_slideshow),
        pluralStringResource(
            R.plurals.lw_photos_count,
            frame.frameCount,
            frame.frameCount
        )
    )
}
