package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.security.BiometricAvailability
import dev.stade.security.BiometricOutcome
import dev.stade.security.clearBiometricUnlock
import dev.stade.security.rememberBiometricGate
import dev.stade.security.BiometricGate
import dev.stade.security.SessionTimeout
import dev.stade.security.getLockOnShutdownEnabled
import dev.stade.security.isLockOnShutdownSupported
import dev.stade.security.setLockOnShutdownEnabled
import androidx.compose.material.icons.filled.PowerSettingsNew
import dev.stade.ui.components.SettingsGroup
import dev.stade.ui.components.PlatformVerticalScrollbar
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPinSetup: (requireCurrent: Boolean) -> Unit,
    onOpenDuressPinSetup: () -> Unit
) {
    val lockEnabled = remember { container.secrets.isLockEnabled() }
    var pinVerified by remember { mutableStateOf(!lockEnabled) }

    if (!pinVerified) {
        SecurityPinGate(
            container = container,
            onVerified = { pinVerified = true },
            onBack = onBack
        )
        return
    }

    SecuritySettingsContent(
        container = container,
        onBack = onBack,
        onOpenPinSetup = onOpenPinSetup,
        onOpenDuressPinSetup = onOpenDuressPinSetup
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecuritySettingsContent(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPinSetup: (requireCurrent: Boolean) -> Unit,
    onOpenDuressPinSetup: () -> Unit
) {
    val strings = LocalStrings.current
    var refreshTick by remember { mutableStateOf(0) }
    val scrambleEnabled = remember(refreshTick) { container.secrets.isScrambleKeypadEnabled() }
    val sessionTimeout = remember(refreshTick) { container.secrets.sessionTimeoutSeconds() }
    val lockOnShutdown by getLockOnShutdownEnabled()
    val screenshotBlockingEnabled = remember(refreshTick) { container.secrets.isScreenshotBlockingEnabled() }
    val linkPreviewsEnabled = remember(refreshTick) { dev.stade.link.getLinkPreviewsEnabled(container.db) }
    var timeoutMenuOpen by remember { mutableStateOf(false) }
    var showNeverInfoDialog by remember { mutableStateOf(false) }
    val biometrics = rememberBiometricGate()
    val biometricAvail = biometrics.availability
    var biometricOn by remember(refreshTick) { mutableStateOf(biometrics.enrolled) }
    var showBiometricPinDialog by remember { mutableStateOf(false) }
    var biometricNotice by remember { mutableStateOf<String?>(null) }
    var showDuressInfoDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.securitySettingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
            ) {
                item {
                    SecuritySectionLabel(strings.pinSection)
                    SettingsGroup {
                        row {
                            SecurityNavRow(
                                icon = Icons.Default.Fingerprint,
                                tint = MaterialTheme.colorScheme.primary,
                                title = strings.changePinTitle,
                                subtitle = strings.changePinSubtitle,
                                onClick = { onOpenPinSetup(true) }
                            )
                        }
                        if (biometricAvail != BiometricAvailability.Unsupported) {
                            val ready = biometricAvail == BiometricAvailability.Ready
                            row {
                                SecuritySwitchRow(
                                    icon = Icons.Default.Fingerprint,
                                    tint = MaterialTheme.colorScheme.primary,
                                    title = strings.biometricUnlockTitle,
                                    subtitle = when {
                                        !ready -> strings.biometricNotEnrolledSubtitle
                                        biometricOn -> strings.biometricUnlockOnSubtitle
                                        else -> strings.biometricUnlockOffSubtitle
                                    },
                                    checked = biometricOn && ready,
                                    enabled = ready,
                                    onCheckedChange = { want ->
                                        if (want) {
                                            showBiometricPinDialog = true
                                        } else {
                                            biometrics.disable()
                                            biometricOn = false
                                        }
                                    }
                                )
                            }
                        }
                        if (isKeypadSupported) {
                            row {
                                SecuritySwitchRow(
                                    icon = Icons.Default.Grid3x3,
                                    tint = MaterialTheme.colorScheme.primary,
                                    title = strings.scrambleKeypadTitle,
                                    subtitle = if (scrambleEnabled) strings.scrambleKeypadOnSubtitle else strings.scrambleKeypadOffSubtitle,
                                    checked = scrambleEnabled,
                                    onCheckedChange = {
                                        container.secrets.setScrambleKeypadEnabled(it)
                                        refreshTick++
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    val duressSet = remember(refreshTick) { container.vault.hasDuressPin() }
                    val transportsLockEnabled = remember(refreshTick) { container.secrets.isTransportsLockEnabled() }
                    val conversationShortcutsEnabled by dev.stade.notification.getConversationShortcutsEnabled()
                    SecuritySectionLabel(strings.privacySection)
                    SettingsGroup {
                        row {
                            SecuritySwitchRow(
                                icon = Icons.Default.Link,
                                tint = MaterialTheme.colorScheme.primary,
                                title = strings.linkPreviewsSettingTitle,
                                subtitle = strings.linkPreviewsSettingSubtitle,
                                checked = linkPreviewsEnabled,
                                onCheckedChange = {
                                    dev.stade.link.setLinkPreviewsEnabled(container.db, it)
                                    refreshTick++
                                }
                            )
                        }
                        if (isScreenPrivacySupported) {
                            row {
                                SecuritySwitchRow(
                                    icon = Icons.Default.VisibilityOff,
                                    tint = MaterialTheme.colorScheme.primary,
                                    title = strings.screenshotBlockingTitle,
                                    subtitle = if (screenshotBlockingEnabled) strings.screenshotBlockingOnSubtitle else strings.screenshotBlockingOffSubtitle,
                                    checked = screenshotBlockingEnabled,
                                    onCheckedChange = {
                                        container.secrets.setScreenshotBlockingEnabled(it)
                                        refreshTick++
                                    }
                                )
                            }
                        }
                        row {
                            SecuritySwitchRow(
                                icon = Icons.Default.Lock,
                                tint = MaterialTheme.colorScheme.primary,
                                title = strings.transportsLockTitle,
                                subtitle = strings.transportsLockSubtitle,
                                checked = transportsLockEnabled,
                                onCheckedChange = {
                                    container.secrets.setTransportsLockEnabled(it)
                                    refreshTick++
                                }
                            )
                        }
                        if (dev.stade.notification.isConversationShortcutsSupported) {
                            row {
                                SecuritySwitchRow(
                                    icon = Icons.Default.TouchApp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    title = strings.conversationShortcutsTitle,
                                    subtitle = if (conversationShortcutsEnabled) strings.conversationShortcutsOnSubtitle else strings.conversationShortcutsOffSubtitle,
                                    checked = conversationShortcutsEnabled,
                                    onCheckedChange = {
                                        dev.stade.notification.setConversationShortcutsEnabled(it)
                                    }
                                )
                            }
                        }
                        row {
                            SecurityNavRow(
                                icon = Icons.Default.ReportProblem,
                                tint = MaterialTheme.colorScheme.error,
                                title = strings.duressPinTitle,
                                subtitle = if (duressSet) strings.duressPinSetSubtitle else strings.duressPinNotSetSubtitle,
                                onClick = onOpenDuressPinSetup,
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { showDuressInfoDialog = true }) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = strings.duressPinInfoTitle,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (duressSet) {
                                            IconButton(onClick = {
                                                container.vault.clearDuressPin()
                                                refreshTick++
                                            }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = strings.clearDuressPinAction,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    SecuritySectionLabel(strings.sessionSection)
                    SettingsGroup {
                        row {
                            Box {
                                SecurityNavRow(
                                    icon = Icons.Default.Timer,
                                    tint = MaterialTheme.colorScheme.primary,
                                    title = strings.autoLockTitle,
                                    subtitle = strings.autoLockSubtitle(strings.sessionTimeoutLabel(sessionTimeout)),
                                    onClick = { timeoutMenuOpen = true },
                                    trailingContent = {
                                        IconButton(onClick = { showNeverInfoDialog = true }) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = strings.autoLockNeverInfoTitle,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                                DropdownMenu(
                                    expanded = timeoutMenuOpen,
                                    onDismissRequest = { timeoutMenuOpen = false }
                                ) {
                                    SessionTimeout.OPTIONS.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(strings.sessionTimeoutLabel(opt)) },
                                            trailingIcon = {
                                                if (opt == sessionTimeout) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                container.secrets.setSessionTimeoutSeconds(opt)
                                                timeoutMenuOpen = false
                                                refreshTick++
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (isLockOnShutdownSupported) {
                            row {
                                SecuritySwitchRow(
                                    icon = Icons.Default.PowerSettingsNew,
                                    tint = MaterialTheme.colorScheme.primary,
                                    title = strings.lockOnShutdownTitle,
                                    subtitle = strings.lockOnShutdownSubtitle,
                                    checked = lockOnShutdown,
                                    onCheckedChange = { setLockOnShutdownEnabled(it) }
                                )
                            }
                        }
                    }
                }
            }

            if (showDuressInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showDuressInfoDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.ReportProblem,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = { Text(strings.duressPinInfoTitle) },
                    text = {
                        Text(
                            strings.duressPinInfoBody,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showDuressInfoDialog = false }) {
                            Text(strings.understood)
                        }
                    }
                )
            }
            if (showBiometricPinDialog) {
                BiometricEnrollDialog(
                    container = container,
                    gate = biometrics,
                    onDismiss = { showBiometricPinDialog = false },
                    onEnabled = {
                        showBiometricPinDialog = false
                        biometricOn = true
                        biometricNotice = strings.biometricEnabledNotice
                    },
                    onFailed = { message ->
                        showBiometricPinDialog = false
                        biometricOn = false
                        biometricNotice = message
                    }
                )
            }
            biometricNotice?.let { notice ->
                LaunchedEffect(notice) {
                    delay(2600)
                    biometricNotice = null
                }
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 6.dp
                    ) {
                        Text(
                            notice,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }
            }
            if (showNeverInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showNeverInfoDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text(strings.autoLockNeverInfoTitle) },
                    text = {
                        Text(
                            strings.autoLockNeverInfoBody,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showNeverInfoDialog = false }) {
                            Text(strings.understood)
                        }
                    }
                )
            }
            PlatformVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun SecuritySectionLabel(title: String) {
    SettingsSectionLabel(title)
}


@Composable
private fun SecurityNavRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    NavigationSettingsRow(
        icon = icon,
        iconTint = tint,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        trailingContent = trailingContent
    )
}

@Composable
private fun SecuritySwitchRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SwitchSettingsRow(
        icon = icon,
        iconTint = tint,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPinGate(
    container: AppContainer,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val shakeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val keyFocusRequester = remember { FocusRequester() }
    val biometrics = rememberBiometricGate()
    var biometricPrompting by remember { mutableStateOf(false) }
    var biometricOffered by remember { mutableStateOf(false) }
    val biometricUsable = biometrics.enrolled && biometrics.availability == BiometricAvailability.Ready

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { keyFocusRequester.requestFocus() }
    }

    fun promptBiometric() {
        if (biometricPrompting || isVerifying || !biometricUsable) return
        biometricPrompting = true
        biometrics.authenticate(
            title = strings.biometricPromptTitle,
            subtitle = strings.biometricPromptSubtitle,
            pinFallbackLabel = strings.biometricUsePinAction
        ) { outcome ->
            biometricPrompting = false
            when (outcome) {
                is BiometricOutcome.Unlocked -> {
                    isVerifying = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            container.secrets.verifyPin(outcome.pin)
                        }
                        isVerifying = false
                        if (ok) {
                            onVerified()
                        } else {
                            clearBiometricUnlock()
                            error = strings.biometricResetNotice
                        }
                    }
                }
                BiometricOutcome.Reset -> error = strings.biometricResetNotice
                is BiometricOutcome.Failed -> error = outcome.message ?: strings.biometricFailedNotice
                else -> Unit
            }
        }
    }

    LaunchedEffect(biometricUsable) {
        if (biometricUsable && !biometricOffered) {
            biometricOffered = true
            delay(220)
            promptBiometric()
        }
    }

    fun tryVerify() {
        if (pin.length < 4 || isVerifying || error != null) return
        val snap = pin
        isVerifying = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { container.secrets.verifyPin(snap) }
            isVerifying = false
            if (ok) {
                onVerified()
            } else {
                error = strings.wrongCurrentPin
                launch {
                    shakeOffset.animateTo(0f, androidx.compose.animation.core.keyframes {
                        durationMillis = 420
                        0f at 0
                        -12f at 55
                        12f at 110
                        -10f at 165
                        10f at 220
                        -6f at 275
                        6f at 330
                        0f at 420
                    })
                }
                delay(700)
                pin = ""
                error = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.securitySettingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .focusRequester(keyFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val digit = when (keyEvent.key) {
                            Key.Zero, Key.NumPad0 -> "0"
                            Key.One, Key.NumPad1 -> "1"
                            Key.Two, Key.NumPad2 -> "2"
                            Key.Three, Key.NumPad3 -> "3"
                            Key.Four, Key.NumPad4 -> "4"
                            Key.Five, Key.NumPad5 -> "5"
                            Key.Six, Key.NumPad6 -> "6"
                            Key.Seven, Key.NumPad7 -> "7"
                            Key.Eight, Key.NumPad8 -> "8"
                            Key.Nine, Key.NumPad9 -> "9"
                            else -> null
                        }
                        when {
                            digit != null -> {
                                if (!isVerifying && pin.length < 16 && error == null) {
                                    pin += digit
                                    if (pin.length >= 16) tryVerify()
                                }
                                true
                            }
                            keyEvent.key == Key.Backspace -> {
                                if (pin.isNotEmpty() && !isVerifying) pin = pin.dropLast(1)
                                true
                            }
                            keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter -> {
                                tryVerify()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(strings.enterCurrentPinTitle, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                strings.enterCurrentPinSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    PinDots(
                        filled = pin.length,
                        shakeOffset = shakeOffset.value,
                        error = error != null
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (isKeypadSupported) {
                val digits = (1..9).map { it.toString() }
                val rows = listOf(digits.subList(0, 3), digits.subList(3, 6), digits.subList(6, 9))
                val haptic = LocalHapticFeedback.current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { d ->
                                GatePadButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isVerifying && error == null && pin.length < 16) {
                                        pin += d
                                        if (pin.length >= 16) tryVerify()
                                    }
                                }) {
                                    Text(d, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val canConfirm = pin.length >= 4 && !isVerifying && error == null
                        Surface(
                            modifier = Modifier.size(68.dp).clip(CircleShape).clickable(enabled = canConfirm) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tryVerify()
                            },
                            color = if (canConfirm) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Check, contentDescription = strings.confirmAction,
                                    tint = if (canConfirm) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        GatePadButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isVerifying && error == null && pin.length < 16) {
                                pin += "0"
                                if (pin.length >= 16) tryVerify()
                            }
                        }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
                        GatePadButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (pin.isNotEmpty() && !isVerifying) pin = pin.dropLast(1)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = strings.backspaceAction,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            if (biometricUsable) {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { promptBiometric() },
                    enabled = !isVerifying && !biometricPrompting
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.biometricUseFingerprintAction)
                }
            }
        }
    }
}

@Composable
private fun GatePadButton(
    onClick: () -> Unit,
    size: Dp = 68.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun BiometricEnrollDialog(
    container: AppContainer,
    gate: BiometricGate,
    onDismiss: () -> Unit,
    onEnabled: () -> Unit,
    onFailed: (String?) -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    fun submit() {
        if (busy || pin.length < 4) return
        busy = true
        wrong = false
        val candidate = pin
        scope.launch {
            val ok = withContext(Dispatchers.Default) { container.secrets.verifyPin(candidate) }
            if (!ok) {
                busy = false
                wrong = true
                return@launch
            }
            gate.enable(
                pin = candidate,
                title = strings.biometricUnlockTitle,
                subtitle = strings.biometricEnablePromptSubtitle,
                cancelLabel = strings.cancel
            ) { outcome ->
                busy = false
                when (outcome) {
                    is BiometricOutcome.Unlocked -> onEnabled()
                    BiometricOutcome.FellBackToPin -> onDismiss()
                    is BiometricOutcome.Failed -> onFailed(outcome.message ?: strings.biometricFailedNotice)
                    else -> onFailed(strings.biometricFailedNotice)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(
                Icons.Default.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(strings.biometricConfirmPinTitle) },
        text = {
            Column {
                Text(strings.biometricConfirmPinBody, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PinDots(filled = pin.length, shakeOffset = 0f, error = wrong)
                }
                if (wrong) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        strings.wrongCurrentPin,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(14.dp))
                val haptic = LocalHapticFeedback.current
                fun press(digit: String) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!busy && pin.length < 16) {
                        pin += digit
                        wrong = false
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { d ->
                                GatePadButton(onClick = { press(d) }, size = 56.dp) {
                                    Text(d, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.size(56.dp))
                        GatePadButton(onClick = { press("0") }, size = 56.dp) {
                            Text("0", style = MaterialTheme.typography.titleMedium)
                        }
                        GatePadButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (pin.isNotEmpty() && !busy) pin = pin.dropLast(1)
                            },
                            size = 56.dp
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = strings.backspaceAction,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = !busy && pin.length >= 4) {
                Text(strings.continueAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) }
        }
    )
}
