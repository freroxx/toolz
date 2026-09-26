/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.media.downloader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.data.downloader.MediaDownloaderRepository
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.toolzBackground

/** Editable containers per platform shown in the customization screen. */
fun downloaderPlatformExts(platform: MediaDownloaderRepository.Platform): List<String> =
    // Native format availability varies by provider. These are the format
    // preferences Toolz can remember whenever a matching option is available.
    listOf("mp4", "mp3", "m4a", "wav", "ogg", "flac")

/**
 * Quality keys users can pin or auto-select. Video by quality label (stable
 * across local ladders and remote assets alike), audio by container.
 * MP4 is intentionally absent: every video row is MP4, so it could never sort.
 */
fun downloaderQualityKeys(): List<String> =
    listOf("1080p", "720p", "480p", "360p", "MP3", "M4A", "WAV", "OGG", "FLAC")

private fun platformDisplayName(platform: MediaDownloaderRepository.Platform): String =
    when (platform) {
        MediaDownloaderRepository.Platform.YOUTUBE -> "YouTube"
        MediaDownloaderRepository.Platform.TIKTOK -> "TikTok"
        MediaDownloaderRepository.Platform.INSTAGRAM -> "Reels"
    }

private fun platformDot(platform: MediaDownloaderRepository.Platform): Color =
    when (platform) {
        MediaDownloaderRepository.Platform.YOUTUBE -> Color(0xFFFF0000)
        MediaDownloaderRepository.Platform.TIKTOK -> Color(0xFF000000)
        MediaDownloaderRepository.Platform.INSTAGRAM -> Color(0xFFDD2A7B)
    }

