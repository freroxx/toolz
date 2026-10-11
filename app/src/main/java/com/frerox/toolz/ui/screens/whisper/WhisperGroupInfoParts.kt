/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.readBoundedGroupImageBytes
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton

// ─────────────────────────────────────────────────────────────────────────────
// Generic list row (connected-list segments)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GroupListRow(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    leading: (@Composable () -> Unit)? = null,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supporting.isNullOrBlank()) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = containerColor,
            modifier = modifier.fillMaxWidth(),
        ) { body() }
    } else {
        Surface(shape = shape, color = containerColor, modifier = modifier.fillMaxWidth()) { body() }
    }
}

@Composable
internal fun SettingSwitchRow(
    shape: RoundedCornerShape,
    icon: ImageVector,
    title: String,
    supporting: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    GroupListRow(
        shape = shape,
        onClick = onToggle,
        leading = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        title = title,
        supporting = supporting,
        trailing = { ExpressiveSwitch(checked = checked, onCheckedChange = { onToggle() }) },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero + quick actions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GroupHero(
    name: String,
    groupId: String,
    pictureBytes: ByteArray?,
    canChangePicture: Boolean,
    onPickPicture: (ByteArray, String) -> Unit,
    onPictureError: (String) -> Unit,
    canRename: Boolean,
    onRename: () -> Unit,
    memberCount: Int,
    inviteLabel: String,
    description: String,
    canEditDescription: Boolean,
    onEditDescription: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tooBig = stringResource(R.string.st_Whisper_Error_ImageTooLarge)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { readBoundedGroupImageBytes(it) }
                ?: error("empty")
            onPickPicture(bytes, mime)
        }.onFailure { onPictureError(tooBig) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = canChangePicture, role = Role.Button) { picker.launch("image/*") },
            ) {
                GroupAvatar(name = name, groupId = groupId, size = 112.dp, picture = pictureBytes)
            }
            if (canChangePicture) {
                Surface(
                    onClick = { picker.launch("image/*") },
                    shape = CircleShape,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = GroupCd.picture(),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (canRename) {
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.st_Whisper_Groups_Rename),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(
                icon = Icons.Rounded.Groups,
                text = "$memberCount " + stringResource(
                    if (memberCount == 1) R.string.st_Whisper_Groups_Member else R.string.st_Whisper_Groups_Members,
                ),
            )
            InfoChip(icon = Icons.Rounded.PersonAdd, text = inviteLabel)
        }
        if (description.isNotBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = canEditDescription, onClick = onEditDescription)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    active: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        containerColor = if (active) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (active) scheme.onTertiaryContainer else scheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member + request + invite rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AdminBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.st_Whisper_Groups_Admin),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun MemberRow(
    shape: RoundedCornerShape,
    member: WhisperGroupInfoViewModel.MemberEntry,
    onClick: (() -> Unit)?,
) {
    GroupListRow(
        shape = shape,
        onClick = onClick,
        leading = { GroupAvatar(name = member.name, groupId = member.userId, size = 44.dp) },
        title = member.name,
        trailing = if (member.role == "admin") ({ AdminBadge() }) else null,
    )
}

@Composable
internal fun RequestRow(
    shape: RoundedCornerShape,
    userId: String,
    name: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    GroupListRow(
        shape = shape,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        leading = { GroupAvatar(name = name, groupId = userId, size = 44.dp) },
        title = name,
        titleColor = MaterialTheme.colorScheme.onSecondaryContainer,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolzExpressiveTextButton(onClick = onDeny) { Text(GroupCd.deny()) }
                ToolzExpressiveButton(onClick = onApprove) {
                    Text(GroupCd.approve(), fontWeight = FontWeight.Bold)
                }
            }
        },
    )
}

@Composable
internal fun PendingInviteRow(
    shape: RoundedCornerShape,
    userId: String,
    name: String,
    onCancel: () -> Unit,
) {
    GroupListRow(
        shape = shape,
        leading = { GroupAvatar(name = name, groupId = userId, size = 44.dp) },
        title = name,
        supporting = stringResource(R.string.st_Whisper_Groups_InvitedLabel),
        trailing = {
            ToolzExpressiveTextButton(onClick = onCancel) {
                Text(stringResource(R.string.st_Whisper_Groups_CancelInvite))
            }
        },
    )
}

