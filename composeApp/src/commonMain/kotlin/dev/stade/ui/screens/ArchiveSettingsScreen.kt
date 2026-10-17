package dev.stade.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.runtime.collectAsState
import dev.stade.identity.LocalIdentity
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.ui.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveSettingsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    var keepArchived by remember {
        mutableStateOf(!container.archivedChats.autoUnarchiveOnMessage())
    }
    val archivedKeys by remember(owner.id) { container.archivedChats.observeArchived(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.archivedChats.archived(owner.id) })
    var showUnarchiveAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(strings.archiveSettingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backToChatsAction
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        keepArchived = !keepArchived
                        container.archivedChats.setAutoUnarchiveOnMessage(!keepArchived)
                    }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(strings.keepChatsArchivedTitle, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        strings.keepChatsArchivedSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = keepArchived,
                    onCheckedChange = {
                        keepArchived = it
                        container.archivedChats.setAutoUnarchiveOnMessage(!it)
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = archivedKeys.isNotEmpty()) { showUnarchiveAll = true }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.unarchiveAllTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (archivedKeys.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        if (archivedKeys.isEmpty()) strings.noArchivedChats else strings.unarchiveAllSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showUnarchiveAll) {
        AlertDialog(
            onDismissRequest = { showUnarchiveAll = false },
            icon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
            title = { Text(strings.unarchiveAllConfirmTitle) },
            text = {
                Text(
                    strings.unarchiveAllConfirmBody(archivedKeys.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val keys = archivedKeys.toList()
                    showUnarchiveAll = false
                    keys.forEach { container.archivedChats.setArchived(owner.id, it, false) }
                }) { Text(strings.unarchiveChatAction) }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveAll = false }) { Text(strings.cancel) }
            }
        )
    }
}
