package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.stade.ui.components.HomeIdentityHeader
import dev.stade.ui.components.LocalHomeBarClearance
import dev.stade.AppContainer
import dev.stade.contact.InviteParseResult
import dev.stade.identity.LocalIdentity
import dev.stade.share.isShareSheetSupported
import dev.stade.share.shareFile
import dev.stade.transport.TransportType
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.promoteOrAlreadyAdded
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val torPlugin = remember { container.transports.get(TransportType.TOR) }
    val torInfo by remember(torPlugin) {
        torPlugin?.info ?: kotlinx.coroutines.flow.MutableStateFlow(null)
    }.collectAsState(initial = null)

    var selfAddrs by remember { mutableStateOf(container.connections.selfAddresses()) }
    var invite by remember { mutableStateOf(container.handshake.createInvite(owner, selfAddrs)) }
    LaunchedEffect(torInfo?.running) {
        selfAddrs = container.connections.selfAddresses()
        invite = container.handshake.createInvite(owner, selfAddrs)
    }
    val inviteHasTor = selfAddrs.any { it.startsWith("tor://") }
    var alias by remember { mutableStateOf("") }
    var pastedCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var statusSticky by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    val pendingInvite by container.pendingInvite.collectAsState()
    val pendingDials by container.connections.pendingDials.collectAsState()
    var dialingTargetAddrs by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(pendingInvite) {
        val p = pendingInvite
        if (!p.isNullOrBlank() && pastedCode.isBlank()) {
            pastedCode = p
            status = strings.pendingInviteOpened
            statusSticky = true
            container.pendingInvite.value = null
        }
    }

    LaunchedEffect(status, statusSticky) {
        if (status != null && !statusSticky) {
            delay(8000)
            status = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    if (embedded) {
                        Text(strings.addContactTitle, style = MaterialTheme.typography.titleMedium)
                    } else {
                        HomeIdentityHeader(container = container, owner = owner)
                    }
                },
            )
        }
    ) { padding ->
        val density = LocalDensity.current
        val keyboardBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
        val systemBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        val restingBottom = LocalHomeBarClearance.current + systemBottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp)
                .padding(bottom = maxOf(restingBottom, keyboardBottom)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepCard(stepNumber = 1, title = strings.step1Title) {
                Text(
                    strings.step1Description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!inviteHasTor) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.inviteNotReadyForRemote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    enabled = inviteHasTor,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        scope.launch {
                            shareFile(
                                invite.display.encodeToByteArray(),
                                "invite.stadeid",
                                "application/octet-stream",
                                strings.shareInviteFileAction
                            )
                        }
                    }
                ) {
                    Text(if (isShareSheetSupported) strings.shareInviteFileAction else strings.saveInviteFileAction)
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    enabled = inviteHasTor,
                    onClick = {
                        clipboard.setText(AnnotatedString(invite.display))
                        status = strings.inviteCodeCopied(invite.display.length)
                        statusSticky = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.copyInviteCode(invite.display.length))
                }
            }

            StepCard(stepNumber = 2, title = strings.step2Title) {
                OutlinedTextField(
                    value = pastedCode,
                    onValueChange = { pastedCode = it },
                    label = { Text(strings.inviteCodeLabel) },
                    placeholder = { Text("STADE2-…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipped = clipboard.getText()?.text
                            if (!clipped.isNullOrEmpty()) pastedCode = clipped
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = strings.pasteButton)
                        }
                    },
                    supportingText = {
                        val n = pastedCode.replace(Regex("[^A-Za-z0-9]"), "").length
                        Text(strings.charCount(n))
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    strings.inviteFileImportHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(strings.contactNameLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    enabled = pastedCode.isNotBlank() && alias.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        container.appScope.launch {
                            try {
                                val trimmed = pastedCode.trim()
                                val looksLikeStadeId = Regex("^STADE-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}-[0-9A-Za-z]{4}$")
                                    .matches(trimmed.uppercase())
                                if (looksLikeStadeId) {
                                    status = strings.inviteCodeIsStadeId
                                    return@launch
                                }
                                val result = container.handshake.parseInviteDetailed(trimmed)
                                val parsed = when (result) {
                                    is InviteParseResult.Ok -> result.payload
                                    is InviteParseResult.MissingPrefix -> {
                                        status = strings.inviteMissingPrefix(result.firstChars)
                                        return@launch
                                    }
                                    is InviteParseResult.TooShort -> {
                                        status = strings.inviteTooShort(result.actual, result.expected)
                                        return@launch
                                    }
                                    is InviteParseResult.TrailingBytes -> {
                                        status = strings.inviteTrailingBytes(result.extra)
                                        return@launch
                                    }
                                    InviteParseResult.BadMagic -> {
                                        status = strings.inviteBadMagic
                                        return@launch
                                    }
                                    is InviteParseResult.BadVersion -> {
                                        status = strings.inviteBadVersion(result.version)
                                        return@launch
                                    }
                                    is InviteParseResult.BadNickname -> {
                                        status = strings.inviteBadNickname(result.length)
                                        return@launch
                                    }
                                    is InviteParseResult.BadAddressBlob -> {
                                        status = strings.inviteBadAddressBlob(result.length)
                                        return@launch
                                    }
                                    InviteParseResult.EdVerifyFail -> {
                                        status = strings.inviteEdVerifyFail
                                        return@launch
                                    }
                                    InviteParseResult.MlDsaVerifyFail -> {
                                        status = strings.inviteMlDsaVerifyFail
                                        return@launch
                                    }
                                    is InviteParseResult.DecodeError -> {
                                        status = strings.inviteDecodeError(result.cause)
                                        return@launch
                                    }
                                }
                                if (parsed.signingPublicKey.contentEquals(owner.publicSigningKey)) {
                                    status = strings.selfInviteError
                                    return@launch
                                }
                                val existingContact = container.contacts.findByStadeId(parsed.stadeId)
                                if (existingContact != null) {
                                    val trimmedAlias = alias.trim()
                                    if (trimmedAlias.isNotEmpty()) {
                                        runCatching { container.contacts.rename(existingContact.id, trimmedAlias) }
                                    }
                                    val renamed = if (trimmedAlias.isNotEmpty()) existingContact.copy(nickname = trimmedAlias) else existingContact
                                    status = container.promoteOrAlreadyAdded(owner, renamed, strings)
                                    return@launch
                                }
                                container.sync.unforget(parsed.stadeId)
                                val addrs = parsed.addresses
                                if (addrs.isEmpty()) {
                                    status = strings.inviteAcceptedNoAddr
                                    statusSticky = true
                                } else {
                                    val lanOnly = addrs.none { it.startsWith("tor://") }
                                    container.connections.queueDial(addrs)
                                    dialingTargetAddrs = addrs.toSet()
                                    status = if (lanOnly)
                                        strings.inviteAccepted(parsed.nickname, addrs.size) +
                                            "\n" + strings.inviteLanOnlyWarning
                                    else
                                        strings.inviteAccepted(parsed.nickname, addrs.size)
                                    statusSticky = true
                                    val targetId = parsed.stadeId

                                    val added = withTimeoutOrNull(5 * 60_000L) {
                                        container.contacts.observeContacts(owner.id).first { list ->
                                            list.any { it.id == targetId }
                                        }
                                        true
                                    } ?: false
                                    if (added || container.contacts.findByStadeId(targetId) != null) {
                                        val trimmedAlias = alias.trim()
                                        if (trimmedAlias.isNotEmpty()) {
                                            runCatching { container.contacts.rename(targetId, trimmedAlias) }
                                        }
                                        val displayName = if (trimmedAlias.isNotEmpty()) trimmedAlias else parsed.nickname
                                        status = strings.contactAdded(displayName)
                                        statusSticky = true
                                        dialingTargetAddrs = emptySet()
                                    } else {
                                        container.connections.cancelPendingDial(addrs)
                                        status = strings.connectionTimeout
                                        statusSticky = true
                                    }
                                }
                                pastedCode = ""
                                alias = ""
                            } catch (e: Exception) {
                                status = strings.error(e.message ?: "")
                            }
                        }
                    }
                ) { Text(strings.acceptInvite) }
                if (dialingTargetAddrs.isNotEmpty()) {
                    val activeAttempts = pendingDials.filterKeys { it in dialingTargetAddrs }.values
                    if (activeAttempts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column {
                            for (a in activeAttempts.sortedByDescending { it.timestamp }) {
                                Text(
                                    "• ${a.address.take(40)}…  —  ${a.detail ?: a.status.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stepNumber.toString(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
