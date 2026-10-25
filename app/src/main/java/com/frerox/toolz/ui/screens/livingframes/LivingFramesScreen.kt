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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class Source(val title: String, val hint: String, val icon: ImageVector) {
    GIF("GIF", "Animated image", Icons.Rounded.Gif),
    VIDEO("Video", "A clip up to 30 s", Icons.Rounded.Videocam),
    PHOTO("Photo", "One still picture", Icons.Rounded.Photo),
    SLIDESHOW("Slideshow", "Up to 30 photos", Icons.Rounded.Slideshow)
}

/** Nominal dp widths of a launcher cell block, so corner radius previews at true proportion. */
private enum class Aspect(val label: String, val ratio: Float, val nominalWidthDp: Float) {
    SQUARE("Square", 1f, 156f),
    WIDE("Wide", 2f, 320f),
    TALL("Tall", 0.5f, 156f)
}

private val CAPTURE_FPS_CHOICES = listOf(15, 24, 30, 60)

private data class PreviewState(val loading: Boolean, val frames: List<ImageBitmap>)

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
    var showQuality by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var aspect by rememberSaveable { mutableStateOf(Aspect.SQUARE) }
    var detailsFor by remember { mutableStateOf<LivingFrame?>(null) }
    var pinHelpFor by remember { mutableStateOf<LivingFrame?>(null) }

    // Imports, deletes and external pins can change the selected set without
    // going through the library tile. Start every new set playing; a paused
    // state belongs to the set it was paused on.
    LaunchedEffect(selected?.id) { playing = true }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let(viewModel::importGif)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let(viewModel::onVideoPicked)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let(viewModel::importPhoto)
    }
    val slidesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(LivingFramesRepository.MAX_SLIDES)
    ) { if (it.isNotEmpty()) viewModel.importSlideshow(it) }

    fun openSources() {
        showQuality = false
        showSources = true
    }

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
        if (viewModel.pinToHome(frame)) {
            scope.launch { snackbar.showSnackbar("Finish adding it on your home screen.") }
        } else {
            pinHelpFor = frame
        }
    }

    // ── Dialogs & sheets ─────────────────────────────────────────────────

    ui.import?.let { imp ->
        if (!ui.importHidden) {
            AlertDialog(
                onDismissRequest = viewModel::hideImport,
                title = { Text(imp.label) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            "You can keep using the app while this finishes.",
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
            text = {
                Text(
                    if (placed.isEmpty()) {
                        "Its files will be removed from this device."
                    } else {
                        "Its files will be removed, and home-screen copies will turn into placeholders."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vib?.vibrateLongClick()
                        viewModel.delete(selected.id)
                    },
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
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setName(selected.id, text); renaming = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }

    detailsFor?.let { frame ->
        DetailsDialog(frame = frame, onDismiss = { detailsFor = null })
    }

    pinHelpFor?.let { frame ->
        AlertDialog(
            onDismissRequest = { pinHelpFor = null },
            title = { Text("Add it from your home screen") },
            text = {
                Text(
                    "Long-press an empty spot on your home screen, choose Widgets, then pick " +
                        "Toolz → Living Widgets and select “${frame.name}”."
                )
            },
            confirmButton = { TextButton(onClick = { pinHelpFor = null }) { Text("Got it") } }
        )
    }

    if (showSources) {
        ModalBottomSheet(onDismissRequest = { showSources = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "New Living Widget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Choose what to start from.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SourceGrid(onPick = ::pick)
                ImportQualityRow(
                    quality = ui.importQuality,
                    expanded = showQuality,
                    onToggle = { showQuality = !showQuality },
                    onChange = viewModel::setImportQuality
                )
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
                subtitle = "Bring your home screen to life",
                navigationIcon = {
                    IconButton(
                        onClick = { vib?.vibrateClick(); onBack() },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (selected != null) {
                        IconButton(onClick = { vib?.vibrateClick(); openSources() }) {
                            Icon(Icons.Rounded.Add, contentDescription = "New Living Widget")
                        }
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 12.dp
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        ToolzExpressiveButton(
                            onClick = { pin(selected) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = LargeExpressiveShape
                        ) {
                            Icon(Icons.Rounded.AddToHomeScreen, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (placed.isEmpty()) "Add to home screen" else "Add another copy",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
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
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                        if (ui.loadFailed) {
                            LoadFailedCard(onRetry = viewModel::retryLoad)
                        } else {
                            EmptyState(
                                quality = ui.importQuality,
                                showQuality = showQuality,
                                onToggleQuality = { showQuality = !showQuality },
                                onQuality = viewModel::setImportQuality,
                                onPick = ::pick,
                                storage = ui.storage,
                                onCleanup = viewModel::cleanupStorage
                            )
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
                                placedCount = placed.size,
                                onAspect = { vib?.vibrateTick(); aspect = it },
                                onTogglePlay = { vib?.vibrateClick(); playing = !playing },
                                onRename = { renaming = true },
                                onDetails = { detailsFor = selected },
                                onDelete = { confirmDelete = true }
                            )
                        }
                    }
                    item(key = "library") {
                        StaggeredEntrance(index = 1) {
                            Library(
                                frames = ui.frames,
                                selectedId = selected.id,
                                coverOf = viewModel::coverFile,
                                onSelect = {
                                    vib?.vibrateTick()
                                    viewModel.select(it)
                                    playing = true
                                },
                                onNew = { vib?.vibrateClick(); openSources() }
                            )
                        }
                    }
                    item(key = "customize") {
                        StaggeredEntrance(index = 2) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Make it yours",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                CustomizePanel(
                                    frame = selected,
                                    nextIn = placed.firstNotNullOfOrNull { it.nextSlideIn },
                                    vm = viewModel
                                )
                            }
                        }
                    }
                    if (placed.isNotEmpty()) {
                        item(key = "placed") {
                            StaggeredEntrance(index = 3) {
                                PlacedWidgetsPanel(
                                    placed = placed,
                                    onRefresh = viewModel::refreshWidget
                                )
                            }
                        }
                    }
                    ui.storage?.let { storage ->
                        if (storage.usedBytes > 0L || storage.orphanBytes > 0L) {
                            item(key = "storage") {
                                StaggeredEntrance(index = 4) {
                                    StoragePanel(
                                        storage = storage,
                                        onCleanup = viewModel::cleanupStorage
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Empty & recovery states ──────────────────────────────────────────────

@Composable
private fun EmptyState(
    quality: LivingFrameQuality,
    showQuality: Boolean,
    onToggleQuality: () -> Unit,
    onQuality: (LivingFrameQuality) -> Unit,
    onPick: (Source) -> Unit,
    storage: com.frerox.toolz.data.livingframes.LivingFramesStorage?,
    onCleanup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Make your home screen move",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Pick a GIF, video or photo to bring your home screen to life.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SourceGrid(onPick = onPick)
        ImportQualityRow(
            quality = quality,
            expanded = showQuality,
            onToggle = onToggleQuality,
            onChange = onQuality
        )
        Text(
            "You can resize it any time after adding it.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        storage?.let {
            if (it.usedBytes > 0L || it.orphanBytes > 0L) {
                StoragePanel(storage = it, onCleanup = onCleanup)
            }
        }
    }
}

@Composable
private fun LoadFailedCard(onRetry: () -> Unit) {
    ExpressiveCard(
        onClick = {},
        enabled = false,
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Warning, null, Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Your widgets didn't open",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                "Your files are safe. Try loading them again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            ToolzExpressiveButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Try again", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ── Stage (live preview) ─────────────────────────────────────────────────

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
                        "Running in the background",
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
    placedCount: Int,
    onAspect: (Aspect) -> Unit,
    onTogglePlay: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val preview by produceState(PreviewState(true, emptyList()), frame.id, frame.createdAt) {
        value = PreviewState(true, emptyList())
        value = PreviewState(false, viewModel.previewFrames(frame))
    }
    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    buildString {
                        append(frameSummary(frame))
                        if (placedCount > 0) {
                            append(" · ")
                            append(if (placedCount == 1) "on your home screen" else "$placedCount on your home screen")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        onClick = { menuOpen = false; onDetails() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val w = minOf(maxWidth, maxHeight * aspect.ratio)
                    val h = w / aspect.ratio
                    val animW by animateDpAsState(
                        w,
                        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                        label = "pw"
                    )
                    val animH by animateDpAsState(
                        h,
                        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                        label = "ph"
                    )
                    val corner = (frame.cornerRadiusDp * (animW.value / aspect.nominalWidthDp)).dp
                    Box(Modifier.size(animW, animH), contentAlignment = Alignment.Center) {
                        when {
                            preview.loading -> PreviewLoading()
                            preview.frames.isEmpty() -> PreviewUnavailable()
                            else -> {
                                val canPlay = frame.isAnimated || frame.isSlideshow
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (canPlay) {
                                                Modifier.clickable(
                                                    onClickLabel =
                                                        if (playing) "Pause preview" else "Play preview",
                                                    onClick = onTogglePlay
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    LivePreview(frame, preview.frames, playing, corner, Modifier.fillMaxSize())
                                }
                            }
                        }
                        if (!preview.loading && preview.frames.isNotEmpty() &&
                            (frame.isAnimated || frame.isSlideshow)
                        ) {
                            PlayToggle(
                                playing = playing,
                                onClick = onTogglePlay,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
                previewNote(frame, preview.frames.size)?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Preview shape",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToolzConnectedButtonGroup(
                selectedIndex = Aspect.entries.indexOf(aspect),
                options = Aspect.entries.map { it.label },
                onOptionSelected = { onAspect(Aspect.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PreviewLoading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExpressiveLoadingIndicator()
        Text(
            "Preparing preview…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewUnavailable() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            Icons.Rounded.Warning, null, Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Preview unavailable",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "The widget can still use this set.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun frameSummary(frame: LivingFrame): String = when {
    frame.isSlideshow -> "Slideshow · ${frame.frameCount} photos"
    frame.isAnimated -> {
        val fps = LivingFrameEngine.playbackFps(frame)
        val ms = LivingFrameEngine.displayDurationMs(
            frame.frameCount, frame.sourceFps, fps, frame.speed,
            frame.loopMode, frame.loopTransition
        )
        "${if (frame.type == LivingFrameType.GIF) "GIF" else "Video"} · ${seconds(ms)}"
    }
    frame.type == LivingFrameType.PHOTO -> "Photo"
    frame.type == LivingFrameType.GIF -> "GIF"
    else -> "Video"
}

private fun previewNote(frame: LivingFrame, shownFrames: Int): String? {
    if (!frame.isAnimated || shownFrames <= 0 || shownFrames >= frame.frameCount) return null
    val fps = LivingFrameEngine.playbackFps(frame)
    val shown = LivingFrameEngine.displayDurationMs(
        shownFrames, frame.sourceFps, fps, frame.speed, frame.loopMode, frame.loopTransition
    )
    val total = LivingFrameEngine.displayDurationMs(
        frame.frameCount, frame.sourceFps, fps, frame.speed, frame.loopMode, frame.loopTransition
    )
    if (shown >= total) return null
    // Videos intentionally preview their first half. Say so, but do not claim
    // "half" when the decode cap shortened it even further.
    val half = frame.type == LivingFrameType.VIDEO &&
        shownFrames >= (frame.frameCount / 2).coerceAtLeast(1)
    return if (half) {
        "Previewing the first half (${seconds(shown)}) — the widget plays the full ${seconds(total)}."
    } else {
        "Previewing the first ${seconds(shown)} — the widget plays the full ${seconds(total)}."
    }
}

private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1000f)

/** Circle when paused, squircle when playing — the shape change is the state change. */
@Composable
private fun PlayToggle(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val corner by animateDpAsState(
        if (playing) 14.dp else 22.dp,
        spring(Spring.DampingRatioMediumBouncy),
        label = "playShape"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause preview" else "Play preview",
                modifier = Modifier.size(22.dp)
            )
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
    val displayFps = LivingFrameEngine.playbackFps(frame)
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
    var tick by remember(frame.id) { mutableIntStateOf(0) }
    LaunchedEffect(steps) {
        if (steps.isNotEmpty()) tick = tick.coerceIn(0, steps.lastIndex)
    }
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
                PreviewImage(frames[step.from.coerceIn(0, frames.lastIndex)], frame, shape)
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
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape).alpha(alpha)
            )
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .aspectRatio(bitmap.width / bitmap.height.toFloat())
                    .clip(shape)
                    .alpha(alpha)
            )
        }
    }
}

// ── Library ──────────────────────────────────────────────────────────────

@Composable
private fun Library(
    frames: List<LivingFrame>,
    selectedId: String,
    coverOf: (String) -> java.io.File,
    onSelect: (String) -> Unit,
    onNew: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Your widgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyRow(
            Modifier.fillMaxWidth().horizontalFadingEdges(left = 16.dp, right = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(frames, key = { it.id }) { f ->
                LibraryTile(f, f.id == selectedId, coverOf(f.id)) { onSelect(f.id) }
            }
            item(key = "new") {
                Column(
                    Modifier.width(92.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onNew,
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(92.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, contentDescription = "New Living Widget")
                        }
                    }
                    Text(
                        "New",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(frame: LivingFrame, selected: Boolean, cover: java.io.File, onClick: () -> Unit) {
    val corner by animateDpAsState(
        if (selected) 30.dp else 22.dp,
        spring(Spring.DampingRatioMediumBouncy),
        label = "tileShape"
    )
    val shape = RoundedCornerShape(corner)
    val icon = when (frame.type) {
        LivingFrameType.GIF -> Icons.Rounded.Gif
        LivingFrameType.VIDEO -> Icons.Rounded.Videocam
        LivingFrameType.PHOTO -> Icons.Rounded.Photo
        LivingFrameType.SLIDESHOW -> Icons.Rounded.Slideshow
    }
    Column(
        Modifier
            .width(92.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(92.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
                )
        ) {
            AsyncImage(
                model = cover,
                contentDescription = frame.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
        Text(
            frame.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ── Sources & import quality ─────────────────────────────────────────────

@Composable
private fun SourceGrid(onPick: (Source) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val containers = listOf(
        colors.primaryContainer,
        colors.secondaryContainer,
        colors.tertiaryContainer,
        colors.surfaceContainerHighest
    )
    val onContainers = listOf(
        colors.onPrimaryContainer,
        colors.onSecondaryContainer,
        colors.onTertiaryContainer,
        colors.onSurface
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Source.entries.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, source ->
                    val i = row * 2 + col
                    ExpressiveCard(
                        onClick = { onPick(source) },
                        shape = RoundedCornerShape(26.dp),
                        containerColor = containers[i],
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(source.icon, null, Modifier.size(30.dp), tint = onContainers[i])
                            Column {
                                Text(
                                    source.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onContainers[i]
                                )
                                Text(
                                    source.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onContainers[i].copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportQualityRow(
    quality: LivingFrameQuality,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (LivingFrameQuality) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Tune, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Quality",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${quality.label} · ${qualityHint(quality)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Hide quality options" else "Change quality",
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolzConnectedButtonGroup(
                        selectedIndex = LivingFrameQuality.entries.indexOf(quality),
                        options = LivingFrameQuality.entries.map { it.label },
                        onOptionSelected = { onChange(LivingFrameQuality.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Higher quality looks sharper and uses more space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun qualityHint(quality: LivingFrameQuality): String = when (quality) {
    LivingFrameQuality.COMPACT -> "saves space"
    LivingFrameQuality.HIGH -> "recommended"
    LivingFrameQuality.ULTRA -> "sharpest"
}

// ── Details ──────────────────────────────────────────────────────────────

@Composable
private fun DetailsDialog(frame: LivingFrame, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Type", typeLabel(frame))
                if (frame.isSlideshow) {
                    DetailRow("Photos", "${frame.frameCount}")
                } else if (frame.isAnimated) {
                    DetailRow("Length", seconds(frameDurationMs(frame)))
                    DetailRow("Frames", "${frame.frameCount}")
                    DetailRow("Plays at", "${LivingFrameEngine.playbackFps(frame)} fps")
                    DetailRow(
                        "Captured at",
                        "${frame.sourceFps} fps · ${frame.quality.label} quality"
                    )
                } else {
                    DetailRow("Size", "${frame.width}×${frame.height}")
                    DetailRow("Quality", frame.quality.label)
                }
                if (frame.resolutionPercent != 100) {
                    DetailRow("Detail", "${frame.resolutionPercent}%")
                }
                if (frame.storedBytes > 0L) {
                    DetailRow("On disk", LivingFramesRepository.formatBytes(frame.storedBytes))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

private fun typeLabel(frame: LivingFrame): String = when (frame.type) {
    LivingFrameType.SLIDESHOW -> "Slideshow"
    LivingFrameType.PHOTO -> "Photo"
    LivingFrameType.GIF -> "GIF"
    LivingFrameType.VIDEO -> "Video loop"
}

private fun frameDurationMs(frame: LivingFrame): Long = LivingFrameEngine.displayDurationMs(
    frame.frameCount,
    frame.sourceFps,
    LivingFrameEngine.playbackFps(frame),
    frame.speed,
    frame.loopMode,
    frame.loopTransition
)

// ── Video clip picker ────────────────────────────────────────────────────

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
        mutableFloatStateOf(minOf(8_000L, maxLenMs).toFloat())
    }
    var start by remember(pending.uri) { mutableFloatStateOf(0f) }
    val lengthMs = length.roundToLong().coerceIn(500L, maxLenMs)
    val maxStart = (pending.durationMs - lengthMs).coerceAtLeast(0L).toFloat()
    val startMs = start.coerceIn(0f, maxStart).toLong()

    var chosenFps by remember(pending.uri) { mutableIntStateOf(30) }
    var advanced by rememberSaveable(pending.uri.toString()) { mutableStateOf(false) }
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
    val presets = listOf(3_000L, 6_000L, 8_000L, 12_000L, 20_000L, 30_000L)
        .filter { it <= maxLenMs }

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
                "Trim your video",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Choose the moment you want to loop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
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
                        .background(Color.Black.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center
                ) {
                    val t = thumb
                    if (t == null) ExpressiveLoadingIndicator()
                    else Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            clock(pending.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (maxStart > 0f) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingHeader("Starts at", clock(startMs))
                        Slider(
                            value = start.coerceIn(0f, maxStart),
                            onValueChange = { start = it },
                            valueRange = 0f..maxStart,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingHeader("Length", "%.1f s".format(Locale.US, lengthMs / 1000f))
                    Slider(
                        value = lengthMs.toFloat(),
                        onValueChange = { length = it },
                        valueRange = 500f..maxLenMs.toFloat().coerceAtLeast(501f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (presets.isNotEmpty()) {
                        ChoiceChips(
                            options = presets,
                            selected = presets.firstOrNull { it == lengthMs },
                            label = { "${it / 1000} s" },
                            onSelect = { length = it.toFloat() }
                        )
                    }
                    Text(
                        "About ${LivingFramesRepository.formatBytes(estimate)} on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { advanced = !advanced },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (advanced) "Fewer options" else "More options", modifier = Modifier.weight(1f))
                    Icon(
                        if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = advanced,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingBlock("Smoothness", "$effectiveFps fps") {
                            ToolzConnectedButtonGroup(
                                selectedIndex = CAPTURE_FPS_CHOICES.indexOf(chosenFps).coerceAtLeast(0),
                                options = CAPTURE_FPS_CHOICES.map { "$it" },
                                onOptionSelected = { chosenFps = CAPTURE_FPS_CHOICES[it] },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (effectiveFps < chosenFps) {
                                Text(
                                    "Limited to $effectiveFps fps for this length and quality.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        SettingBlock("Quality", quality.label) {
                            ToolzConnectedButtonGroup(
                                selectedIndex = LivingFrameQuality.entries.indexOf(quality),
                                options = LivingFrameQuality.entries.map { it.label },
                                onOptionSelected = { onQuality(LivingFrameQuality.entries[it]) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Stored at ${stored.first}×${stored.second} px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ToolzExpressiveButton(
                onClick = { onConfirm(startMs, lengthMs, effectiveFps, quality) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Create loop", fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun clock(ms: Long): String =
    String.format(Locale.US, "%d:%02d.%d", ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 100)
