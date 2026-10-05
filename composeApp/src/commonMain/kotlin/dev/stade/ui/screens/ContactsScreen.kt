package dev.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import dev.stade.ui.components.OnionIndicator
import dev.stade.ui.components.onionStateLabel
import dev.stade.ui.components.onionStateOf
import dev.stade.ui.rememberNetworkOnline
import dev.stade.ui.components.HomeTopBarState
import dev.stade.ui.components.rememberHomeTopBarState
import dev.stade.ui.components.LocalHomeTopBarClearance
import dev.stade.ui.components.LocalHomeBarClearance
import dev.stade.stadium.isOfficial
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.BotBadge
import dev.stade.ui.components.UpdateRequiredBanner
import org.jetbrains.compose.resources.painterResource
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.alpha
import dev.stade.ui.components.StarIntroOverlay
import dev.stade.ui.components.markStarIntroSeen
import dev.stade.ui.components.starIntroPending
import dev.stade.transport.TransportType
import dev.stade.ui.components.TorBootstrapCard
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.PushPin
import dev.stade.group.GroupInfo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import dev.stade.ui.theme.StadeColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.contact.Contact
import dev.stade.contact.deleteContact
import dev.stade.identity.LocalIdentity
import dev.stade.message.SearchResult
import dev.stade.ui.directChatPreview
import dev.stade.ui.groupChatPreview
import dev.stade.ui.PlatformBackHandler
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine

