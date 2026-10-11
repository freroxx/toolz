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

package com.frerox.toolz.ui.screens.livingframes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLagLevel
import com.frerox.toolz.data.livingframes.LivingFrameLagMeter
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveLoadingIndicator
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch
import java.io.File

private val CardShape = SquircleShape
private const val PREVIEW_MAX_FRAMES = 32

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LivingWidgetsScreen(
    onBack: () -> Unit,
    viewModel: LivingFramesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val ui by viewModel.ui.collectAsState()
    val status by viewModel.status.collectAsState()
    val diag by viewModel.diagnostics.collectAsState()
    val saverOn by viewModel.saverOn.collectAsState()
    val shizuku by viewModel.shizuku.collectAsState()
    val busyMessage by viewModel.busyMessage.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var previewPlaying by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val openBatterySettings = rememberOpenBatterySettings()
    val selected = viewModel.selected()

    LaunchedEffect(ui.importError) {
        ui.importError?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(ui.importSuccess) {
        ui.importSuccess?.let { snackbar.showSnackbar(it) }
    }

    val importFps = selected?.fps ?: LivingFrameFps.DEFAULT
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importGif(uri, importFps)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importVideo(uri, importFps)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(uri)
    }
    val slideshowPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.importSlideshow(uris)
    }

    fun pick(mode: LivingFrameType) {
        vibrationManager?.vibrateClick()
        viewModel.clearMessages()
        when (mode) {
            LivingFrameType.GIF -> gifPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            LivingFrameType.VIDEO -> videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
            LivingFrameType.PHOTO -> photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            LivingFrameType.SLIDESHOW -> slideshowPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this widget?", fontWeight = FontWeight.Black) },
            text = { Text("“${selected.name}” and its ${selected.frameCount} frames will be removed. Home-screen widgets using it go back to a placeholder.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vibrationManager?.vibrateLongClick()
                        viewModel.delete(selected.id)
                        scope.launch { snackbar.showSnackbar("Widget deleted") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            }
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Living Widgets",
                subtitle = "GIFs, video and photos on your home screen",
                navigationIcon = {
                    IconButton(
                        onClick = { vibrationManager?.vibrateClick(); onBack() },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 12.dp, bottom = 24.dp))
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (saverOn) {
                item {
                    StaggeredEntrance(index = 0) {
                        SaverBanner(
                            shizukuRunning = shizuku.running,
                            shizukuAuthorized = shizuku.authorized,
                            busyMessage = busyMessage,
                            onEnableShizuku = { viewModel.requestShizukuAuth() },
                            onDisableSaver = {
                                viewModel.disableSaver { err ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            err ?: "Battery Saver is off — widgets resuming"
                                        )
                                    }
                                }
                            },
                            onOpenSettings = { openBatterySettings() }
                        )
                    }
                }
            }
            item {
                StaggeredEntrance(index = 0) {
                    PlaybackStatusRow(
                        status = status,
                        diag = diag,
                        onResume = {
                            vibrationManager?.vibrateClick()
                            viewModel.startPlayback()
                        }
                    )
                }
            }

            if (selected != null) {
                item {
                    StaggeredEntrance(index = 1) {
                        PreviewCard(
                            frame = selected,
                            playing = previewPlaying,
                            onPlayingChange = { previewPlaying = it },
                            onRename = { viewModel.setName(selected.id, it) }
                        )
                    }
                }
                item {
                    StaggeredEntrance(index = 2) {
                        WidgetsStrip(
                            frames = ui.frames,
                            selectedId = selected.id,
                            onSelect = {
                                vibrationManager?.vibrateTick()
                                viewModel.select(it)
                                previewPlaying = true
                            }
                        )
                    }
                }
                item {
                    StaggeredEntrance(index = 3) {
                        if (selected.type == LivingFrameType.SLIDESHOW) {
                            SlideshowCard(
                                frame = selected,
                                nextLabel = viewModel.slideNextLabel(selected.id),
                                onInterval = { viewModel.setSlideshowInterval(selected.id, it) },
                                onShuffle = { viewModel.setShuffle(selected.id, it) }
                            )
                        } else {
                            SpeedCard(
                                frame = selected,
                                onFpsChange = { viewModel.setFps(selected.id, it) }
                            )
                        }
                    }
                }
                item {
                    StaggeredEntrance(index = 4) {
                        WidgetSettingsCard(
                            frame = selected,
                            onLoop = { viewModel.setLoopMode(selected.id, it) },
                            onFit = { viewModel.setFit(selected.id, it) },
                            onTap = { viewModel.setTapAction(selected.id, it) },
                            onOpacity = { viewModel.setOpacity(selected.id, it) },
                            onCorner = { viewModel.setCorner(selected.id, it) },
                            onCaption = { viewModel.setShowCaption(selected.id, it) }
                        )
                    }
                }
                item {
                    StaggeredEntrance(index = 5) {
                        PinAndDeleteRow(
                            frame = selected,
                            boundCount = viewModel.boundWidgetCount(selected.id),
                            onPin = {
                                vibrationManager?.vibrateLongClick()
                                val ok = viewModel.pinToHome(selected)
                                if (!ok) {
                                    Toast.makeText(
                                        context,
                                        "Your launcher blocked pinning — long-press the home screen, tap Widgets, then add Toolz Living Widget.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onDelete = { confirmDelete = true },
                            onTestPush = {
                                vibrationManager?.vibrateClick()
                                viewModel.testPush(selected.id) { msg ->
                                    scope.launch { snackbar.showSnackbar(msg) }
                                }
                            }
                        )
                    }
                }
            } else if (!ui.isImporting) {
                item {
                    StaggeredEntrance(index = 1) {
                        EmptyCard()
                    }
                }
            }

            item {
                StaggeredEntrance(index = 6) {
                    ImportGrid(
                        importing = ui.isImporting,
                        onPick = ::pick
                    )
                }
            }

            item {
                Footnote()
            }
        }
    }
}

@Composable
private fun rememberOpenBatterySettings(): () -> Unit {
    val context = LocalContext.current
    return remember {
        {
            try {
                val intent = try {
                    android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
                } catch (_: Exception) {
                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                }
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    Toast.makeText(context, "Open Settings → Battery → Battery Saver", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            }
        }
    }
}

// ── Saver banner ─────────────────────────────────────────────────────────

@Composable
private fun SaverBanner(
    shizukuRunning: Boolean,
    shizukuAuthorized: Boolean,
    busyMessage: String?,
    onEnableShizuku: () -> Unit,
    onDisableSaver: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Battery Saver is pausing your widgets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Animation and slideshows stay frozen while it is on. Turn it off to resume.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(12.dp))
            if (busyMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveLoadingIndicator()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        busyMessage,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else if (shizukuAuthorized) {
                ToolzExpressiveButton(
                    onClick = onDisableSaver,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Turn Battery Saver off", fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(8.dp))
                ToolzOutlinedExpressiveButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open battery settings instead")
                }
            } else {
                ToolzExpressiveButton(
                    onClick = if (shizukuRunning) onEnableShizuku else onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        if (shizukuRunning) "Authorize Shizuku to turn it off" else "Open battery settings",
                        fontWeight = FontWeight.Black
                    )
                }
                if (shizukuRunning) {
                    Spacer(Modifier.height(8.dp))
                    ToolzOutlinedExpressiveButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open settings manually")
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tip: with Shizuku running, Toolz can turn it off for you.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ── Status ───────────────────────────────────────────────────────────────

@Composable
private fun PlaybackStatusRow(
    status: LivingFramesWidgetHelper.PlaybackStatus,
    diag: Map<Int, String>,
    onResume: () -> Unit
) {
    val animating = status.animating
    val widgetCount = status.widgetCount
    val dot = when {
        widgetCount == 0 -> MaterialTheme.colorScheme.outline
        animating -> Color(0xFF43A047)
        else -> Color(0xFFFFA000)
    }
    val text = when {
        widgetCount == 0 -> "No widgets pinned yet"
        animating -> "Animating · $widgetCount widget${if (widgetCount == 1) "" else "s"}"
        status.pausedReason == "SAVER" -> "Paused · Battery saver is ON"
        status.pausedReason == "SCREEN_OFF" -> "Paused · screen is off"
        else -> "Paused · $widgetCount widget${if (widgetCount == 1) "" else "s"} — tap resume"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (widgetCount > 0 && !animating) {
                    TextButton(onClick = onResume) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Resume", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (diag.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                diag.entries.sortedBy { it.key }.take(3).forEach { (id, info) ->
                    Text(
                        "Widget $id → frame $info",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Preview (vsync-driven, preloaded — no disk reads per tick) ───────────

@Composable
private fun PreviewCard(
    frame: LivingFrame,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    viewModel: LivingFramesViewModel = hiltViewModel()
) {
    var measuredFps by remember { mutableFloatStateOf(-1f) }
    var measuredDropped by remember { mutableIntStateOf(0) }
    var browse by remember(frame.id) { mutableIntStateOf(-1) }
    var showRename by remember(frame.id) { mutableStateOf(false) }
    val multi = frame.frameCount > 1
    val isSlideshow = frame.type == LivingFrameType.SLIDESHOW

    if (showRename) {
        var draft by remember(frame.id) { mutableStateOf(frame.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename widget", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(40) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRename = false
                    if (draft.isNotBlank()) onRename(draft.trim())
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            }
        )
    }

    ExpressiveCard(
        onClick = {},
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        enabled = false
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    frame.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showRename = true }) {
                    Text("Rename", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                if (multi && !isSlideshow) {
                    IconButton(onClick = { onPlayingChange(!playing) }) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause preview" else "Play preview"
                        )
                    }
                } else if (isSlideshow && multi) {
                    Row {
                        TextButton(onClick = {
                            browse = if (browse < 0) frame.frameCount - 1
                            else (browse - 1 + frame.frameCount) % frame.frameCount
                        }) { Text("Prev", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = {
                            browse = if (browse < 0) 1 % frame.frameCount
                            else (browse + 1) % frame.frameCount
                        }) { Text("Next", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypePill(frame.type)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !multi -> "Static · ${frame.width}×${frame.height}px"
                        isSlideshow && browse >= 0 -> "Photo ${browse + 1}/${frame.frameCount}"
                        isSlideshow -> "Slideshow · ${frame.frameCount} photos"
                        !playing -> "Paused · ${frame.frameCount} frames"
                        measuredFps < 0 -> "Measuring…"
                        else -> "%.1f fps · %d dropped".format(measuredFps, measuredDropped)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            FramePlayer(
                frame = frame,
                playing = playing && browse < 0,
                forcedIndex = browse.takeIf { it >= 0 },
                onStats = { fps, dropped ->
                    measuredFps = fps
                    measuredDropped = dropped
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(frame.cornerRadiusDp.dp))
            )
        }
    }
}

@Composable
private fun TypePill(type: LivingFrameType) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            when (type) {
                LivingFrameType.GIF -> "GIF"
                LivingFrameType.VIDEO -> "Video"
                LivingFrameType.PHOTO -> "Photo"
                LivingFrameType.SLIDESHOW -> "Slideshow"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun FramePlayer(
    frame: LivingFrame,
    playing: Boolean,
    onStats: (Float, Int) -> Unit,
    modifier: Modifier = Modifier,
    forcedIndex: Int? = null,
    viewModel: LivingFramesViewModel = hiltViewModel()
) {
    var bitmaps by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(frame.id, reloadKey) {
        bitmaps = emptyList()
        failed = false
        val loaded = try {
            viewModel.decodePreviewFrames(frame.id)
                .take(PREVIEW_MAX_FRAMES)
                .map { it.asImageBitmap() }
        } catch (_: Exception) { emptyList() }
        if (loaded.isEmpty()) failed = true else bitmaps = loaded
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            failed -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load preview", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { reloadKey++ }) { Text("Retry") }
                }
            }
            bitmaps.isEmpty() -> ExpressiveLoadingIndicator()
            forcedIndex != null -> {
                Image(
                    bitmap = bitmaps[forcedIndex % bitmaps.size],
                    contentDescription = frame.name,
                    modifier = Modifier.fillMaxSize().alpha(frame.opacity),
                    contentScale = if (frame.fit == LivingFrameFit.FILL) ContentScale.Crop else ContentScale.Fit
                )
            }
            bitmaps.size == 1 || !playing -> {
                Image(
                    bitmap = bitmaps[0],
                    contentDescription = frame.name,
                    modifier = Modifier.fillMaxSize().alpha(frame.opacity),
                    contentScale = if (frame.fit == LivingFrameFit.FILL) ContentScale.Crop else ContentScale.Fit
                )
                if (playing) {
                    // Static single frame: report nominal rate once.
                    LaunchedEffect(frame.id) { onStats(frame.fps.toFloat(), 0) }
                }
            }
            else -> VsyncImage(
                bitmaps = bitmaps,
                fps = frame.fps,
                fit = frame.fit,
                opacity = frame.opacity,
                contentDescription = frame.name,
                onStats = onStats,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Advances preloaded bitmaps on the display vsync clock and reports the real
 * measured rate once per second. Only this composable recomposes per tick.
 */
@Composable
private fun VsyncImage(
    bitmaps: List<ImageBitmap>,
    fps: Int,
    fit: LivingFrameFit,
    opacity: Float,
    contentDescription: String?,
    onStats: (Float, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(bitmaps, fps) {
        val intervalNs = 1_000_000_000L / fps.coerceAtLeast(1)
        var last = withFrameNanos { it }
        var acc = 0L
        var shown = 0
        var dropped = 0
        var windowStart = last
        while (true) {
            val t = withFrameNanos { it }
            val dt = (t - last).coerceAtLeast(0L)
            last = t
            acc += dt
            if (acc >= intervalNs) {
                val steps = (acc / intervalNs).toInt()
                if (steps > 1) dropped += steps - 1
                tick = (tick + 1) % bitmaps.size
                acc %= intervalNs
                shown++
            }
            if (t - windowStart >= 1_000_000_000L) {
                onStats(shown.toFloat(), dropped)
                shown = 0
                dropped = 0
                windowStart = t
            }
        }
    }
    Image(
        bitmap = bitmaps[tick % bitmaps.size],
        contentDescription = contentDescription,
        modifier = modifier.alpha(opacity),
        contentScale = if (fit == LivingFrameFit.FILL) ContentScale.Crop else ContentScale.Fit
    )
}

// ── Frames strip ─────────────────────────────────────────────────────────

@Composable
private fun WidgetsStrip(
    frames: List<LivingFrame>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            "MY WIDGETS · ${frames.size}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalFadingEdges(left = 24.dp, right = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(frames, key = { it.id }) { frame ->
                WidgetThumb(
                    frame = frame,
                    selected = frame.id == selectedId,
                    onClick = { onSelect(frame.id) }
                )
            }
        }
    }
}

@Composable
private fun WidgetThumb(
    frame: LivingFrame,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cover = remember(frame.id) {
        File(File(context.filesDir, "living_frames/${frame.id}"), "cover.webp")
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.size(132.dp, 148.dp)
    ) {
        Column {
            Box(Modifier.weight(1f)) {
                AsyncImage(
                    model = cover,
                    contentDescription = frame.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentScale = ContentScale.Crop
                )
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Text(
                frame.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Text(
                "${frame.frameCount}f · ${frame.fps}fps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    ExpressiveCard(
        onClick = {},
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        enabled = false
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Add, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("No widgets yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                "Pick a type below to create your first home-screen widget.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Import grid ──────────────────────────────────────────────────────────

private data class ImportMode(
    val type: LivingFrameType,
    val icon: ImageVector,
    val label: String,
    val sub: String
)

@Composable
private fun ImportGrid(importing: Boolean, onPick: (LivingFrameType) -> Unit) {
    val modes = remember {
        listOf(
            ImportMode(LivingFrameType.GIF, Icons.Rounded.AutoAwesome, "GIF", "Loop a GIF"),
            ImportMode(LivingFrameType.VIDEO, Icons.Rounded.Movie, "Video", "Silent loop ≤10s"),
            ImportMode(LivingFrameType.PHOTO, Icons.Rounded.Image, "Photo", "Single frame"),
            ImportMode(LivingFrameType.SLIDESHOW, Icons.Rounded.PhotoLibrary, "Slideshow", "Up to 20 photos")
        )
    }
    Column {
        Text(
            "CREATE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            modes.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { mode ->
                        ExpressiveCard(
                            onClick = { if (!importing) onPick(mode.type) },
                            shape = RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            enabled = !importing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(mode.icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text(mode.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    mode.sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (importing) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveLoadingIndicator()
                Spacer(Modifier.width(12.dp))
                Text("Decoding frames…", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Everything stays on your device. Clips become silent frame loops — Android widgets can't play sound.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

// ── Speed ────────────────────────────────────────────────────────────────

@Composable
private fun SlideshowCard(
    frame: LivingFrame,
    nextLabel: String?,
    onInterval: (Int) -> Unit,
    onShuffle: (Boolean) -> Unit
) {
    ExpressiveCard(
        onClick = {},
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        enabled = false
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "SLIDESHOW · ${frame.frameCount} PHOTOS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Change photo every", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            ToolzConnectedButtonGroup(
                selectedIndex = LivingFramesWidgetHelper.SLIDESHOW_INTERVALS_MIN
                    .indexOf(frame.slideshowIntervalMin).takeIf { it >= 0 } ?: 2,
                options = LivingFramesWidgetHelper.SLIDESHOW_INTERVALS_MIN
                    .map { LivingFramesWidgetHelper.slideshowIntervalLabel(it) },
                onOptionSelected = { onInterval(LivingFramesWidgetHelper.SLIDESHOW_INTERVALS_MIN[it]) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            CustomIntervalStepper(
                currentMins = frame.slideshowIntervalMin,
                onInterval = onInterval
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = frame.shuffle,
                    onClick = { onShuffle(!frame.shuffle) },
                    label = { Text("Shuffle", fontWeight = FontWeight.Bold) },
                    leadingIcon = if (frame.shuffle) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (nextLabel != null) "Next photo $nextLabel" else "Pin the widget to start the rotation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Runs on timed alarms — no battery drain between changes. May arrive late in deep sleep.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CustomIntervalStepper(currentMins: Int, onInterval: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onInterval((currentMins - 5).coerceAtLeast(5)) }) {
            Text("− 5m", fontWeight = FontWeight.Black)
        }
        Text(
            "Every ${LivingFramesWidgetHelper.slideshowIntervalLabel(currentMins)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(onClick = { onInterval((currentMins + 5).coerceAtMost(1440)) }) {
            Text("+ 5m", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SpeedCard(frame: LivingFrame, onFpsChange: (Int) -> Unit) {
    val estimate = remember(frame.fps, frame.avgFrameBytes) {
        LivingFrameLagMeter.estimate(frame.fps, frame.avgFrameBytes.coerceAtLeast(20_000L))
    }
    ExpressiveCard(
        onClick = {},
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        enabled = false
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "SPEED · ${frame.fps} FPS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            ToolzConnectedButtonGroup(
                selectedIndex = LivingFrameFps.PRESETS.indexOf(frame.fps),
                options = LivingFrameFps.PRESETS.map { "$it" },
                onOptionSelected = { onFpsChange(LivingFrameFps.PRESETS[it]) },
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = frame.fps.toFloat(),
                onValueChange = { onFpsChange(it.toInt().coerceIn(LivingFrameFps.MIN, LivingFrameFps.MAX)) },
                valueRange = LivingFrameFps.MIN.toFloat()..LivingFrameFps.MAX.toFloat(),
                steps = LivingFrameFps.MAX - LivingFrameFps.MIN - 1,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            if (frame.fps > LivingFrameFps.EXPERIMENTAL_THRESHOLD) {
                Text(
                    "Above 15 fps is experimental — most launchers throttle it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }
            val dot = when (estimate.level) {
                LivingFrameLagLevel.FINE -> Color(0xFF43A047)
                LivingFrameLagLevel.MAY_LAG -> Color(0xFFFFA000)
                LivingFrameLagLevel.LIKELY_LAG -> MaterialTheme.colorScheme.error
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = dot.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, dot.copy(alpha = 0.35f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${estimate.headline} · ~${estimate.kilobytesPerSecond}KB/s",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            estimate.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Settings ─────────────────────────────────────────────────────────────

@Composable
private fun WidgetSettingsCard(
    frame: LivingFrame,
    onLoop: (LivingFrameLoopMode) -> Unit,
    onFit: (LivingFrameFit) -> Unit,
    onTap: (LivingFrameTapAction) -> Unit,
    onOpacity: (Float) -> Unit,
    onCorner: (Int) -> Unit,
    onCaption: (Boolean) -> Unit
) {
    ExpressiveCard(
        onClick = {},
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        enabled = false
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "WIDGET SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            if (frame.frameCount > 1 && frame.type != LivingFrameType.SLIDESHOW) {
                SettingGroup("Loop") {
                    ToolzConnectedButtonGroup(
                        selectedIndex = frame.loopMode.ordinal,
                        options = listOf("Loop", "Boomerang", "Once"),
                        onOptionSelected = { onLoop(LivingFrameLoopMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            SettingGroup("Fill") {
                ToolzConnectedButtonGroup(
                    selectedIndex = if (frame.fit == LivingFrameFit.FILL) 0 else 1,
                    options = listOf("Fill", "Fit"),
                    onOptionSelected = { onFit(if (it == 0) LivingFrameFit.FILL else LivingFrameFit.FIT) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SettingGroup("Tap opens") {
                ToolzConnectedButtonGroup(
                    selectedIndex = frame.tapAction.ordinal,
                    options = listOf("App", "Next", "Off"),
                    onOptionSelected = { onTap(LivingFrameTapAction.entries[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SettingGroup("Name tag") {
                FilterChip(
                    selected = frame.showCaption,
                    onClick = { onCaption(!frame.showCaption) },
                    label = {
                        Text(
                            if (frame.showCaption) "Shown on widget" else "Hidden",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = if (frame.showCaption) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }
            SettingGroup("Opacity · ${(frame.opacity * 100).toInt()}%") {
                Slider(
                    value = frame.opacity,
                    onValueChange = onOpacity,
                    valueRange = 0.4f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            SettingGroup("Corners · ${frame.cornerRadiusDp}dp") {
                Slider(
                    value = frame.cornerRadiusDp.toFloat(),
                    onValueChange = { onCorner(it.toInt()) },
                    valueRange = 0f..32f,
                    steps = 31,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun PinAndDeleteRow(
    frame: LivingFrame,
    boundCount: Int,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onTestPush: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolzExpressiveButton(
            onClick = onPin,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = LargeExpressiveShape
        ) {
            Icon(Icons.Rounded.AddToHomeScreen, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (boundCount > 0) "Add another · $boundCount placed" else "Add to home screen",
                fontWeight = FontWeight.Black
            )
        }
        ToolzOutlinedExpressiveButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete widget", fontWeight = FontWeight.Bold)
        }
        if (boundCount > 0) {
            ToolzOutlinedExpressiveButton(
                onClick = onTestPush,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send test frame", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Footnote() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Home widgets can't play video or sound — clips become silent frame loops. 10–15 fps runs best; animation pauses when the screen is off.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
