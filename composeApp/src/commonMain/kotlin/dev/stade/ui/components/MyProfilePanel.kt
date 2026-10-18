package dev.stade.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.identity.ProfileStatus
import dev.stade.media.MediaEditorDialog
import dev.stade.message.MAX_BIO_LEN
import dev.stade.ui.compressAvatar
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.rememberMediaPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PANEL_SLIDE_MS = 320
private const val PANEL_FADE_MS = 180
private val PROFILE_PANEL_MAX_WIDTH = 520.dp
private val PANEL_DISMISS_DRAG = 56.dp

val LocalOpenMyProfile = compositionLocalOf<(() -> Unit)?> { null }

@Composable
fun MyProfilePanel(
    container: AppContainer,
    owner: LocalIdentity,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pendingAvatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showRemoveAvatarConfirm by remember { mutableStateOf(false) }
    val avatarPicker = rememberMediaPickerLauncher(
        onImages = { images -> images.firstOrNull()?.let { pendingAvatarBytes = it } },
        onVideo = {},
        imagesOnly = true
    )

    pendingAvatarBytes?.let { raw ->
        MediaEditorDialog(
            imageBytes = raw,
            onSave = { edited ->
                scope.launch {
                    val compressed = withContext(Dispatchers.Default) { compressAvatar(edited) }
                    container.avatars.setMyAvatar(owner, compressed)
                }
                pendingAvatarBytes = null
            },
            onCancel = { pendingAvatarBytes = null },
            circular = true
        )
    }

    if (showRemoveAvatarConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveAvatarConfirm = false },
            icon = {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(strings.removeAvatarConfirmTitle) },
            text = { Text(strings.removeAvatarConfirmBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { container.avatars.setMyAvatar(owner, null) }
                        showRemoveAvatarConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(strings.removeAvatarAction) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAvatarConfirm = false }) { Text(strings.cancel) }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(PANEL_FADE_MS)),
            exit = fadeOut(tween(PANEL_FADE_MS))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) { -it } + fadeIn(tween(PANEL_FADE_MS)),
            exit = slideOutVertically(tween(PANEL_SLIDE_MS)) { -it } + fadeOut(tween(PANEL_FADE_MS))
        ) {
            ProfilePanelSurface(
                container = container,
                owner = owner,
                onDismiss = onDismiss,
                onChangeAvatar = { avatarPicker.launch() },
                onRemoveAvatar = { showRemoveAvatarConfirm = true }
            )
        }
    }
}

@Composable
private fun ProfilePanelSurface(
    container: AppContainer,
    owner: LocalIdentity,
    onDismiss: () -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var bio by remember(owner.id, owner.bio) { mutableStateOf(owner.bio) }
    var status by remember(owner.id, owner.status) {
        mutableStateOf(ProfileStatus.fromCode(owner.status))
    }
    var saved by remember { mutableStateOf(false) }
    var dragged by remember { mutableStateOf(0f) }
    var showAvatarMenu by remember { mutableStateOf(false) }
    val dirty = bio != owner.bio || status.code != owner.status

    LaunchedEffect(saved) {
        if (saved) {
            delay(1400)
            saved = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = PROFILE_PANEL_MAX_WIDTH),
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.myProfileTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.closeAction)
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (owner.avatar != null) showAvatarMenu = true else onChangeAvatar()
                            }
                    ) {
                        Avatar(
                            name = owner.nickname,
                            keySeed = owner.publicSigningKey,
                            avatarBytes = owner.avatar,
                            size = 76.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                if (owner.avatar != null) showAvatarMenu = true else onChangeAvatar()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = strings.changeAvatarAction,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showAvatarMenu,
                        onDismissRequest = { showAvatarMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.changeAvatarAction) },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                            onClick = {
                                showAvatarMenu = false
                                onChangeAvatar()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.removeAvatarAction) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showAvatarMenu = false
                                onRemoveAvatar()
                            }
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        owner.nickname,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    ProfileStatusPill(status)
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                strings.profileStatusSection,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            StatusPicker(selected = status, onSelect = { status = it })
            Spacer(Modifier.height(8.dp))
            Text(
                strings.profileStatusManualNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            Text(
                strings.profileAboutSection,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { raw ->
                    val cleaned = raw.replace('\n', ' ').replace('\r', ' ')
                    bio = cleaned.take(MAX_BIO_LEN)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(strings.profileAboutPlaceholder) },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                supportingText = {
                    Text(
                        "${bio.length}/$MAX_BIO_LEN",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            Spacer(Modifier.height(4.dp))
            Text(
                strings.profileSharedNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = dirty,
                onClick = {
                    val nextBio = bio.trim()
                    val nextStatus = status.code
                    bio = nextBio
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            runCatching { container.avatars.setMyProfile(owner, nextBio, nextStatus) }
                        }
                        saved = true
                    }
                }
            ) {
                Text(if (saved && !dirty) strings.profileSaved else strings.saveAction)
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> dragged += delta },
                        onDragStopped = {
                            if (dragged < -with(density) { PANEL_DISMISS_DRAG.toPx() }) onDismiss()
                            dragged = 0f
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun StatusPicker(selected: ProfileStatus, onSelect: (ProfileStatus) -> Unit) {
    val rows = listOf(
        listOf(ProfileStatus.NONE, ProfileStatus.ONLINE),
        listOf(ProfileStatus.IDLE, ProfileStatus.DND),
        listOf(ProfileStatus.OFFLINE)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    StatusTile(
                        status = option,
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusTile(
    status: ProfileStatus,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(profileStatusColor(status))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                profileStatusLabel(status),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