private sealed class ChatListItem {
    data class ContactItem(val contact: Contact, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : ChatListItem()
    data class GroupItem(val group: GroupInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : ChatListItem()
    data class StadiumItem(val stadium: dev.stade.stadium.StadiumInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : ChatListItem()
    val displayName: String get() = when (this) {
        is ContactItem -> contact.nickname
        is GroupItem   -> group.name
        is StadiumItem -> stadium.name
    }
    val key: String get() = when (this) {
        is ContactItem -> contact.id
        is GroupItem   -> "grp_${group.id}"
        is StadiumItem -> "std_${stadium.id}"
    }
    val sortKey: Long get() = when (this) {
        is ContactItem -> lastMessageTs ?: 0L
        is GroupItem   -> lastMessageTs ?: 0L
        is StadiumItem -> lastMessageTs ?: stadium.createdAt
    }
    val pinnedAt: Long? get() = when (this) {
        is ContactItem -> pinnedAtValue
        is GroupItem   -> pinnedAtValue
        is StadiumItem -> pinnedAtValue
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onOpenChat: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenStadium: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenStadey: () -> Unit = {},
    onOpenStarred: () -> Unit = {},
    onOpenArchiveSettings: () -> Unit = {},
    showArchived: Boolean = false,
    onOpenArchived: () -> Unit = {},
    onCloseArchived: () -> Unit = {},
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onCreateStadium: () -> Unit = {},
    onJoinStadium: () -> Unit = {},
    onOpenRadar: (() -> Unit)? = null,
    onLongPressVerify: (String) -> Unit,
    onOpenChatMessage: (String, String) -> Unit = { _, _ -> },
    onOpenGroupMessage: (String, String) -> Unit = { _, _ -> },
    onOpenStadiumMessage: (String, String) -> Unit = { _, _ -> },
    homeBar: HomeTopBarState? = null
) {
    val bar = homeBar ?: rememberHomeTopBarState()
    val allContacts by remember(owner.id) { container.contacts.observeContacts(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.contacts.contacts(owner.id) })
    val contacts by remember(allContacts) { derivedStateOf { allContacts.filter { it.kind == 0 } } }
    val groups by remember(owner.id) { container.groups.observeGroups(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.groups.allGroups(owner.id) })
    val stadiums by remember(owner.id) { container.stadiums.observeStadiums(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.stadiums.allStadiums(owner.id) })
    val connectedSet by container.sync.connectedContacts.collectAsState()
    val versionMismatch by container.sync.peerVersionMismatch.collectAsState()
    val typingSet by container.typing.typingContacts.collectAsState()
    val pinned by remember(owner.id) { container.pinnedChats.observePinned(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.pinnedChats.pinned(owner.id) })
    val archivedKeys by remember(owner.id) { container.archivedChats.observeArchived(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.archivedChats.archived(owner.id) })
    var archiveMenuOpen by remember { mutableStateOf(false) }
    val torInfo by remember {
        container.transports.get(TransportType.TOR)?.info ?: MutableStateFlow(null)
    }.collectAsState()
    val torRunning = torInfo?.running == true
    val onionState = onionStateOf(rememberNetworkOnline(), torInfo)
    var torCardShown by remember { mutableStateOf(false) }
    var torLastPercent by remember { mutableStateOf(0) }
    var torLastPhase by remember { mutableStateOf("") }
    LaunchedEffect(torInfo) {
        val info = torInfo ?: return@LaunchedEffect
        val pct = info.bootstrapPercent
        if (pct != null && !info.running) {
            torCardShown = true
            torLastPercent = pct
            torLastPhase = info.bootstrapPhase
        }
    }
    var starIntroRunning by remember { mutableStateOf(starIntroPending(container.db)) }
    LaunchedEffect(Unit) { bar.starPlaced = !starIntroRunning }
    LaunchedEffect(starIntroRunning) { if (!starIntroRunning) bar.starPlaced = true }
    val autoUnarchive by remember { container.archivedChats.observeAutoUnarchive() }
        .collectAsState(initial = remember { container.archivedChats.autoUnarchiveOnMessage() })
    val unreadContactIds by remember {
        container.db.stadeDbQueries.unreadContactIds().asFlow().mapToList(Dispatchers.Default)
    }.collectAsState(initial = emptyList())
    val unreadGroupIds by remember {
        container.db.stadeDbQueries.unreadGroupIds().asFlow().mapToList(Dispatchers.Default)
    }.collectAsState(initial = emptyList())
    val archivedUnreadCount = remember(archivedKeys, unreadContactIds, unreadGroupIds) {
        archivedKeys.count { key ->
            when {
                key.startsWith("grp_") -> unreadGroupIds.contains(key.removePrefix("grp_"))
                key.startsWith("std_") -> false
                else -> unreadContactIds.contains(key)
            }
        }
    }

    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current

    val contactLastMessages by remember(contacts) {
        combine(
            contacts.map { c -> container.messages.observeLastMessage(c.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = contacts.map { container.messages.lastMessage(it.id) }.ifEmpty { listOf(null) })

    val groupLastMessages by remember(groups) {
        combine(
            groups.map { g -> container.groups.observeLastMessage(g.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = groups.map { container.groups.lastMessage(it.id) }.ifEmpty { listOf(null) })

    val stadiumLastMessages by remember(stadiums) {
        combine(
            stadiums.map { s -> container.stadiums.observeLastMessage(s.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = stadiums.map { container.stadiums.lastMessage(it.id) }.ifEmpty { listOf(null) })


    var actionItem by remember { mutableStateOf<ChatListItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val stadeyVisible by getStadeyVisible()
    var showHideStadeyMenu by remember { mutableStateOf(false) }
    var showHideStadeyConfirm by remember { mutableStateOf(false) }

    if (actionItem != null && !showDeleteConfirm) {
        val item = actionItem!!
        val itemPinned = item.pinnedAt != null
        AlertDialog(
            onDismissRequest = { actionItem = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,

            icon = {
                when (item) {
                    is ChatListItem.ContactItem -> Avatar(item.contact.nickname, size = 56.dp, keySeed = item.contact.publicSigningKey, avatarBytes = item.contact.avatar)
                    is ChatListItem.GroupItem -> Avatar(item.group.name, size = 56.dp, icon = Icons.Default.Group)
                    is ChatListItem.StadiumItem -> Avatar(
                        item.stadium.name,
                        size = 56.dp,
                        icon = Icons.Default.Podcasts,
                        image = if (item.stadium.isOfficial) painterResource(Res.drawable.app_icon) else null,
                        verified = item.stadium.isOfficial
                    )
                }
            },

            title = {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },

            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val itemArchived = archivedKeys.contains(item.key)
                    FilledTonalButton(
                        onClick = {
                            container.archivedChats.setArchived(owner.id, item.key, !itemArchived)
                            actionItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (itemArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (itemArchived) strings.unarchiveChatAction else strings.archiveChatAction)
                    }
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Default) {
                                    container.pinnedChats.setPinned(owner.id, item.key, !itemPinned)
                                }
                            }
                            actionItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (itemPinned) strings.unpinChatAction else strings.pinChatAction)
                    }

                    val itemMuted = when (item) {
                        is ChatListItem.ContactItem -> item.contact.muted
                        is ChatListItem.GroupItem -> item.group.muted
                        is ChatListItem.StadiumItem -> item.stadium.muted
                    }
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Default) {
                                    when (item) {
                                        is ChatListItem.ContactItem -> container.contacts.setMuted(item.contact.id, !itemMuted)
                                        is ChatListItem.GroupItem -> container.groups.setMuted(item.group.id, !itemMuted)
                                        is ChatListItem.StadiumItem -> container.stadiums.setMuted(item.stadium.id, !itemMuted)
                                    }
                                }
                            }
                            actionItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (itemMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (itemMuted) strings.unmuteChatAction else strings.muteChatAction)
                    }

                    if (item is ChatListItem.ContactItem) {
                        FilledTonalButton(
                            onClick = {
                                actionItem = null
                                onLongPressVerify(item.contact.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(strings.viewProfileAction)
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(strings.deleteContact)
                        }
                    }
                }
            },

            confirmButton = {}
        )
    }

    if (showDeleteConfirm && actionItem is ChatListItem.ContactItem) {
        val c = (actionItem as ChatListItem.ContactItem).contact
        AlertDialog(
            onDismissRequest = {
                if (!deleting) {
                    showDeleteConfirm = false
                    actionItem = null
                }
            },
            icon = {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(strings.deleteContactTitle(c.nickname)) },
            text = {
                Text(
                    strings.deleteContactBody,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                runCatching {
                                    container.deleteContact(owner.id, c.id)
                                }
                            }
                            showDeleteConfirm = false
                            actionItem = null
                            deleting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        showDeleteConfirm = false
                        actionItem = null
                    }
                ) { Text(strings.cancel) }
            }
        )
    }

    if (showHideStadeyMenu) {
        AlertDialog(
            onDismissRequest = { showHideStadeyMenu = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Avatar(name = "Stadey", size = 56.dp, icon = Icons.Default.SmartToy) },
            title = {
                Text(
                    text = "Stadey",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            showHideStadeyMenu = false
                            showHideStadeyConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.hideStadeyAction)
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showHideStadeyConfirm) {
        AlertDialog(
            onDismissRequest = { showHideStadeyConfirm = false },
            icon = {
                Icon(Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text(strings.hideStadeyDialogTitle) },
            text = {
                Text(
                    strings.hideStadeyDialogBody,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        setStadeyVisible(false)
                        showHideStadeyConfirm = false
                    }
                ) { Text(strings.hideStadeyConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showHideStadeyConfirm = false }) { Text(strings.cancel) }
            }
        )
    }

    LaunchedEffect(contacts.size) {
        if (contacts.isEmpty() && bar.searchActive) {
            bar.searchActive = false
            bar.query = ""
        }
    }

    PlatformBackHandler(enabled = bar.searchActive) {
        bar.searchActive = false
        bar.query = ""
    }

    val filtered by remember {
        derivedStateOf {
            if (!bar.searchActive || bar.query.isBlank()) contacts
            else contacts.filter { it.nickname.contains(bar.query.trim(), ignoreCase = true) }
        }
    }

    var messageResults by remember { mutableStateOf(emptyList<SearchResult>()) }
    LaunchedEffect(bar.query, bar.searchActive) {
        val q = bar.query.trim()
        if (!bar.searchActive || q.isBlank()) {
            messageResults = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        val results = withContext(Dispatchers.Default) {
            (container.messages.searchMessages(owner.id, q) + container.groups.searchMessages(owner.id, q) + container.stadiums.searchMessages(owner.id, q))
                .sortedByDescending { it.timestamp }
                .take(30)
        }
        messageResults = results
    }

    val combinedItems by remember(filtered, groups, stadiums, bar.searchActive, bar.query, contactLastMessages, groupLastMessages, stadiumLastMessages, pinned, archivedKeys, showArchived) {
        derivedStateOf {
            val q = bar.query.trim()
            val result = mutableListOf<ChatListItem>()
            groups
                .filter { !bar.searchActive || q.isBlank() || it.name.contains(q, ignoreCase = true) }
                .forEachIndexed { i, g ->
                    result.add(ChatListItem.GroupItem(g, groupLastMessages.getOrNull(i)?.timestamp, pinned["grp_${g.id}"]))
                }
            stadiums
                .filter { !bar.searchActive || q.isBlank() || it.name.contains(q, ignoreCase = true) }
                .forEachIndexed { i, s ->
                    result.add(ChatListItem.StadiumItem(s, stadiumLastMessages.getOrNull(i)?.timestamp, pinned["std_${s.id}"]))
                }
            filtered.forEachIndexed { i, c ->
                val origIdx = contacts.indexOf(c)
                result.add(ChatListItem.ContactItem(c, contactLastMessages.getOrNull(origIdx)?.timestamp, pinned[c.id]))
            }
            if (!bar.searchActive || q.isBlank()) {
                result.retainAll { archivedKeys.contains(it.key) == showArchived }
            }
            result.sortWith(
                compareByDescending<ChatListItem> { it.pinnedAt != null }
                    .thenByDescending { it.pinnedAt ?: it.sortKey }
            )
            result
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (showArchived) {
                    TopAppBar(
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = { Text(strings.archivedChatsTitle) },
                        navigationIcon = {
                            IconButton(onClick = onCloseArchived) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.backToChatsAction
                                )
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { archiveMenuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = strings.archiveSettingsTitle)
                                }
                                DropdownMenu(
                                    expanded = archiveMenuOpen,
                                    onDismissRequest = { archiveMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.archiveSettingsTitle) },
                                        onClick = {
                                            archiveMenuOpen = false
                                            onOpenArchiveSettings()
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            },
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding(),
                        top = if (showArchived) {
                            padding.calculateTopPadding()
                        } else {
                            LocalHomeTopBarClearance.current
                        }
                    )
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (torCardShown) {
                    item(key = "torBootstrap") {
                        TorBootstrapCard(
                            percent = torLastPercent,
                            phase = torLastPhase,
                            complete = torRunning,
                            onDismissed = { torCardShown = false }
                        )
                    }
                }
                versionMismatch?.let { mismatch ->
                        item(key = "versionNotice") {
                            UpdateRequiredBanner(
                                message = if (mismatch.peerIsNewer) strings.updateRequiredByYou
                                    else strings.updateRequiredByPeer,
                                dismissLabel = strings.updateAction,
                                onDismiss = { container.sync.clearVersionMismatch() }
                            )
                        }
                    }
                    if (showArchived) {
                        item(key = "archive-notice") {
                            ArchiveNoticeBanner(
                                autoUnarchive = autoUnarchive,
                                onClick = onOpenArchiveSettings
                            )
                        }
                        if (combinedItems.isEmpty()) {
                            item(key = "archive-empty") {
                                Text(
                                    strings.noArchivedChats,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        if (!bar.searchActive && archivedKeys.isNotEmpty()) {
                            item(key = "archived-entry") {
                                ArchivedEntryRow(
                                    unreadCount = archivedUnreadCount,
                                    onClick = onOpenArchived
                                )
                            }
                        }
                        if (stadeyVisible && !(bar.searchActive && bar.query.isNotBlank())) {
                            item(key = "stadey") {
                                StadeyRow(
                                    onClick = onOpenStadey,
                                    onLongPress = { showHideStadeyMenu = true }
                                )
                            }
                        }
                    }
                    if (!showArchived && contacts.isEmpty() && groups.isEmpty() && stadiums.isEmpty()) {
                        item {
                            EmptyContacts(Modifier.fillMaxWidth().fillParentMaxHeight())
                        }
                    } else {
                        items(combinedItems, key = { it.key }) { item ->
                            when (item) {
                                is ChatListItem.ContactItem -> {
                                    val contact = item.contact
                                    val lastMsg by remember(contact.id) { container.messages.observeLastMessage(contact.id) }
                                        .collectAsState(initial = remember(contact.id) { container.messages.lastMessage(contact.id) })
                                    val unread by remember(contact.id) { container.messages.observeUnreadCount(contact.id) }
                                        .collectAsState(initial = remember(contact.id) { container.messages.unreadCount(contact.id) })
                                    val preview by remember(lastMsg?.id, strings) {
                                        derivedStateOf { directChatPreview(lastMsg, strings) }
                                    }
                                    ContactRow(
                                        contact = contact,
                                        connected = connectedSet.contains(contact.id),
                                        typing = typingSet.contains(contact.id),
                                        lastMessage = preview,
                                        unread = unread,
                                        pinned = item.pinnedAt != null,
                                        onClick = { onOpenChat(contact.id) },
                                        onLongPress = { actionItem = item }
                                    )
                                }
                                is ChatListItem.GroupItem -> {
                                    val group = item.group
                                    val lastMsg by remember(group.id) { container.groups.observeLastMessage(group.id) }
                                        .collectAsState(initial = remember(group.id) { container.groups.lastMessage(group.id) })
                                    val unread by remember(group.id) { container.groups.observeUnreadCount(group.id) }
                                        .collectAsState(initial = remember(group.id) { container.groups.unreadCount(group.id) })
                                    val preview by remember(lastMsg?.id, strings) {
                                        derivedStateOf { container.groupChatPreview(group.id, lastMsg, owner, strings) }
                                    }
                                    GroupRow(
                                        group = group,
                                        lastMessage = preview,
                                        unread = unread,
                                        pinned = item.pinnedAt != null,
                                        onClick = { onOpenGroupChat(group.id) },
                                        onLongPress = { actionItem = item }
                                    )
                                }
                                is ChatListItem.StadiumItem -> {
                                    val stadium = item.stadium
                                    StadiumRow(
                                        stadium = stadium,
                                        pinned = item.pinnedAt != null,
                                        onClick = { onOpenStadium(stadium.id) },
                                        onLongPress = { actionItem = item }
                                    )
                                }
                            }
                        }
                        if (bar.searchActive && bar.query.isNotBlank() && messageResults.isNotEmpty()) {
                            item {
                                Text(
                                    strings.searchResultsSectionMessages,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(messageResults, key = { "msg_" + it.messageId }) { result ->
                                MessageSearchRow(
                                    result = result,
                                    onClick = {
                                        when {
                                            result.isStadium -> onOpenStadiumMessage(result.chatId, result.messageId)
                                            result.isGroup -> onOpenGroupMessage(result.chatId, result.messageId)
                                            else -> onOpenChatMessage(result.chatId, result.messageId)
                                        }
                                    }
                                )
                            }
                        }
                        if (bar.searchActive && bar.query.isNotBlank() && combinedItems.isEmpty() && messageResults.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        strings.noSearchResults,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item(key = "homeBarClearance") {
                        Spacer(Modifier.height(LocalHomeBarClearance.current))
                    }
                }
            }
        }

    if (starIntroRunning) {
        StarIntroOverlay(
            targetCenter = bar.starPillCenter,
            onStarPlaced = { bar.starPlaced = true },
            onFinished = {
                markStarIntroSeen(container.db)
                starIntroRunning = false
            }
        )
    }
    }
}

@Composable
private fun MessageSearchRow(result: SearchResult, onClick: () -> Unit) {
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                name = result.title,
                size = 40.dp,
                icon = when {
                    result.isStadium -> Icons.Default.Podcasts
                    result.isGroup -> Icons.Default.Group
                    else -> null
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    result.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatChatTime(result.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = subtleColor
            )
        }
    }
}

@Composable
private fun EmptyContacts(modifier: Modifier) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                strings.noContactsTitle,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(8.dp))
            Text(
                strings.noContactsHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StadeyRow(onClick: () -> Unit, onLongPress: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = "Stadey", size = 52.dp, icon = Icons.Default.SmartToy)

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Stadey",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(6.dp))
                    BotBadge()
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    strings.stadeyRowSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    connected: Boolean,
    typing: Boolean,
    lastMessage: String?,
    unread: Long,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Avatar(contact.nickname, size = 52.dp, keySeed = contact.publicSigningKey, avatarBytes = contact.avatar)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(2.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                if (connected) StadeColors.online else StadeColors.offline
                            )
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.nickname,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (contact.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = strings.pinChatAction,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (contact.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = strings.muteChatAction,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    if (typing) strings.typingIndicator else lastMessage ?: strings.noMessages,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (typing) MaterialTheme.colorScheme.primary else subtleColor,
                    maxLines = 1
                )
            }

            if (unread > 0) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: GroupInfo,
    lastMessage: String?,
    unread: Long,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = group.name, size = 52.dp, icon = Icons.Default.Group)

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = strings.pinChatAction,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (group.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = strings.muteChatAction,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    lastMessage ?: strings.noMessages,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }

            if (unread > 0) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StadiumRow(
    stadium: dev.stade.stadium.StadiumInfo,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                name = stadium.name,
                size = 52.dp,
                icon = Icons.Default.Podcasts,
                image = if (stadium.isOfficial) painterResource(Res.drawable.app_icon) else null,
                verified = stadium.isOfficial
            )

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stadium.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = strings.pinChatAction,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (stadium.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = strings.muteChatAction,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    strings.stadiumSubscriberCount(stadium.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }
        }
    }
}



@Composable
private fun ArchivedEntryRow(
    unreadCount: Int,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Archive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            strings.archivedChatsTitle,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (unreadCount > 0) {
            Text(
                unreadCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ArchiveNoticeBanner(autoUnarchive: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    Text(
        if (autoUnarchive) strings.archiveNoticeUnarchiveBanner else strings.archiveNoticeBanner,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    )
}