/**
 * Advanced customization: per-platform format memory (favorites, hidden,
 * auto-select, default mode) plus on-device fallback audio formats. Everything
 * is stored in the shared settings DataStore, so backup & restore picks it up.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaDownloaderSettingsScreen(
    onBack: () -> Unit,
    viewModel: MediaDownloaderViewModel = hiltViewModel(),
) {
    val prefs by viewModel.formatPrefs.collectAsState()
    val platforms = listOf(
        MediaDownloaderRepository.Platform.YOUTUBE,
        MediaDownloaderRepository.Platform.TIKTOK,
        MediaDownloaderRepository.Platform.INSTAGRAM,
    )
    var selectedPlatform by rememberSaveable { mutableStateOf(platforms[0].name) }
    val platform = runCatching {
        MediaDownloaderRepository.Platform.valueOf(selectedPlatform)
    }.getOrNull() ?: platforms[0]

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        containerColor = Color.Transparent,
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_MediaDownloader_Settings),
                subtitle = stringResource(R.string.st_MediaDownloader_SettingsSub),
                navigationIcon = {
                    ToolzTonalExpressiveIconButton(
                        onClick = onBack,
                        shape = SquircleShape,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val entranceSeen = rememberDownloaderEntranceSeen()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fadingEdges(top = 16.dp, bottom = 32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "audio_formats") {
                DownloaderSection(0, entranceSeen) {
                    AudioFormatsCard(
                        enabled = prefs.ladderExts,
                        onToggle = viewModel::setLadderExt,
                    )
                }
            }
            item(key = "platform_picker") {
                DownloaderSection(1, entranceSeen) {
                    ExpressiveCard(onClick = {}, enabled = false, shape = LargeExpressiveShape) {
                        ToolzConnectedButtonGroup(
                            selectedIndex = platforms.indexOf(platform).takeIf { it >= 0 } ?: 0,
                            options = platforms.map(::platformDisplayName),
                            onOptionSelected = { index ->
                                selectedPlatform = platforms[index].name
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
            item(key = "platform_detail") {
                DownloaderSection(2, entranceSeen) {
                    PlatformPrefsCard(
                        platform = platform,
                        prefs = prefs.perPlatform[platform.name]
                            ?: MediaDownloaderViewModel.PlatformFormatPrefs(),
                        onToggleFavorite = { viewModel.toggleFavorite(platform, it) },
                        onToggleHidden = { viewModel.toggleHidden(platform, it) },
                        onAutoSelect = { viewModel.setAutoSelect(platform, it) },
                        onMode = { viewModel.setPlatformMode(platform, it) },
                    )
                }
            }
            item(key = "reset") {
                DownloaderSection(3, entranceSeen) {
                    ToolzOutlinedExpressiveButton(
                        onClick = viewModel::resetFormatPrefs,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.st_MediaDownloader_ResetDefaults),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioFormatsCard(
    enabled: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    ExpressiveCard(onClick = {}, enabled = false, shape = LargeExpressiveShape) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.st_MediaDownloader_AudioFormats),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.st_MediaDownloader_AudioFormatsHint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaDownloaderViewModel.DEFAULT_LADDER_EXTS.forEach { ext ->
                    val locked = ext == "mp3"
                    ExpressiveFilterChip(
                        selected = locked || ext in enabled,
                        enabled = !locked,
                        onClick = { onToggle(ext, ext !in enabled) },
                        leadingIcon = if (locked) {
                            {
                                Icon(
                                    Icons.Rounded.Lock,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else {
                            null
                        },
                        label = {
                            Text(
                                ext.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformPrefsCard(
    platform: MediaDownloaderRepository.Platform,
    prefs: MediaDownloaderViewModel.PlatformFormatPrefs,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onAutoSelect: (String?) -> Unit,
    onMode: (MediaDownloaderViewModel.DownloadMode?) -> Unit,
) {
    val exts = downloaderPlatformExts(platform)
    ExpressiveCard(onClick = {}, enabled = false, shape = LargeExpressiveShape) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (platform == MediaDownloaderRepository.Platform.TIKTOK) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                platformDot(platform)
                            },
                            CircleShape,
                        ),
                )
                Text(
                    platformDisplayName(platform),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            PrefGroupLabel(stringResource(R.string.st_MediaDownloader_DefaultMode))
            val customMode = prefs.defaultMode != null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(R.string.st_MediaDownloader_CustomMode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ExpressiveSwitch(
                    checked = customMode,
                    onCheckedChange = { enabled ->
                        onMode(
                            if (enabled) {
                                MediaDownloaderViewModel.DownloadMode.BOTH
                            } else {
                                null
                            },
                        )
                    },
                )
            }
            if (customMode) {
                val modeIndex = when (prefs.defaultMode) {
                    MediaDownloaderViewModel.DownloadMode.VIDEO -> 0
                    MediaDownloaderViewModel.DownloadMode.AUDIO -> 2
                    else -> 1
                }
                ToolzConnectedButtonGroup(
                    selectedIndex = modeIndex,
                    options = listOf(
                        stringResource(R.string.st_MediaDownloader_VideoGroup),
                        stringResource(R.string.st_MediaDownloader_Both),
                        stringResource(R.string.st_MediaDownloader_AudioGroup),
                    ),
                    onOptionSelected = { index ->
                        onMode(
                            when (index) {
                                0 -> MediaDownloaderViewModel.DownloadMode.VIDEO
                                2 -> MediaDownloaderViewModel.DownloadMode.AUDIO
                                else -> MediaDownloaderViewModel.DownloadMode.BOTH
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PrefGroupLabel(
                stringResource(R.string.st_MediaDownloader_Favorites),
                "Video by quality, audio by format. Pinned rows jump to the top.",
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                downloaderQualityKeys().forEach { key ->
                    ExpressiveFilterChip(
                        selected = prefs.favorites.any { it.uppercase() == key.uppercase() },
                        onClick = { onToggleFavorite(key) },
                        label = {
                            Text(
                                key.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                }
            }

            PrefGroupLabel(
                stringResource(R.string.st_MediaDownloader_Hidden),
                stringResource(R.string.st_MediaDownloader_HiddenHint),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // MP4 (all video) and MP3 can never be hidden — the backend keeps
                // them, so they are not offered here to avoid dead toggles.
                exts.filter { it != "mp3" && it != "mp4" }.forEach { ext ->
                    ExpressiveFilterChip(
                        selected = ext in prefs.hidden,
                        onClick = { onToggleHidden(ext) },
                        label = {
                            Text(
                                ext.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                }
            }

            PrefGroupLabel(
                stringResource(R.string.st_MediaDownloader_AutoSelect),
                "Pre-selects this quality whenever it appears.",
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpressiveFilterChip(
                    selected = prefs.autoSelect == null,
                    onClick = { onAutoSelect(null) },
                    label = {
                        Text(
                            stringResource(R.string.st_MediaDownloader_Auto),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
                downloaderQualityKeys().forEach { key ->
                    ExpressiveFilterChip(
                        selected = prefs.autoSelect?.uppercase() == key.uppercase(),
                        onClick = { onAutoSelect(key) },
                        label = {
                            Text(
                                key.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefGroupLabel(title: String, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
