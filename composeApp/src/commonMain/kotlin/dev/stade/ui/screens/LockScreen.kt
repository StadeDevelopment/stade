package dev.stade.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stade.security.BiometricAvailability
import dev.stade.security.BiometricOutcome
import dev.stade.security.clearBiometricUnlock
import dev.stade.security.rememberBiometricGate
import dev.stade.security.UnlockOutcome
import dev.stade.security.Vault
import dev.stade.ui.components.BrandMark
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_MIN = 4
private const val PIN_MAX = 16
private const val PIN_DOTS_MAX = 8

private suspend fun Animatable<Float, *>.shake() {
    animateTo(0f, keyframes {
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

@Composable
fun LockScreen(
    vault: Vault,
    onUnlocked: () -> Unit,
    onForgotPin: () -> Unit = {},
    onPrepareWipe: (suspend () -> Unit)? = null,
    onDuressTriggered: (suspend () -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var lockoutUntil by remember { mutableStateOf(vault.lockoutUntilMillis()) }
    var nowTick by remember { mutableStateOf(vault.nowMillis()) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scrambleEnabled = remember { vault.isScrambleKeypadEnabled() }
    val strings = LocalStrings.current
    val keyFocusRequester = remember { FocusRequester() }
    val biometrics = rememberBiometricGate()
    var biometricPrompting by remember { mutableStateOf(false) }
    var biometricOffered by remember { mutableStateOf(false) }
    val biometricUsable = biometrics.enrolled && biometrics.availability == BiometricAvailability.Ready

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { keyFocusRequester.requestFocus() }
    }

    LaunchedEffect(lockoutUntil) {
        while (lockoutUntil > vault.nowMillis()) {
            nowTick = vault.nowMillis()
            delay(500)
        }
        nowTick = vault.nowMillis()
    }

    val lockedNow = lockoutUntil > nowTick

    fun unlockWith(candidate: String, onWrong: () -> Unit) {
        isVerifying = true
        scope.launch {
            val outcome = withContext(Dispatchers.Default) { vault.unlock(candidate) }
            isVerifying = false
            if (outcome is UnlockOutcome.Success) onUnlocked() else onWrong()
        }
    }

    fun promptBiometric() {
        if (biometricPrompting || isVerifying || lockedNow || !biometricUsable) return
        biometricPrompting = true
        biometrics.authenticate(
            title = strings.biometricPromptTitle,
            subtitle = strings.biometricPromptSubtitle,
            pinFallbackLabel = strings.biometricUsePinAction
        ) { outcome ->
            biometricPrompting = false
            when (outcome) {
                is BiometricOutcome.Unlocked -> unlockWith(outcome.pin) {
                    clearBiometricUnlock()
                    error = strings.biometricResetNotice
                }
                BiometricOutcome.Reset -> error = strings.biometricResetNotice
                is BiometricOutcome.Failed -> error = outcome.message ?: strings.biometricFailedNotice
                else -> Unit
            }
        }
    }

    LaunchedEffect(biometricUsable) {
        if (biometricUsable && !biometricOffered && !lockedNow) {
            biometricOffered = true
            delay(220)
            promptBiometric()
        }
    }

    fun tryUnlock() {
        if (pin.length < PIN_MIN || error != null || isVerifying) return
        val currentPin = pin
        val wasLocked = lockedNow
        isVerifying = true
        scope.launch {
            val isDuress = withContext(Dispatchers.Default) { vault.isDuressPin(currentPin) }
            if (isDuress && onDuressTriggered != null) {
                withContext(Dispatchers.Default) { onDuressTriggered() }
                isVerifying = false
                return@launch
            }
            if (wasLocked) {
                isVerifying = false
                launch { shakeOffset.shake() }
                delay(700)
                pin = ""
                return@launch
            }
            val outcome = withContext(Dispatchers.Default) { vault.unlock(currentPin) }
            isVerifying = false
            when (outcome) {
                UnlockOutcome.Success -> onUnlocked()
                is UnlockOutcome.Wrong -> {
                    error = if (outcome.remainingBeforeLockout > 0) {
                        strings.wrongPinRemaining(outcome.remainingBeforeLockout)
                    } else {
                        strings.wrongPin
                    }
                    launch { shakeOffset.shake() }
                    lockoutUntil = vault.lockoutUntilMillis()
                    delay(700)
                    pin = ""
                    error = null
                }
                is UnlockOutcome.LockedOut -> {
                    lockoutUntil = outcome.untilMillis
                    error = null
                    pin = ""
                }
                UnlockOutcome.NotInitialized -> {
                    error = strings.vaultNotInitialized
                    delay(700)
                    pin = ""
                    error = null
                }
                is UnlockOutcome.Error -> {
                    error = outcome.message
                    launch { shakeOffset.shake() }
                    delay(900)
                    pin = ""
                    error = null
                }
            }
        }
    }

    Scaffold { padding ->
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
                                if (pin.length < PIN_MAX && error == null && !isVerifying) {
                                    pin += digit
                                    if (pin.length >= PIN_MAX) tryUnlock()
                                }
                                true
                            }
                            keyEvent.key == Key.Backspace -> {
                                if (pin.isNotEmpty() && !isVerifying) pin = pin.dropLast(1)
                                true
                            }
                            keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter -> {
                                tryUnlock()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 72.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (lockedNow) strings.tooManyAttemptsSubtitle else strings.unlockSubtitle,
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
            Box(modifier = Modifier.height(20.dp)) {
                when {
                    lockedNow -> {
                        val remaining = ((lockoutUntil - nowTick) / 1000L).coerceAtLeast(0L)
                        Text(
                            strings.retryIn(strings.formatRemainingTime(remaining)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    error != null -> Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (isKeypadSupported) {
                PinKeypad(
                    onDigit = { d ->
                        if (pin.length < PIN_MAX && error == null && !isVerifying) {
                            pin += d
                            if (pin.length >= PIN_MAX) tryUnlock()
                        }
                    },
                    onBackspace = {
                        if (pin.isNotEmpty() && !isVerifying) pin = pin.dropLast(1)
                    },
                    actionIsCheck = pin.length >= PIN_MIN && error == null && !isVerifying,
                    onAction = { tryUnlock() },
                    scrambled = scrambleEnabled
                )
            }
            if (!isKeypadSupported) {
                PinConfirmAction(
                    enabled = pin.length >= PIN_MIN && error == null && !isVerifying,
                    label = strings.confirmAction,
                    onClick = { tryUnlock() }
                )
            }
            Spacer(Modifier.height(16.dp))
            if (biometricUsable) {
                TextButton(
                    onClick = { promptBiometric() },
                    enabled = !wiping && !lockedNow && !isVerifying && !biometricPrompting
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.biometricUseFingerprintAction)
                }
            }
            TextButton(onClick = { showForgotDialog = true }, enabled = !wiping) {
                Text(strings.forgotPin)
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { if (!wiping) showForgotDialog = false },
            icon = {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(strings.resetPinTitle) },
            text = {
                Text(
                    strings.resetPinBody,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (wiping) return@Button
                        wiping = true
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                runCatching { onPrepareWipe?.invoke() }
                                runCatching { clearBiometricUnlock() }
                                vault.wipe()
                            }
                            showForgotDialog = false
                            wiping = false
                            onForgotPin()
                        }
                    },
                    enabled = !wiping,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(if (wiping) strings.wiping else strings.resetAndWipe) }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }, enabled = !wiping) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

enum class PinSetupMode { Primary, Duress }

@Composable
fun PinSetupScreen(
    vault: Vault,
    requireCurrent: Boolean = false,
    mode: PinSetupMode = PinSetupMode.Primary,
    tip: String? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    var phase by remember { mutableStateOf(if (requireCurrent) Phase.Current else Phase.New) }
    var currentPin by remember { mutableStateOf("") }
    var savedCurrent by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val keyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { keyFocusRequester.requestFocus() }
    }

    val currentInput = when (phase) {
        Phase.Current -> currentPin
        Phase.New -> newPin
        Phase.Confirm -> confirmPin
    }

    fun verifyCurrentAndAdvance() {
        if (currentPin.length < PIN_MIN || error != null || isVerifying) return
        val snap = currentPin
        isVerifying = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                vault.unlock(snap) is UnlockOutcome.Success
            }
            isVerifying = false
            if (ok) {
                savedCurrent = snap
                phase = Phase.New
                currentPin = ""
                error = null
            } else {
                error = strings.wrongCurrentPin
                launch { shakeOffset.shake() }
                delay(700)
                currentPin = ""
                error = null
            }
        }
    }

    LaunchedEffect(confirmPin, phase) {
        if (phase == Phase.Confirm && confirmPin.length == newPin.length) {
            if (mode == PinSetupMode.Duress && confirmPin == savedCurrent) {
                error = strings.duressPinMatchesRealPin
                launch { shakeOffset.shake() }
                delay(800)
                confirmPin = ""
                error = null
            } else if (confirmPin == newPin) {
                isVerifying = true
                val ok = withContext(Dispatchers.Default) {
                    when {
                        mode == PinSetupMode.Duress -> {
                            runCatching { vault.setDuressPin(newPin) }.isSuccess
                        }
                        requireCurrent -> vault.changePassword(savedCurrent, newPin)
                        else -> runCatching { vault.setup(newPin) }.isSuccess
                    }
                }
                isVerifying = false
                if (ok) {
                    if (mode != PinSetupMode.Duress) clearBiometricUnlock()
                    onDone()
                } else {
                    error = strings.pinChangeFailed
                    launch { shakeOffset.shake() }
                    delay(800)
                    confirmPin = ""
                    error = null
                }
            } else {
                error = strings.pinMismatch
                launch { shakeOffset.shake() }
                delay(700)
                confirmPin = ""
                error = null
            }
        }
    }

    val heading = when {
        mode == PinSetupMode.Duress && phase == Phase.New -> strings.setDuressPinTitle
        mode == PinSetupMode.Duress && phase == Phase.Confirm -> strings.confirmDuressPinTitle
        phase == Phase.Current -> strings.enterCurrentPinTitle
        phase == Phase.New -> strings.setNewPinTitle
        else -> strings.confirmPinTitle
    }
    val sub = when {
        mode == PinSetupMode.Duress && phase == Phase.New -> strings.setDuressPinSubtitle
        phase == Phase.Current -> strings.enterCurrentPinSubtitle
        phase == Phase.New -> strings.setPinSubtitle(PIN_MIN, PIN_MAX)
        else -> strings.confirmPinSubtitle
    }

    Scaffold { padding ->
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
                                when (phase) {
                                    Phase.Current -> if (currentPin.length < PIN_MAX && error == null && !isVerifying) {
                                        currentPin += digit
                                        if (currentPin.length >= PIN_MAX) verifyCurrentAndAdvance()
                                    }
                                    Phase.New -> if (newPin.length < PIN_MAX) {
                                        newPin += digit
                                        if (newPin.length >= PIN_MAX) phase = Phase.Confirm
                                    }
                                    Phase.Confirm -> if (confirmPin.length < newPin.length && error == null) confirmPin += digit
                                }
                                true
                            }
                            keyEvent.key == Key.Backspace -> {
                                when (phase) {
                                    Phase.Current -> if (currentPin.isNotEmpty() && !isVerifying) currentPin = currentPin.dropLast(1)
                                    Phase.New -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
                                    Phase.Confirm -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                }
                                true
                            }
                            keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter -> {
                                when (phase) {
                                    Phase.Current -> verifyCurrentAndAdvance()
                                    Phase.New -> if (newPin.length >= PIN_MIN) phase = Phase.Confirm
                                    Phase.Confirm -> {}
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(heading, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            )
            if (tip != null && phase == Phase.New) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tip,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    PinDots(
                        filled = currentInput.length,
                        shakeOffset = shakeOffset.value,
                        error = error != null
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.height(20.dp)) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (isKeypadSupported) {
                PinKeypad(
                    onDigit = { d ->
                        when (phase) {
                            Phase.Current -> if (currentPin.length < PIN_MAX && error == null && !isVerifying) {
                                currentPin += d
                                if (currentPin.length >= PIN_MAX) verifyCurrentAndAdvance()
                            }
                            Phase.New -> if (newPin.length < PIN_MAX) {
                                newPin += d
                                if (newPin.length >= PIN_MAX) phase = Phase.Confirm
                            }
                            Phase.Confirm -> if (confirmPin.length < newPin.length && error == null) confirmPin += d
                        }
                    },
                    onBackspace = {
                        when (phase) {
                            Phase.Current -> if (currentPin.isNotEmpty() && !isVerifying) currentPin = currentPin.dropLast(1)
                            Phase.New -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
                            Phase.Confirm -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        }
                    },
                    actionLabel = null,
                    actionIsCheck = when (phase) {
                        Phase.Current -> currentPin.length >= PIN_MIN && error == null && !isVerifying
                        Phase.New -> newPin.length >= PIN_MIN
                        Phase.Confirm -> false
                    },
                    onAction = {
                        when (phase) {
                            Phase.Current -> verifyCurrentAndAdvance()
                            Phase.New -> if (newPin.length >= PIN_MIN) phase = Phase.Confirm
                            Phase.Confirm -> {}
                        }
                    },
                    cancelLabel = if (requireCurrent) strings.cancel else null,
                    onCancel = onCancel
                )
            }
            if (!isKeypadSupported) {
                PinConfirmAction(
                    enabled = when (phase) {
                        Phase.Current -> currentPin.length >= PIN_MIN && error == null && !isVerifying
                        Phase.New -> newPin.length >= PIN_MIN
                        Phase.Confirm -> false
                    },
                    label = strings.continueAction,
                    onClick = {
                        when (phase) {
                            Phase.Current -> verifyCurrentAndAdvance()
                            Phase.New -> if (newPin.length >= PIN_MIN) phase = Phase.Confirm
                            Phase.Confirm -> {}
                        }
                    },
                    visible = phase != Phase.Confirm
                )
            }
            if (!isKeypadSupported && requireCurrent) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel, enabled = !isVerifying) {
                    Text(strings.cancel)
                }
            }
        }
    }
}

@Composable
private fun PinConfirmAction(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    visible: Boolean = true
) {
    val strings = LocalStrings.current
    if (!visible) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(min = 200.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            strings.pressEnterHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
        )
    }
}

private enum class Phase { Current, New, Confirm }

@Composable
internal fun PinDots(filled: Int, shakeOffset: Float, error: Boolean) {
    val visibleDots = filled.coerceIn(PIN_MIN, PIN_DOTS_MAX)
    val overflow = (filled - PIN_DOTS_MAX).coerceAtLeast(0)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.graphicsLayer { translationX = shakeOffset }
    ) {
        repeat(visibleDots) { i ->
            val isFilled = i < filled
            val color = when {
                error -> MaterialTheme.colorScheme.error
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .size(if (isFilled) 16.dp else 14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        if (overflow > 0) {
            Text(
                "+$overflow",
                style = MaterialTheme.typography.labelMedium,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    actionLabel: String? = null,
    actionIsCheck: Boolean = false,
    onAction: () -> Unit = {},
    cancelLabel: String? = null,
    onCancel: () -> Unit = {},
    scrambled: Boolean = false
) {
    val strings = LocalStrings.current
    val digits: List<String> = remember(scrambled) {
        val all = (1..9).map { it.toString() } + "0"
        if (scrambled) all.shuffled() else all
    }
    val topDigits = digits.take(9)
    val bottomMid = digits[9]
    val rows = listOf(
        topDigits.subList(0, 3),
        topDigits.subList(3, 6),
        topDigits.subList(6, 9)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { d -> KeyButton(label = d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                actionIsCheck -> KeyButton(checkIcon = true, isAction = true, confirmDesc = strings.confirmAction) { onAction() }
                actionLabel != null -> KeyButton(label = actionLabel, isAction = true) { onAction() }
                cancelLabel != null -> KeyButton(label = cancelLabel, isAction = false, small = true) { onCancel() }
                else -> Spacer(Modifier.width(72.dp).height(72.dp))
            }
            KeyButton(label = bottomMid) { onDigit(bottomMid) }
            KeyButton(icon = true, backspaceDesc = strings.backspaceAction) { onBackspace() }
        }
    }
}

@Composable
private fun KeyButton(
    label: String = "",
    icon: Boolean = false,
    checkIcon: Boolean = false,
    isAction: Boolean = false,
    small: Boolean = false,
    confirmDesc: String = "",
    backspaceDesc: String = "",
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bg = if (isAction) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (isAction) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        color = bg,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when {
                checkIcon -> Icon(Icons.Filled.Check, contentDescription = confirmDesc, tint = fg)
                icon -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = backspaceDesc, tint = fg)
                else -> Text(
                    label,
                    style = if (small) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.headlineSmall,
                    color = fg
                )
            }
        }
    }
}
