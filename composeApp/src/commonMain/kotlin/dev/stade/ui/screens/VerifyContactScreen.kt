package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.AvatarViewerDialog
import dev.stade.ui.components.VanishDurationSheet
import dev.stade.ui.components.formatVanishRemaining
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.isTouchPrimaryInput
import dev.stade.ui.theme.StadeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyContactScreen(
    container: AppContainer,
    owner: LocalIdentity,
    contactId: String,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    var renameTick by remember(contactId) { mutableStateOf(0) }
    val contact = remember(contactId, renameTick) { container.contacts.get(contactId) }
    var showRenameDialog by remember(contactId) { mutableStateOf(false) }
    var renameText by remember(contactId) { mutableStateOf("") }
    val safety = remember(contact?.id) {
        contact?.let { container.fingerprint.safetyNumber(owner.publicSigningKey, it.publicSigningKey) }
    }
    var verified by remember(contact?.id) { mutableStateOf(contact?.verified == true) }
    val connected by container.sync.connectedContacts.collectAsState()
    val isOnline = contact != null && connected.contains(contact.id)
    val scope = rememberCoroutineScope()

    val customAvatar = contact?.avatar?.takeIf { it.isNotEmpty() }
    var showAvatarViewer by remember(contact?.id) { mutableStateOf(false) }

    val activeVanishSession by remember(contactId) {
        container.vanish.observeCurrentSession(contactId)
    }.collectAsState(initial = null)
    var showVanishDurationSheet by remember(contactId) { mutableStateOf(false) }
    var showVanishCancelDialog by remember(contactId) { mutableStateOf(false) }
    var vanishNow by remember(contactId) { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }

    LaunchedEffect(activeVanishSession?.sessionId) {
        if (activeVanishSession == null) return@LaunchedEffect
        while (true) {
            vanishNow = Clock.System.now().toEpochMilliseconds()
            delay(60_000L)
        }
    }

    if (showRenameDialog && contact != null) {
        val target = contact
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(strings.editAliasTitle) },
            text = {
                Column {
                    Text(strings.editAliasBody, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { if (it.length <= 40) renameText = it },
                        singleLine = true,
                        label = { Text(strings.editAliasLabel) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        container.contacts.rename(target.id, renameText.trim())
                        showRenameDialog = false
                        renameTick++
                    }
                ) { Text(strings.saveAction) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showAvatarViewer && customAvatar != null) {
        AvatarViewerDialog(avatarBytes = customAvatar, onDismiss = { showAvatarViewer = false })
    }

    if (showVanishDurationSheet && contact != null) {
        val c = contact
        VanishDurationSheet(
            onDismiss = { showVanishDurationSheet = false },
            onPick = { durationMs ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        runCatching { container.chat.startVanishMode(owner, c, durationMs) }
                    }
                    showVanishDurationSheet = false
                }
            }
        )
    }

    if (showVanishCancelDialog && contact != null) {
        val c = contact
        AlertDialog(
            onDismissRequest = { showVanishCancelDialog = false },
            title = { Text(strings.vanishTurnOffConfirmTitle) },
            text = { Text(strings.vanishTurnOffConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    showVanishCancelDialog = false
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            runCatching { container.chat.cancelVanishMode(owner, c) }
                        }
                    }
                }) { Text(strings.vanishTurnOffAction, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showVanishCancelDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(contact?.nickname ?: strings.profileTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(
                        name = contact?.nickname ?: "?",
                        modifier = if (customAvatar != null) {
                            Modifier.clip(CircleShape).clickable { showAvatarViewer = true }
                        } else {
                            Modifier
                        },
                        size = 64.dp,
                        keySeed = contact?.publicSigningKey,
                        avatarBytes = contact?.avatar
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(contact?.nickname ?: "", style = MaterialTheme.typography.titleMedium)
                        if (contact != null) {
                            Spacer(Modifier.width(2.dp))
                            IconButton(
                                onClick = {
                                    renameText = contact.nickname
                                    showRenameDialog = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = strings.editAliasTitle,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (contact != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(
                                    if (isOnline) StadeColors.online else StadeColors.offline
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isOnline) strings.online else strings.offline,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (verified) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                strings.verifiedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strings.safetyNumber,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    SafetyNumberBlock(safety ?: "")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        strings.safetyNumberNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (!isTouchPrimaryInput && contact != null) {
                val session = activeVanishSession
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = MaterialTheme.shapes.large,
                    onClick = {
                        if (session != null) showVanishCancelDialog = true else showVanishDurationSheet = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (session != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.vanishDurationSheetTitle,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (session != null) {
                                    strings.vanishStateOn(formatVanishRemaining(session.deadlineAtMs - vanishNow))
                                } else {
                                    strings.vanishStateOff
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (session != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !verified,
                onClick = {
                    container.contacts.verify(contactId)
                    verified = true
                }
            ) {
                Text(if (verified) strings.alreadyVerifiedLabel else strings.markAsVerified)
            }
        }
    }
}

@Composable
private fun SafetyNumberBlock(raw: String) {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Text("-", style = MaterialTheme.typography.titleSmall)
        return
    }
    val groups = digits.chunked(5)
    val perRow = if (groups.size >= 4) (groups.size + 3) / 4 else groups.size
    val rows = groups.chunked(perRow.coerceAtLeast(1))

    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Text(
                row.joinToString("  "),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}