@Composable
internal fun MemberActionsSheet(
    member: WhisperGroupInfoViewModel.MemberEntry,
    iAmAdmin: Boolean,
    canDemote: Boolean,
    canTransfer: Boolean,
    onDismiss: () -> Unit,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
    onMakeOwner: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
) {
    GroupSheet(onDismiss = onDismiss) { close ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupAvatar(name = member.name, groupId = member.userId, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.role == "admin") Spacer(Modifier.height(4.dp))
            }
            if (member.role == "admin") AdminBadge()
        }
        if (iAmAdmin && member.role != "admin") {
            GroupActionRow(
                icon = Icons.Rounded.Star,
                label = stringResource(R.string.st_Whisper_Groups_MakeAdmin),
                onClick = { onPromote(); close() },
            )
        }
        if (canDemote) {
            GroupActionRow(
                icon = Icons.Rounded.StarBorder,
                label = stringResource(R.string.st_Whisper_Groups_Demote),
                onClick = { onDemote(); close() },
            )
        }
        if (canTransfer) {
            GroupActionRow(
                icon = Icons.Rounded.AdminPanelSettings,
                label = stringResource(R.string.st_Whisper_Groups_MakeOwner),
                onClick = { onMakeOwner(); close() },
            )
        }
        if (iAmAdmin) {
            GroupActionRow(
                icon = Icons.Rounded.PersonRemove,
                label = stringResource(R.string.st_Whisper_Groups_Remove),
                destructive = true,
                onClick = { onRemove(); close() },
            )
        }
        GroupActionRow(
            icon = Icons.Rounded.Block,
            label = stringResource(R.string.st_Whisper_Groups_Block),
            destructive = true,
            onClick = { onBlock(); close() },
        )
        GroupActionRow(
            icon = Icons.Rounded.Flag,
            label = stringResource(R.string.st_Whisper_Groups_Report),
            destructive = true,
            onClick = { onReport(); close() },
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheets + dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AddMemberSheet(
    loadFriends: ((List<WhisperProfile>) -> Unit) -> Unit,
    excludedIds: Set<String>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var friends by remember { mutableStateOf<List<WhisperProfile>?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        loadFriends { list -> friends = list.filter { it.id !in excludedIds } }
    }
    val visible = remember(friends, query) {
        val q = query.trim().lowercase()
        val all = friends.orEmpty()
        if (q.isBlank()) all
        else all.filter { it.effectiveName.lowercase().contains(q) || it.username.lowercase().contains(q) }
    }
    val listState = rememberLazyListState()
    GroupSheet(onDismiss = onDismiss) { close ->
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
            GroupSheetTitle(
                text = stringResource(R.string.st_Whisper_Groups_AddMembers),
                subtitle = if (picked.isEmpty()) null else "${picked.size}",
            )
            GroupSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.st_Whisper_Groups_SearchHint),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    friends == null -> GroupUiCenterLoading(modifier = Modifier.fillMaxSize())
                    friends.orEmpty().isEmpty() -> GroupEmptyPanel(
                        icon = Icons.Rounded.Groups,
                        message = stringResource(R.string.st_Whisper_Groups_NoFriendsForGroup),
                    )
                    visible.isEmpty() -> GroupEmptyPanel(
                        icon = Icons.Rounded.PersonAdd,
                        message = stringResource(R.string.st_Whisper_Groups_NoFriendsFound),
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().groupScrollFade(listState),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(visible, key = { it.id }) { friend ->
                            val selected = friend.id in picked
                            FriendPickRow(
                                friend = friend,
                                selected = selected,
                                onToggle = { if (selected) picked.remove(friend.id) else picked.add(friend.id) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolzExpressiveTextButton(onClick = close) { Text(stringResource(R.string.st_Whisper_Cancel)) }
                ToolzExpressiveButton(
                    onClick = { onAdd(picked.toList()); close() },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (picked.isEmpty()) stringResource(R.string.st_Whisper_Groups_AddMembers)
                        else "${stringResource(R.string.st_Whisper_Groups_AddMembers)} (${picked.size})",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Mute duration picker (8h / 1 week / always). */
@Composable
internal fun MuteDurationSheet(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    GroupSheet(onDismiss = onDismiss) { close ->
        GroupSheetTitle(stringResource(R.string.st_Whisper_Groups_MuteFor))
        GroupActionRow(
            icon = Icons.Rounded.Schedule,
            label = stringResource(R.string.st_Whisper_Groups_Mute8h),
            onClick = { onPick(8L * 60 * 60 * 1000); close() },
        )
        GroupActionRow(
            icon = Icons.Rounded.Schedule,
            label = stringResource(R.string.st_Whisper_Groups_MuteWeek),
            onClick = { onPick(7L * 24 * 60 * 60 * 1000); close() },
        )
        GroupActionRow(
            icon = Icons.Rounded.Schedule,
            label = stringResource(R.string.st_Whisper_Groups_MuteAlways),
            onClick = { onPick(Long.MAX_VALUE); close() },
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** Share sheet: QR of the group id + copy helper (join still needs an invite). */
@Composable
internal fun ShareGroupSheet(
    groupId: String,
    groupName: String,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val qr = remember(groupId) {
        runCatching {
            com.frerox.toolz.util.CryptoManager.generateQrCode("WHISPER-GROUP:$groupId", 512)
        }.getOrNull()
    }
    val qrBitmap = remember(qr) { qr?.asImageBitmap() }
    GroupSheet(onDismiss = onDismiss) { close ->
        GroupSheetTitle(stringResource(R.string.st_Whisper_Groups_Share))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (qrBitmap != null) {
                // Always on white so the code scans in dark theme too.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(14.dp),
                ) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = stringResource(R.string.st_Whisper_Groups_Share),
                        modifier = Modifier.size(212.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.st_Whisper_Groups_ShareText, groupName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            ToolzTonalExpressiveButton(
                onClick = {
                    clipboard.setText(AnnotatedString(groupId))
                    onMessage(context.getString(R.string.st_Whisper_Groups_Copied))
                    close()
                },
            ) {
                Text(stringResource(R.string.st_Whisper_Groups_CopyId), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Fullscreen shared-photos grid; thumbs decrypt lazily, tap opens the viewer. */
@Composable
internal fun GroupGalleryDialog(
    photos: List<WhisperGroupInfoViewModel.GalleryItem>,
    bytesById: Map<String, ByteArray>,
    onRequestBytes: (WhisperGroupInfoViewModel.GalleryItem) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var viewer by remember { mutableStateOf<ByteArray?>(null) }
    val gridState = rememberLazyGridState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.st_Whisper_Groups_Gallery), fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = GroupCd.close())
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
                if (photos.isEmpty()) {
                    GroupEmptyPanel(
                        icon = Icons.Rounded.PhotoLibrary,
                        message = stringResource(R.string.st_Whisper_Groups_GalleryEmpty),
                        modifier = Modifier.padding(top = 48.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(photos, key = { it.rowId }) { item ->
                            LaunchedEffect(item.rowId) { onRequestBytes(item) }
                            val bytes = bytesById[item.rowId]
                            val bitmap = rememberGroupBitmap(bytes, 384)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable(enabled = bytes != null) { viewer = bytes },
                                contentAlignment = Alignment.Center,
                            ) {
                                Crossfade(targetState = bitmap, label = "galleryThumb") { bmp ->
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = item.caption.ifBlank {
                                                stringResource(R.string.st_Whisper_Groups_Photo)
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            GroupUiInlineLoading()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    viewer?.let { bytes ->
        GroupImageViewer(bytes = bytes, onDismiss = { viewer = null }, onMessage = onMessage)
    }
}

/** Single-field edit dialog (rename / description). */
@Composable
internal fun GroupTextEditDialog(
    title: String,
    label: String,
    initial: String,
    maxLength: Int,
    confirmLabel: String,
    allowBlank: Boolean,
    singleLine: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) value = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                maxLines = if (singleLine) 1 else 4,
                shape = RoundedCornerShape(20.dp),
                supportingText = { Text("${value.length}/$maxLength") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = { onConfirm(value.trim()) },
                enabled = allowBlank || value.isNotBlank(),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            ToolzExpressiveTextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}
