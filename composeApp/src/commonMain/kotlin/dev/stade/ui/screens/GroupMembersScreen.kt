package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.sync.SyncEngine
import dev.stade.ui.components.Avatar
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMembersScreen(
    container: AppContainer,
    owner: LocalIdentity,
    groupId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val group = remember(groupId) { container.groups.getGroup(groupId) }
    val roster by remember(groupId) { container.groups.observeRoster(groupId) }
        .collectAsState(initial = remember(groupId) { container.groups.roster(groupId) })
    val memberIds = remember(roster) { roster.map { it.memberId } }
    val rosterById = remember(roster) { roster.associateBy { it.memberId } }
    val contacts by remember(owner.id) { container.contacts.observeContacts(owner.id) }.collectAsState(initial = emptyList())
    val contactsById = remember(contacts) { contacts.associateBy { it.id } }

    val isOwner = remember(group?.creatorStadeId) {
        group != null && (group.creatorStadeId == owner.stadeId || group.creatorStadeId.isEmpty())
    }

    var kickTarget by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var bannerKey by remember { mutableStateOf(0) }
    LaunchedEffect(bannerKey) {
        if (bannerKey > 0) {
            delay(3000L)
            banner = null
        }
    }

    LaunchedEffect(groupId) {
        container.sync.events.collect { event ->
            if (event is SyncEngine.SyncEvent.RemovedFromGroup && event.groupId == groupId) {
                onBack()
            }
        }
    }

    fun nameFor(memberId: String): String =
        if (memberId == owner.stadeId) owner.nickname
        else contactsById[memberId]?.nickname
            ?: rosterById[memberId]?.nickname?.takeIf { it.isNotBlank() }
            ?: memberId.takeLast(6)

    fun keySeedFor(memberId: String): ByteArray? =
        if (memberId == owner.stadeId) owner.publicSigningKey
        else contactsById[memberId]?.publicSigningKey ?: rosterById[memberId]?.signingKey

    fun avatarFor(memberId: String): ByteArray? =
        if (memberId == owner.stadeId) owner.avatar
        else contactsById[memberId]?.avatar

    if (kickTarget != null && group != null) {
        val targetId = kickTarget!!
        val targetName = nameFor(targetId)
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            icon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.kickMemberTitle(targetName)) },
            text = { Text(strings.kickMemberBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    kickTarget = null
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            runCatching { container.groupChat.kickMember(owner, group, targetId) }.getOrDefault(false)
                        }
                        banner = if (ok) strings.memberKicked(targetName) else strings.kickMemberFailed
                        bannerKey++
                    }
                }) { Text(strings.kickMemberAction, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { kickTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                title = { Text(strings.groupMembersTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(padding)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(memberIds, key = { it }) { memberId ->
                    val isSelf = memberId == owner.stadeId
                    val isAdmin = group != null && memberId == group.creatorStadeId
                    val name = nameFor(memberId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!isSelf) Modifier.clickable { onOpenProfile(memberId) } else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = name, size = 44.dp, keySeed = keySeedFor(memberId), avatarBytes = avatarFor(memberId))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isSelf) "$name (${strings.youLabel})" else name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (isAdmin) {
                                Text(
                                    strings.groupAdminBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (isOwner && !isSelf) {
                            IconButton(onClick = { kickTarget = memberId }) {
                                Icon(
                                    Icons.Default.PersonRemove,
                                    contentDescription = strings.kickMemberAction,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            val currentBanner = banner
            if (currentBanner != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        currentBanner,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
