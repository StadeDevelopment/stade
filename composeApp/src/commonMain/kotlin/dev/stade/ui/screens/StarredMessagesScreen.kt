package dev.stade.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.stade.ui.PlatformBackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import dev.stade.AppContainer
import dev.stade.chat.StarScope
import dev.stade.chat.StarredRef
import dev.stade.identity.LocalIdentity
import dev.stade.message.previewBody
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.i18n.LocalStrings

private data class StarredEntry(
    val ref: StarredRef,
    val chatName: String,
    val preview: String,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StarredMessagesScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    onOpenMessage: (StarredRef) -> Unit
) {
    val strings = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val refs by remember(owner.id) { container.starredMessages.observeStarred(owner.id) }
        .collectAsState(initial = emptyList())
    var selectedIds by remember(owner.id) { mutableStateOf<Set<String>>(emptySet()) }
    val inSelectionMode = selectedIds.isNotEmpty()

    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
    }

    PlatformBackHandler(enabled = inSelectionMode) { selectedIds = emptySet() }

    val entries = remember(refs, strings) {
        refs.mapNotNull { ref -> resolve(container, strings.photoMessage, strings.voiceMessage, strings.videoMessage, strings.stickerMessage, ref) }
    }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(strings.selectedCount(selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancelSelection)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val targets = refs.filter { selectedIds.contains(it.messageId) }
                            targets.forEach { ref ->
                                container.starredMessages.setStarred(
                                    owner.id, ref.messageId, ref.scope, ref.chatId, false
                                )
                            }
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.StarBorder, contentDescription = strings.unstarMessageAction)
                        }
                    }
                )
            } else {
                TopAppBar(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(strings.starredMessagesTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backToChatsAction)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        strings.noStarredMessages,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.ref.messageId }) { entry ->
                        val selected = selectedIds.contains(entry.ref.messageId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectionMode) toggleSelection(entry.ref.messageId)
                                        else onOpenMessage(entry.ref)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        toggleSelection(entry.ref.messageId)
                                    }
                                )
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (entry.ref.scope) {
                                    StarScope.DIRECT -> Icons.Default.Person
                                    StarScope.GROUP -> Icons.Default.Group
                                    StarScope.STADIUM -> Icons.Default.Podcasts
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.chatName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    entry.preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                formatChatTime(entry.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

private fun resolve(
    container: AppContainer,
    photoLabel: String,
    voiceLabel: String,
    videoLabel: String,
    stickerLabel: String,
    ref: StarredRef
): StarredEntry? = runCatching {
    val q = container.db.stadeDbQueries
    when (ref.scope) {
        StarScope.DIRECT -> {
            val row = q.messageById(ref.messageId).executeAsOneOrNull() ?: return@runCatching null
            StarredEntry(
                ref = ref,
                chatName = container.contacts.get(row.contactId)?.nickname ?: row.contactId.takeLast(6),
                preview = previewBody(row.body, photoLabel, voiceLabel, videoLabel, stickerLabel),
                timestamp = row.timestamp
            )
        }
        StarScope.GROUP -> {
            val row = q.groupMessageById(ref.messageId).executeAsOneOrNull() ?: return@runCatching null
            StarredEntry(
                ref = ref,
                chatName = container.groups.getGroup(row.groupId)?.name ?: row.groupId.takeLast(6),
                preview = previewBody(row.body, photoLabel, voiceLabel, videoLabel, stickerLabel),
                timestamp = row.timestamp
            )
        }
        StarScope.STADIUM -> {
            val row = q.stadiumMessageById(ref.messageId).executeAsOneOrNull() ?: return@runCatching null
            StarredEntry(
                ref = ref,
                chatName = container.stadiums.getStadium(row.stadiumId)?.name ?: row.stadiumId.takeLast(6),
                preview = previewBody(row.body, photoLabel, voiceLabel, videoLabel, stickerLabel),
                timestamp = row.timestamp
            )
        }
    }
}.getOrNull()
