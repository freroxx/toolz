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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.worker.LivingFramesRenderWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    initialId = repo.boundFrame(appWidgetId),
                    onCancel = { finish() },
                    onUse = { frame ->
                        lifecycleScope.launch {
                            repo.bindWidget(appWidgetId, frame.id)
                            LivingFramesSlideshow.cancel(applicationContext, appWidgetId)
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
    initialId: String?,
    onCancel: () -> Unit,
    onUse: (LivingFrame) -> Unit,
    onOpenToolz: () -> Unit
) {
    var frames by remember { mutableStateOf<List<LivingFrame>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var covers by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var selected by remember { mutableStateOf<LivingFrame?>(null) }

    LaunchedEffect(Unit) {
        repo.ensureLoaded()
        loadFailed = repo.isLoadFailed()
        val loaded = repo.snapshot()
        frames = loaded
        selected = loaded.firstOrNull { it.id == initialId }
        covers = withContext(Dispatchers.IO) {
            loaded.mapNotNull { frame ->
                repo.decodeAtLeast(frame.id, 0, 320)
                    ?.let { bmp -> frame.id to bmp.asImageBitmap() }
            }.toMap()
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
                "Configure Living Widget",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Pick the set this home-screen widget should show. You can pin more copies later.",
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
                            if (loadFailed) "Couldn't read the library" else "No Living Widget sets yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (loadFailed) {
                                "The saved files are still on disk. Open Toolz, use “Try loading again”, then add this widget again."
                            } else {
                                "Create a GIF, video or photo set in Toolz, then add this widget again."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TextButton(onClick = onOpenToolz, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Open Living Widgets")
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
                            onClick = { selected = frame }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { selected?.let(onUse) },
                    enabled = selected != null
                ) {
                    Text("Use this set")
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(
    frame: LivingFrame,
    cover: ImageBitmap?,
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
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = frame.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("◐", style = MaterialTheme.typography.headlineMedium)
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
                when (frame.type) {
                    com.frerox.toolz.data.livingframes.LivingFrameType.GIF -> "GIF · ${frame.frameCount} frames"
                    com.frerox.toolz.data.livingframes.LivingFrameType.VIDEO -> "Video · ${frame.frameCount} frames"
                    com.frerox.toolz.data.livingframes.LivingFrameType.PHOTO -> "Photo"
                    com.frerox.toolz.data.livingframes.LivingFrameType.SLIDESHOW -> "Slideshow · ${frame.frameCount} photos"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Text(
                "Selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
