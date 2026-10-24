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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameEstimate
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameQuality
import com.frerox.toolz.data.livingframes.LivingFrameSpeed
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveLoadingIndicator
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class Source(val type: LivingFrameType, val title: String, val hint: String, val icon: ImageVector) {
    GIF(LivingFrameType.GIF, "GIF", "Loop any animated image", Icons.Rounded.Gif),
    VIDEO(LivingFrameType.VIDEO, "Video", "Pick any moment, up to 30 s", Icons.Rounded.Videocam),
    PHOTO(LivingFrameType.PHOTO, "Photo", "A still with your corners", Icons.Rounded.Photo),
    SLIDESHOW(LivingFrameType.SLIDESHOW, "Slideshow", "Rotates up to 30 photos", Icons.Rounded.Slideshow)
}

/** Nominal dp widths of a launcher cell block, so corner radius previews at true proportion. */
private enum class Aspect(val label: String, val ratio: Float, val nominalWidthDp: Float) {
    SQUARE("2×2", 1f, 156f),
    WIDE("4×2", 2f, 320f),
    TALL("2×4", 0.5f, 156f)
}

private val CAPTURE_FPS_CHOICES = listOf(12, 24, 30, 60)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LivingWidgetsScreen(
    onBack: () -> Unit,
    viewModel: LivingFramesViewModel = hiltViewModel()
) {
    val vib = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val ui by viewModel.ui.collectAsState()
    val placed by viewModel.placed.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val selected = ui.selected

    var showSources by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var aspect by rememberSaveable { mutableStateOf(Aspect.SQUARE) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(viewModel::importGif) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(viewModel::onVideoPicked) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(viewModel::importPhoto) }
    val slidesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(LivingFramesRepository.MAX_SLIDES)
    ) { if (it.isNotEmpty()) viewModel.importSlideshow(it) }

    fun pick(source: Source) {
        vib?.vibrateClick()
        showSources = false
        when (source) {
            Source.GIF -> gifPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            Source.VIDEO -> videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            Source.PHOTO -> photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            Source.SLIDESHOW -> slidesPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    fun pin(frame: LivingFrame) {
        vib?.vibrateLongClick()
        if (!viewModel.pinToHome(frame)) {
            scope.launch {
                snackbar.showSnackbar("Your launcher blocked pinning. Long-press the home screen, open Widgets, and add Toolz Living Widget.")
            }
        }
    }

    // ── Dialogs & sheets ─────────────────────────────────────────────────

    ui.import?.let { imp ->
        if (!ui.importHidden) {
            AlertDialog(
                onDismissRequest = viewModel::hideImport,
                title = { Text(imp.label) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExpressiveLoadingIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${((imp.progress ?: 0f) * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        LinearProgressIndicator(
                            progress = { imp.progress ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "You can hide this and keep editing. Progress stays in the library " +
                                "and in a notification, and you can cancel from either.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::hideImport) { Text("Hide") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelImport) { Text("Cancel") }
                }
            )
        }
    }

    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${selected.name}”?") },
            text = { Text("Its ${selected.frameCount} frame${if (selected.frameCount == 1) "" else "s"} are removed. Widgets already on your home screen turn into a placeholder you can long-press to remove.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; vib?.vibrateLongClick(); viewModel.delete(selected.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
        )
    }

    if (renaming && selected != null) {
        var text by remember(selected.id) { mutableStateOf(selected.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(40) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setName(selected.id, text); renaming = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }

    if (showSources) {
        ModalBottomSheet(onDismissRequest = { showSources = false }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("New widget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                ImportQualityPicker(
                    quality = ui.importQuality,
                    onChange = viewModel::setImportQuality
                )
                SourceGrid(onPick = ::pick)
            }
        }
    }

    ui.pendingVideo?.let { pv ->
        ClipSheet(
            pending = pv,
            viewModel = viewModel,
            quality = ui.importQuality,
            onQuality = viewModel::setImportQuality,
            onCancel = viewModel::cancelVideo,
            onConfirm = { start, length, fps, chosenQuality ->
                viewModel.confirmVideo(start, length, fps, chosenQuality)
            }
        )
    }

    // ── Screen ─────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Living Widgets",
                subtitle = "GIFs, video and photos on your home screen",
                navigationIcon = {
                    IconButton(
                        onClick = { vib?.vibrateClick(); onBack() },
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
        floatingActionButton = {
            if (selected != null) {
                ExtendedFloatingActionButton(
                    onClick = { pin(selected) },
                    icon = { Icon(Icons.Rounded.AddToHomeScreen, null) },
                    text = { Text("Add to home screen", fontWeight = FontWeight.SemiBold) },
                    shape = LargeExpressiveShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 12.dp, bottom = 24.dp))
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (ui.import != null && ui.importHidden) {
                item(key = "importProgress") {
                    ImportProgressCard(
                        state = ui.import!!,
                        onShow = viewModel::showImport,
                        onCancel = viewModel::cancelImport
                    )
                }
            }
            when {
                !ui.loaded -> item {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator()
                    }
                }

                selected == null -> item {
                    StaggeredEntrance(index = 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Make your home screen move",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (ui.loadFailed) {
                                        "We couldn't read the saved library. Imported frames are still on disk; try loading again before adding more."
                                    } else {
                                        "Pick something to start. It's cut to your widget's exact size, and the launcher plays it. Nothing runs in the background."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (ui.loadFailed) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!ui.loadFailed) SourceGrid(onPick = ::pick)
                            if (ui.loadFailed || (ui.storage?.usedBytes ?: 0L) > 0L) {
                                StoragePanel(
                                    storage = ui.storage,
                                    loadFailed = ui.loadFailed,
                                    onRetry = viewModel::retryLoad,
                                    onCleanup = viewModel::cleanupStorage
                                )
                            }
                        }
                    }
                }

                else -> {
                    item(key = "stage") {
                        StaggeredEntrance(index = 0) {
                            StageSection(
                                frame = selected,
                                viewModel = viewModel,
                                aspect = aspect,
                                playing = playing,
                                onAspect = { vib?.vibrateTick(); aspect = it },
                                onTogglePlay = { vib?.vibrateClick(); playing = !playing },
                                onRename = { renaming = true }
                            )
                        }
                    }
                    item(key = "library") {
                        StaggeredEntrance(index = 1) {
                            Library(
                                frames = ui.frames,
                                selectedId = selected.id,
                                coverOf = viewModel::coverFile,
                                onSelect = { vib?.vibrateTick(); viewModel.select(it); playing = true },
                                onNew = { vib?.vibrateClick(); showSources = true }
                            )
                        }
                    }
                    item(key = "settings") {
                        StaggeredEntrance(index = 2) {
                            SettingsSection(
                                frame = selected,
                                viewModel = viewModel,
                                placed = placed,
                                onPin = { pin(selected) },
                                onDelete = { confirmDelete = true }
                            )
                        }
                    }
                    item(key = "storage") {
                        StaggeredEntrance(index = 3) {
                            StoragePanel(
                                storage = ui.storage,
                                loadFailed = ui.loadFailed,
                                onRetry = viewModel::retryLoad,
                                onCleanup = viewModel::cleanupStorage
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Stage (live preview) ────────────────────────────────────────────────────────────

@Composable
private fun ImportProgressCard(
    state: ImportState,
    onShow: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveLoadingIndicator()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "You can keep editing while this finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                Text(
                    "${((state.progress ?: 0f) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            LinearProgressIndicator(
                progress = { state.progress ?: 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onShow) { Text("Show progress") }
            }
        }
    }
}

@Composable
private fun StageSection(
    frame: LivingFrame,
    viewModel: LivingFramesViewModel,
    aspect: Aspect,
    playing: Boolean,
    onAspect: (Aspect) -> Unit,
    onTogglePlay: () -> Unit,
    onRename: () -> Unit
) {
    val frames by produceState<List<ImageBitmap>>(emptyList(), frame.id, frame.createdAt) {
        value = emptyList()
        value = viewModel.previewFrames(frame)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    frame.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    describe(frame),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, "Rename") }
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(300.dp).padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val w = minOf(maxWidth, maxHeight * aspect.ratio)
                    val h = w / aspect.ratio
                    val animW by animateDpAsState(w, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "pw")
                    val animH by animateDpAsState(h, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "ph")
                    val corner = (frame.cornerRadiusDp * (animW.value / aspect.nominalWidthDp)).dp
                    Box(Modifier.size(animW, animH), contentAlignment = Alignment.Center) {
                        if (frames.isEmpty()) ExpressiveLoadingIndicator()
                        else LivePreview(frame, frames, playing, corner, Modifier.fillMaxSize())
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (frame.isAnimated && frames.isNotEmpty() && frames.size < frame.frameCount) {
                            val displayFps = LivingFrameFps.snap(frame.fps)
                            val sourceFps = frame.sourceFps.coerceAtLeast(1)
                            val shown = LivingFrameEngine.displayDurationMs(
                                frames.size, sourceFps, displayFps, frame.speed,
                                frame.loopMode, frame.loopTransition
                            )
                            val total = LivingFrameEngine.displayDurationMs(
                                frame.frameCount, sourceFps, displayFps, frame.speed,
                                frame.loopMode, frame.loopTransition
                            )
                            "Preview · first %.1f s of %.1f s".format(
                                shown / 1000f,
                                total / 1000f
                            )
                        } else {
                            "Preview as a ${aspect.label} widget"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (frame.isAnimated || frame.isSlideshow) PlayToggle(playing, onTogglePlay)
                }
            }
        }
        ToolzConnectedButtonGroup(
            selectedIndex = Aspect.entries.indexOf(aspect),
            options = Aspect.entries.map { it.label },
            onOptionSelected = { onAspect(Aspect.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun describe(frame: LivingFrame): String = when {
    frame.isSlideshow -> "Slideshow · ${frame.frameCount} photos · ${frame.quality.label}"
    frame.isAnimated -> {
        val sourceFps = frame.sourceFps.coerceAtLeast(1)
        val displayFps = LivingFrameFps.snap(frame.fps)
        val playFps = LivingFrameEngine.playbackFps(frame)
        val seconds = LivingFrameEngine.displayDurationMs(
            frame.frameCount, sourceFps, playFps, frame.speed, frame.loopMode,
            frame.loopTransition
        ) / 1000f
        val rate = if (displayFps > sourceFps) {
            "$displayFps fps (captured at $sourceFps)"
        } else {
            "$displayFps fps"
        }
        val speed = if (LivingFrameSpeed.snap(frame.speed) != 1f) {
            " · ${LivingFrameSpeed.label(frame.speed)}"
        } else {
            ""
        }
        "${if (frame.type == LivingFrameType.GIF) "GIF" else "Video"} · %.1f s · %d frames · %s%s · %d×%d".format(
            seconds, frame.frameCount, rate, speed, frame.width, frame.height
        )
    }
    else -> "Photo · ${frame.width}×${frame.height} · ${frame.quality.label}"
}

/** Circle when paused, squircle when playing — the shape change is the state change. */
@Composable
private fun PlayToggle(playing: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(if (playing) 16.dp else 28.dp, spring(Spring.DampingRatioMediumBouncy), label = "playShape")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
        }
    }
}

/**
 * Same playback rules as the widget: the loop order comes from
 * LivingFrameEngine and the rate from the frame's fps. Corners, fit and
 * opacity are applied at draw time so sliders respond instantly.
 */
@Composable
private fun LivePreview(
    frame: LivingFrame,
    frames: List<ImageBitmap>,
    playing: Boolean,
    corner: Dp,
    modifier: Modifier = Modifier
) {
    val displayFps = LivingFrameFps.snap(frame.fps)
    val sourceFps = frame.sourceFps.coerceAtLeast(1)
    // Slideshows use their own cadence; animations share the widget's planner
    // so the preview shows exactly the steps (including seam blends) that will
    // be pushed to the launcher.
    val steps = remember(
        frames, frame.fps, frame.speed, frame.loopMode, frame.loopTransition,
        frame.isSlideshow
    ) {
        if (frame.isSlideshow) {
            frames.indices.map { LivingFrameEngine.DisplayStep(it, it, 0f) }
        } else {
            LivingFrameEngine.displaySteps(
                frames.size, sourceFps, displayFps, frame.speed, frame.loopMode,
                frame.loopTransition
            )
        }
    }
    var tick by remember(steps) { mutableIntStateOf(0) }
    LaunchedEffect(steps, displayFps, frame.isSlideshow, playing) {
        if (!playing || steps.size < 2) return@LaunchedEffect
        val intervalNs = if (frame.isSlideshow) {
            2_400_000_000L
        } else {
            1_000_000_000L / displayFps.coerceAtLeast(1)
        }
        var pos = tick.coerceIn(0, steps.lastIndex)
        var acc = 0L
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            acc += now - last
            last = now
            if (acc >= intervalNs) {
                pos = (pos + (acc / intervalNs).toInt()) % steps.size
                acc %= intervalNs
                tick = pos
            }
        }
    }
    val shape = RoundedCornerShape(corner)
    val step = steps[tick.coerceIn(0, steps.lastIndex)]
    when {
        frame.isSlideshow -> {
            Crossfade(
                targetState = step.from.coerceIn(0, frames.lastIndex),
                animationSpec = tween(500),
                modifier = modifier,
                label = "slide"
            ) { i ->
                PreviewImage(frames[i.coerceIn(0, frames.lastIndex)], frame, shape)
            }
        }
        step.blended -> {
            // The widget bakes this step as a blend; draw both sides at their
            // weights so the seam reads the same in the preview.
            Box(modifier) {
                PreviewImage(
                    frames[step.from.coerceIn(0, frames.lastIndex)], frame, shape
                )
                PreviewImage(
                    frames[step.to.coerceIn(0, frames.lastIndex)], frame, shape,
                    alphaScale = step.mix
                )
            }
        }
        else -> {
            Box(modifier) {
                PreviewImage(frames[step.from.coerceIn(0, frames.lastIndex)], frame, shape)
            }
        }
    }
}

@Composable
private fun PreviewImage(
    bitmap: ImageBitmap,
    frame: LivingFrame,
    shape: Shape,
    alphaScale: Float = 1f
) {
    val alpha = (frame.opacity * alphaScale.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (frame.fit == LivingFrameFit.FILL) {
            Image(
                bitmap = bitmap, contentDescription = frame.name, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape).alpha(alpha)
            )
        } else {
            Image(
                bitmap = bitmap, contentDescription = frame.name, contentScale = ContentScale.FillBounds,
                modifier = Modifier.aspectRatio(bitmap.width / bitmap.height.toFloat()).clip(shape).alpha(alpha)
            )
        }
    }
}

// ── Library ───────────────────────────────────────────────────────────────────────

@Composable
private fun Library(
    frames: List<LivingFrame>,
    selectedId: String,
    coverOf: (String) -> java.io.File,
    onSelect: (String) -> Unit,
    onNew: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyRow(
            Modifier.fillMaxWidth().horizontalFadingEdges(left = 16.dp, right = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(frames, key = { it.id }) { f ->
                LibraryTile(f, f.id == selectedId, coverOf(f.id)) { onSelect(f.id) }
            }
            item(key = "new") {
                Surface(
                    onClick = onNew,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(92.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "New widget") }
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(frame: LivingFrame, selected: Boolean, cover: java.io.File, onClick: () -> Unit) {
    val corner by animateDpAsState(if (selected) 32.dp else 22.dp, spring(Spring.DampingRatioMediumBouncy), label = "tileShape")
    val shape = RoundedCornerShape(corner)
    val icon = when (frame.type) {
        LivingFrameType.GIF -> Icons.Rounded.Gif
        LivingFrameType.VIDEO -> Icons.Rounded.Videocam
        LivingFrameType.PHOTO -> Icons.Rounded.Photo
        LivingFrameType.SLIDESHOW -> Icons.Rounded.Slideshow
    }
    Box(
        Modifier
            .size(92.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(model = cover, contentDescription = frame.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(14.dp), tint = Color.White) }
    }
}

// ── Settings ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    frame: LivingFrame,
    viewModel: LivingFramesViewModel,
    placed: List<PlacedWidget>,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val vib = LocalVibrationManager.current
    val tabs = when {
        frame.isSlideshow -> listOf("Timing", "Look", "Widget")
        frame.isAnimated -> listOf("Motion", "Look", "Widget")
        else -> listOf("Look", "Widget")
    }
    var tab by rememberSaveable(frame.id) { mutableIntStateOf(0) }
    val current = tabs[tab.coerceIn(0, tabs.lastIndex)]

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ToolzConnectedButtonGroup(
            selectedIndex = tabs.indexOf(current),
            options = tabs,
            onOptionSelected = { vib?.vibrateTick(); tab = it },
            modifier = Modifier.fillMaxWidth()
        )
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 14 }) togetherWith fadeOut(tween(100))
            },
            label = "settingsTab"
        ) { name ->
            when (name) {
                "Motion" -> MotionPanel(frame, viewModel)
                "Timing" -> TimingPanel(frame, placed.firstNotNullOfOrNull { it.nextSlideIn }, viewModel)
                "Look" -> LookPanel(frame, viewModel)
                else -> WidgetPanel(frame, placed, onPin, viewModel::refreshWidget, onDelete)
            }
        }
    }
}

// ── Sources ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SourceGrid(onPick: (Source) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val containers = listOf(colors.primaryContainer, colors.secondaryContainer, colors.tertiaryContainer, colors.surfaceContainerHighest)
    val onContainers = listOf(colors.onPrimaryContainer, colors.onSecondaryContainer, colors.onTertiaryContainer, colors.onSurface)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Source.entries.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, source ->
                    val i = row * 2 + col
                    ExpressiveCard(
                        onClick = { onPick(source) },
                        shape = RoundedCornerShape(28.dp),
                        containerColor = containers[i],
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(source.icon, null, Modifier.size(32.dp), tint = onContainers[i])
                            Column {
                                Text(source.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onContainers[i])
                                Text(source.hint, style = MaterialTheme.typography.bodySmall, color = onContainers[i].copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Video clip picker ──────────────────────────────────────────────────────────────────────

@Composable
private fun ImportQualityPicker(quality: LivingFrameQuality, onChange: (LivingFrameQuality) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingHeader("Import quality", quality.label)
        ToolzConnectedButtonGroup(
            selectedIndex = quality.ordinal,
            options = LivingFrameQuality.entries.map { it.label },
            onOptionSelected = { onChange(LivingFrameQuality.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Stores video frames up to ${quality.storedSide} px and photos up to " +
                "${quality.photoSide} px, and captures at most ${quality.maxCaptureFps} fps. " +
                "Higher quality uses more storage; it can't be raised after importing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipSheet(
    pending: PendingVideo,
    viewModel: LivingFramesViewModel,
    quality: LivingFrameQuality,
    onQuality: (LivingFrameQuality) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (
        startMs: Long,
        lengthMs: Long,
        fps: Int,
        quality: LivingFrameQuality
    ) -> Unit
) {
    val maxLenMs = minOf(pending.durationMs, LivingFramesRepository.MAX_CLIP_MS).coerceAtLeast(500L)
    var length by remember(pending.uri) {
        mutableFloatStateOf(minOf(12_000L, maxLenMs).toFloat())
    }
    var start by remember(pending.uri) { mutableFloatStateOf(0f) }
    val lengthMs = length.roundToLong().coerceIn(500L, maxLenMs)
    val maxStart = (pending.durationMs - lengthMs).coerceAtLeast(0L).toFloat()
    val startMs = start.coerceIn(0f, maxStart).toLong()

    var chosenFps by remember(pending.uri) { mutableIntStateOf(30) }
    // The preset and the 900-frame ceiling both cap what can be captured;
    // show the real rate instead of pretending 60 fps was stored.
    val effectiveFps = LivingFrameFps.atMost(
        minOf(
            chosenFps.toFloat(),
            quality.maxCaptureFps.toFloat(),
            LivingFramesRepository.MAX_FRAMES * 1000f / lengthMs
        )
    )
    val frameCount = (lengthMs / 1000f * effectiveFps).roundToInt()
        .coerceIn(2, LivingFramesRepository.MAX_FRAMES)
    val stored = LivingFrameEstimate.storedSize(pending.width, pending.height, quality)
    val estimate = LivingFrameEstimate.bytes(
        pending.width,
        pending.height,
        quality,
        frameCount
    )

    val thumb by produceState<ImageBitmap?>(null, pending.uri, startMs / 250L) {
        delay(120L)
        viewModel.videoThumb(pending.uri, startMs)?.let { value = it }
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Choose your loop",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    val t = thumb
                    if (t == null) ExpressiveLoadingIndicator()
                    else Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingHeader("Starts at", clock(startMs))
                    Slider(
                        value = start.coerceIn(0f, maxStart),
                        onValueChange = { start = it },
                        valueRange = 0f..maxStart.coerceAtLeast(1f),
                        enabled = maxStart > 0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingHeader("Length", "%.1f s".format(lengthMs / 1000f))
                    Slider(
                        value = lengthMs.toFloat(),
                        onValueChange = { length = it },
                        valueRange = 500f..maxLenMs.toFloat().coerceAtLeast(501f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        "$frameCount frames · $effectiveFps fps · about " +
                            LivingFramesRepository.formatBytes(estimate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Stored at ${stored.first}×${stored.second} px. The real size is shown " +
                            "after import.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (effectiveFps < chosenFps) {
                        Text(
                            "Limited to $effectiveFps fps by ${quality.label} quality or the " +
                                "${LivingFramesRepository.MAX_FRAMES}-frame limit for this length.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingHeader("Frames per second")
                    ToolzConnectedButtonGroup(
                        selectedIndex = CAPTURE_FPS_CHOICES.indexOf(chosenFps).coerceAtLeast(0),
                        options = CAPTURE_FPS_CHOICES.map { "$it" },
                        onOptionSelected = { chosenFps = CAPTURE_FPS_CHOICES[it] },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ImportQualityPicker(quality = quality, onChange = onQuality)
            }
            Spacer(Modifier.height(12.dp))
            ToolzExpressiveButton(
                onClick = { onConfirm(startMs, lengthMs, effectiveFps, quality) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Create loop", fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun clock(ms: Long): String = "%d:%02d.%d".format(ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 100)
