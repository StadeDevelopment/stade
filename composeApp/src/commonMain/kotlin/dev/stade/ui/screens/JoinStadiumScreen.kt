package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import dev.stade.ui.components.HomeIdentityHeader
import dev.stade.ui.components.LocalHomeBarClearance
import dev.stade.AppContainer
import dev.stade.contact.InviteParseResult
import dev.stade.identity.LocalIdentity
import dev.stade.stadium.PendingStadiumJoin
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.inviteErrorText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinStadiumScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    onJoined: (String) -> Unit = {},
    embedded: Boolean = false
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var pastedCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
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
                        Text(strings.joinStadiumAction, style = MaterialTheme.typography.titleMedium)
                    } else {
                        HomeIdentityHeader(container = container, owner = owner)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(bottom = LocalHomeBarClearance.current).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.joinStadiumHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = pastedCode,
                onValueChange = { pastedCode = it },
                label = { Text(strings.stadiumInviteLabel) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    IconButton(onClick = {
                        val clipped = clipboard.getText()?.text
                        if (!clipped.isNullOrEmpty()) pastedCode = clipped
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = strings.pasteButton)
                    }
                }
            )
            FilledTonalButton(
                enabled = pastedCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    container.appScope.launch {
                        val trimmed = pastedCode.trim()
                        val split = container.stadiums.splitInviteLink(trimmed)
                        if (split == null) {
                            status = strings.notAStadiumInvite
                            return@launch
                        }
                        val (handshakePart, stadiumDataPart) = split
                        val stadiumData = container.stadiums.parseStadiumData(stadiumDataPart)
                        if (stadiumData == null) {
                            status = strings.notAStadiumInvite
                            return@launch
                        }
                        val parseResult = container.handshake.parseInviteDetailed(handshakePart)
                        val payload = (parseResult as? InviteParseResult.Ok)?.payload
                        if (payload == null) {
                            status = inviteErrorText(parseResult, strings) ?: strings.invalidInvite
                            return@launch
                        }
                        if (payload.signingPublicKey.contentEquals(owner.publicSigningKey)) {
                            status = strings.selfInviteError
                            return@launch
                        }
                        val addrs = payload.addresses
                        if (addrs.isEmpty()) {
                            status = strings.inviteAcceptedNoAddr
                            return@launch
                        }
                        container.sync.unforget(payload.stadeId)
                        val pending = PendingStadiumJoin(stadiumData.stadiumId, stadiumData.stadiumName, stadiumData.inviteToken)
                        container.stadiums.storePendingJoin(payload.stadeId, pending)
                        status = strings.stadiumJoinDialing(stadiumData.stadiumName)
                        pastedCode = ""

                        val existingContact = container.contacts.findByStadeId(payload.stadeId)
                        if (existingContact != null) {
                            container.stadiumChat.sendJoinRequest(owner, existingContact.id, pending)
                        } else {
                            container.connections.queueDial(addrs)
                        }

                        val joined = withTimeoutOrNull(5 * 60_000L) {
                            container.stadiums.observeStadiums(owner.id).first { list ->
                                list.any { it.id == stadiumData.stadiumId }
                            }
                            true
                        } ?: false
                        status = if (joined) {
                            onJoined(stadiumData.stadiumId)
                            strings.stadiumJoined(stadiumData.stadiumName)
                        } else {
                            runCatching { container.stadiums.clearPendingJoin(payload.stadeId) }
                            if (existingContact == null) container.connections.cancelPendingDial(addrs)
                            strings.connectionTimeout
                        }
                    }
                }
            ) { Text(strings.joinStadiumAction) }
            status?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
