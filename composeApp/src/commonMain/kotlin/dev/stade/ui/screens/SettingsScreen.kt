package dev.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.media.MediaEditorDialog
import dev.stade.notification.getNotificationPrivacyEnabled
import dev.stade.notification.getNotificationsEnabled
import dev.stade.notification.getRunInBackgroundEnabledCommon
import dev.stade.notification.isNotificationSupported
import dev.stade.notification.isRunInBackgroundSupported
import dev.stade.notification.isSystemNotificationSettingsSupported
import dev.stade.notification.openNotificationSettings
import dev.stade.notification.setNotificationPrivacyEnabled
import dev.stade.notification.setNotificationsEnabled
import dev.stade.notification.setRunInBackgroundEnabledCommon
import dev.stade.ui.compressAvatar
import dev.stade.ui.components.SettingsGroup
import dev.stade.ui.components.SettingsRowSurface
import dev.stade.ui.components.SettingsBadge
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.PlatformVerticalScrollbar
import dev.stade.ui.rememberMediaPickerLauncher
import dev.stade.ui.theme.getDynamicColorEnabled
import dev.stade.ui.theme.isDynamicColorSupported
import dev.stade.ui.theme.setDynamicColorEnabled
import dev.stade.ui.i18n.localeDisplayName
import dev.stade.ui.BackupOutcome
import dev.stade.ui.rememberBackupIo
import dev.stade.ui.components.BackupPassphraseDialog
import dev.stade.ui.components.UpdateSettingsSection
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.i18n.getLocalePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    onOpenTransports: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenLanguage: () -> Unit = {},
    onLogout: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val strings = LocalStrings.current
    val fingerprint = remember(owner.id) { container.fingerprint.fingerprint(owner.publicSigningKey) }
    val dynamicColorEnabled by getDynamicColorEnabled()
    val notificationsEnabled by getNotificationsEnabled()
    val notificationPrivacyEnabled by getNotificationPrivacyEnabled()
    val runInBackgroundEnabled by getRunInBackgroundEnabledCommon()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val backupIo = rememberBackupIo(container.vault) { outcome ->
        backupMessage = when (outcome) {
            is BackupOutcome.Exported -> strings.backupExported
            is BackupOutcome.Cancelled -> null
            is BackupOutcome.WrongPassphrase -> strings.backupWrongPassphrase
            is BackupOutcome.NotABackup -> strings.backupNotABackup
            is BackupOutcome.Damaged -> strings.backupDamaged
            else -> strings.backupFailed
        }
    }
    val clipboardManager = LocalClipboardManager.current
    var fingerprintCopied by remember { mutableStateOf(false) }
    val currentLocale by getLocalePreference()
    val stadeyVisible by getStadeyVisible()
    var showActivateStadeyConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pendingAvatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showRemoveAvatarConfirm by remember { mutableStateOf(false) }
    val avatarPicker = rememberMediaPickerLauncher(
        onImages = { images -> images.firstOrNull()?.let { pendingAvatarBytes = it } },
        onVideo = {},
        imagesOnly = true
    )

    LaunchedEffect(fingerprintCopied) {
        if (fingerprintCopied) {
            delay(2000)
            fingerprintCopied = false
        }
    }

    if (showBackupDialog) {
        BackupPassphraseDialog(
            title = strings.backupExportDialogTitle,
            body = strings.backupExportDialogBody,
            confirmLabel = strings.backupExportAction,
            requireConfirmation = true,
            onConfirm = { passphrase ->
                showBackupDialog = false
                backupIo.exportBackup(passphrase)
            },
            onDismiss = { showBackupDialog = false }
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text(strings.backupSection) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { backupMessage = null }) { Text(strings.closeAction) }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(strings.logoutDialogTitle) },
            text = {
                Text(
                    strings.logoutDialogBody,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(strings.deleteAndLogout) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showActivateStadeyConfirm) {
        AlertDialog(
            onDismissRequest = { showActivateStadeyConfirm = false },
            icon = {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text(strings.activateStadeyDialogTitle) },
            text = {
                Text(
                    strings.activateStadeyDialogBody,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        setStadeyVisible(true)
                        showActivateStadeyConfirm = false
                    }
                ) { Text(strings.activateStadeyConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showActivateStadeyConfirm = false }) { Text(strings.cancel) }
            }
        )
    }

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
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(strings.removeAvatarConfirmTitle) },
            text = {
                Text(
                    strings.removeAvatarConfirmBody,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.avatars.setMyAvatar(owner, null)
                        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {

            item {
                ProfileHeader(
                    owner = owner,
                    avatarBytes = owner.avatar,
                    fingerprint = fingerprint,
                    copied = fingerprintCopied,
                    onCopyFingerprint = {
                        clipboardManager.setText(AnnotatedString(fingerprint))
                        fingerprintCopied = true
                    },
                    onChangeAvatar = { avatarPicker.launch() },
                    onRemoveAvatarRequest = { showRemoveAvatarConfirm = true }
                )
            }

            if (isDynamicColorSupported || !stadeyVisible) {
                item {
                    SettingsSectionLabel(strings.appearanceSection)
                    SettingsGroup {
                        if (isDynamicColorSupported) {
                            row {
                                SwitchSettingsRow(
                                    icon = Icons.Default.Palette,
                                    iconTint = MaterialTheme.colorScheme.tertiary,
                                    title = strings.dynamicColorTitle,
                                    subtitle = strings.dynamicColorSubtitle,
                                    checked = dynamicColorEnabled,
                                    onCheckedChange = { setDynamicColorEnabled(it) }
                                )
                            }
                        }
                        if (!stadeyVisible) {
                            row {
                                NavigationSettingsRow(
                                    icon = Icons.Default.SmartToy,
                                    iconTint = MaterialTheme.colorScheme.tertiary,
                                    title = strings.activateStadeyTitle,
                                    subtitle = strings.activateStadeySubtitle,
                                    onClick = { showActivateStadeyConfirm = true }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionLabel(strings.languageSection)
                SettingsGroup {
                    row {
                        NavigationSettingsRow(
                            icon = Icons.Default.Translate,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = strings.languageTitle,
                            subtitle = localeDisplayName(currentLocale),
                            onClick = onOpenLanguage
                        )
                    }
                }
            }

            if (isNotificationSupported) {
                item {
                    SettingsSectionLabel(strings.notificationsSection)
                    val showSystemRow = isSystemNotificationSettingsSupported
                    val showPrivacyRow = notificationsEnabled

                    SettingsGroup {
                    row {
                        SwitchSettingsRow(
                            icon = if (notificationsEnabled) Icons.Default.Notifications
                                   else Icons.Default.NotificationsOff,
                            iconTint = if (notificationsEnabled) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            title = strings.messageNotificationsTitle,
                            subtitle = if (notificationsEnabled) strings.notificationsOnSubtitle
                                       else strings.notificationsOffSubtitle,
                            checked = notificationsEnabled,
                            onCheckedChange = { setNotificationsEnabled(it) }
                        )
                    }
                    if (showPrivacyRow) {
                        row {
                            SwitchSettingsRow(
                                icon = Icons.Default.VisibilityOff,
                                iconTint = MaterialTheme.colorScheme.primary,
                                title = strings.hideNotificationTitle,
                                subtitle = if (notificationPrivacyEnabled)
                                    strings.hiddenNotificationSubtitle
                                else
                                    strings.visibleNotificationSubtitle,
                                checked = notificationPrivacyEnabled,
                                onCheckedChange = { setNotificationPrivacyEnabled(it) }
                            )
                        }
                    }
                    if (showSystemRow) {
                        row {
                            NavigationSettingsRow(
                                icon = Icons.Default.OpenInNew,
                                iconTint = MaterialTheme.colorScheme.primary,
                                title = strings.systemNotificationsTitle,
                                subtitle = strings.systemNotificationsSubtitle,
                                onClick = { openNotificationSettings() }
                            )
                        }
                    }
                    }
                }
            }

            if (isRunInBackgroundSupported) {
                item {
                    SettingsSectionLabel(strings.runInBackgroundTitle)
                    SettingsGroup {
                        row {
                            SwitchSettingsRow(
                                icon = Icons.Default.Sync,
                                iconTint = MaterialTheme.colorScheme.secondary,
                                title = strings.runInBackgroundTitle,
                                subtitle = if (runInBackgroundEnabled) strings.runInBackgroundOnSubtitle
                                           else strings.runInBackgroundOffSubtitle,
                                checked = runInBackgroundEnabled,
                                onCheckedChange = { setRunInBackgroundEnabledCommon(it) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionLabel(strings.networkSection)
                SettingsGroup {
                    row {
                        NavigationSettingsRow(
                            icon = Icons.Default.SettingsEthernet,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = strings.transportLayersTitle,
                            subtitle = strings.transportLayersSubtitle,
                            onClick = onOpenTransports
                        )
                    }
                }
            }

            item {
                SettingsSectionLabel(strings.securitySection)
                SettingsGroup {
                    row {
                        NavigationSettingsRow(
                            icon = Icons.Default.Lock,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = strings.securitySettingsTitle,
                            subtitle = strings.securitySettingsSubtitle,
                            onClick = onOpenSecurity
                        )
                    }
                }
            }

            item { UpdateSettingsSection(container) }

            item {
                SettingsSectionLabel(strings.aboutSection)
                SettingsGroup {
                    row {
                        NavigationSettingsRow(
                            icon = Icons.Default.Info,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = strings.aboutTitle,
                            subtitle = strings.aboutSubtitle,
                            onClick = onOpenAbout
                        )
                    }
                }
            }

            item {
                SettingsSectionLabel(strings.backupSection)
                SettingsGroup {
                    row {
                        NavigationSettingsRow(
                            icon = Icons.Default.Save,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = strings.backupExportTitle,
                            subtitle = strings.backupExportSubtitle,
                            onClick = { showBackupDialog = true }
                        )
                    }
                }
            }

            item {
                SettingsSectionLabel(strings.accountSection)
                SettingsGroup {
                    row {
                        ActionSettingsRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = strings.logoutTitle,
                            subtitle = strings.logoutSubtitle,
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = { showLogoutDialog = true }
                        )
                    }
                }
            }
            }
            PlatformVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}


@Composable
private fun ProfileHeader(
    owner: LocalIdentity,
    avatarBytes: ByteArray?,
    fingerprint: String,
    copied: Boolean,
    onCopyFingerprint: () -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatarRequest: () -> Unit
) {
    val strings = LocalStrings.current
    val cardShape = RoundedCornerShape(16.dp)
    var showAvatarMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(top = 20.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (avatarBytes != null) showAvatarMenu = true else onChangeAvatar()
                        }
                ) {
                    Avatar(
                        name = owner.nickname,
                        keySeed = owner.publicSigningKey,
                        avatarBytes = avatarBytes,
                        size = 84.dp
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = strings.changeAvatarAction,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showAvatarMenu,
                    onDismissRequest = { showAvatarMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.changeAvatarAction) },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                        onClick = { showAvatarMenu = false; onChangeAvatar() }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.removeAvatarAction) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { showAvatarMenu = false; onRemoveAvatarRequest() }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                owner.nickname,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                strings.localIdentity,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .clickable { onCopyFingerprint() },
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { isCopied ->
                        Icon(
                            if (isCopied) Icons.Default.Check else Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = if (isCopied) 1f else 0.7f
                            )
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (copied) strings.fingerprintCopied else strings.fingerprintLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            fingerprint,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = if (copied) 1 else 3
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = strings.copyButton,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}


@Composable
internal fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp, end = 16.dp)
    )
}


@Composable
internal fun SwitchSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SettingsRowSurface(
        modifier = modifier,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsBadge(icon = icon, tint = iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}


@Composable
internal fun NavigationSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    SettingsRowSurface(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsBadge(icon = icon, tint = iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun ActionSettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    SettingsRowSurface(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsBadge(icon = icon, tint = iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


