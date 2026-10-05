package dev.stade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.contact.Contact
import dev.stade.contact.deleteContact
import dev.stade.group.GroupInfo
import dev.stade.identity.LocalIdentity
import dev.stade.message.SearchResult
import dev.stade.stadium.isOfficial
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.BotBadge
import dev.stade.ui.components.BrandMark
import dev.stade.ui.components.DESKTOP_USER_BAR_HEIGHT
import dev.stade.ui.components.DesktopModalHost
import dev.stade.radar.isRadarSupported
import dev.stade.ui.components.DesktopUserBar
import dev.stade.ui.components.UpdateAvailableButton
import dev.stade.ui.components.UpdateRequiredBanner
import dev.stade.ui.screens.StadeRadarScreen
import dev.stade.ui.components.formatChatTime
import org.jetbrains.compose.resources.painterResource
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon
import dev.stade.ui.screens.AboutScreen
import dev.stade.ui.screens.AddContactScreen
import dev.stade.ui.screens.ChatScreen
import dev.stade.ui.screens.CreateGroupScreen
import dev.stade.ui.screens.CreateStadiumScreen
import dev.stade.ui.screens.GroupChatScreen
import dev.stade.ui.screens.GroupMembersScreen
import dev.stade.ui.screens.JoinStadiumScreen
import dev.stade.ui.screens.ManageStadiumScreen
import dev.stade.ui.screens.PinSetupScreen
import dev.stade.ui.screens.ArchiveSettingsScreen
import dev.stade.ui.screens.StadeyScreen
import dev.stade.ui.screens.LanguageScreen
import dev.stade.ui.screens.StarredMessagesScreen
import dev.stade.ui.screens.getStadeyVisible
import dev.stade.ui.screens.setStadeyVisible
import dev.stade.ui.screens.StadiumScreen
import dev.stade.ui.screens.SettingsScreen
import dev.stade.ui.screens.TransportsScreen
import dev.stade.ui.screens.VerifyContactScreen
import dev.stade.ui.screens.SecuritySettingsScreen
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.theme.StadeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine

private sealed class PanelRight {
    data object Empty : PanelRight()
    data class Chat(val contactId: String, val highlightMessageId: String? = null) : PanelRight()
    data class GroupChat(val groupId: String, val highlightMessageId: String? = null) : PanelRight()
    data class GroupMembers(val groupId: String) : PanelRight()
    data object CreateGroup : PanelRight()
    data class Stadium(val stadiumId: String, val highlightMessageId: String? = null) : PanelRight()
    data object CreateStadium : PanelRight()
    data class ManageStadium(val stadiumId: String) : PanelRight()
    data object JoinStadium : PanelRight()
    data object Settings : PanelRight()
    data object Security : PanelRight()
    data object Transports : PanelRight()
    data object About : PanelRight()
    data object Language : PanelRight()
    data object Starred : PanelRight()
    data object ArchiveSettings : PanelRight()
    data object Stadey : PanelRight()
    data object AddContact : PanelRight()
    data object Radar : PanelRight()
    data class Verify(val contactId: String, val from: PanelRight = Chat(contactId)) : PanelRight()
    data class PinSetup(val requireCurrent: Boolean, val ret: PanelRight, val mode: dev.stade.ui.screens.PinSetupMode = dev.stade.ui.screens.PinSetupMode.Primary) : PanelRight()
}

private sealed class PanelChatItem {
    data class ContactItem(val contact: Contact, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : PanelChatItem()
    data class GroupItem(val group: GroupInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : PanelChatItem()
    data class StadiumItem(val stadium: dev.stade.stadium.StadiumInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : PanelChatItem()
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
fun TwoPanelLayout(
    container: AppContainer,
    owner: LocalIdentity,
    onLogout: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
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
    val archivedKeys by remember(owner.id) { container.archivedChats.observeArchived(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.archivedChats.archived(owner.id) })
    val pinned by remember(owner.id) { container.pinnedChats.observePinned(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.pinnedChats.pinned(owner.id) })
    var right by remember { mutableStateOf<PanelRight>(PanelRight.Empty) }
    var createDialog by remember { mutableStateOf<CreateDialog?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    val autoUnarchive by remember { container.archivedChats.observeAutoUnarchive() }
        .collectAsState(initial = remember { container.archivedChats.autoUnarchiveOnMessage() })
    LaunchedEffect(archivedKeys, showArchived) {
        if (showArchived && archivedKeys.isEmpty()) showArchived = false
    }
    var query by remember { mutableStateOf("") }
    val settingsListState = rememberLazyListState()
    val stadeyVisible by getStadeyVisible()
    var showHideStadeyConfirm by remember { mutableStateOf(false) }

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

    val pendingInvite by container.pendingInvite.collectAsState()
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null && right !is PanelRight.AddContact && right !is PanelRight.PinSetup) {
            right = PanelRight.AddContact
        }
    }

    val pendingOpenChatId by container.pendingOpenChat.collectAsState()
    LaunchedEffect(pendingOpenChatId) {
        val id = pendingOpenChatId
        if (id != null) {
            right = PanelRight.Chat(id)
            container.pendingOpenChat.value = null
        }
    }

    val pendingOpenStadiumId by container.pendingOpenStadium.collectAsState()
    LaunchedEffect(pendingOpenStadiumId) {
        val id = pendingOpenStadiumId
        if (id != null) {
            right = PanelRight.Stadium(id)
            container.pendingOpenStadium.value = null
        }
    }

    val pendingGoHome by container.pendingGoHome.collectAsState()
    LaunchedEffect(pendingGoHome) {
        if (pendingGoHome) {
            right = PanelRight.Empty
            container.pendingGoHome.value = false
        }
    }

    var deleteTargetContact by remember { mutableStateOf<Contact?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { it.nickname.contains(query.trim(), ignoreCase = true) }
    }

    val combinedPanelItems = remember(filtered, groups, stadiums, query, contactLastMessages, groupLastMessages, stadiumLastMessages, pinned, archivedKeys, showArchived) {
        val q = query.trim()
        val result = mutableListOf<PanelChatItem>()
        groups
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .forEachIndexed { i, g ->
                result.add(PanelChatItem.GroupItem(g, groupLastMessages.getOrNull(i)?.timestamp, pinned["grp_${g.id}"]))
            }
        stadiums
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .forEachIndexed { i, s ->
                result.add(PanelChatItem.StadiumItem(s, stadiumLastMessages.getOrNull(i)?.timestamp, pinned["std_${s.id}"]))
            }
        filtered.forEachIndexed { i, c ->
            val origIdx = contacts.indexOf(c)
            result.add(PanelChatItem.ContactItem(c, contactLastMessages.getOrNull(origIdx)?.timestamp, pinned[c.id]))
        }
        if (q.isBlank()) {
            result.retainAll { archivedKeys.contains(it.key) == showArchived }
        } else if (showArchived) {
            result.retainAll { archivedKeys.contains(it.key) }
        }
        result.sortWith(
            compareByDescending<PanelChatItem> { it.pinnedAt != null }
                .thenByDescending { it.pinnedAt ?: it.sortKey }
        )
        result
    }

    val archivedUnreadCount = remember(archivedKeys, contacts, groups) {
        archivedKeys.count { key ->
            when {
                key.startsWith("grp_") -> container.groups.unreadCount(key.removePrefix("grp_")) > 0
                key.startsWith("std_") -> false
                else -> container.messages.unreadCount(key) > 0
            }
        }
    }

    var panelMessageResults by remember { mutableStateOf(emptyList<SearchResult>()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            panelMessageResults = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        val results = withContext(Dispatchers.Default) {
            (container.messages.searchMessages(owner.id, q) + container.groups.searchMessages(owner.id, q) + container.stadiums.searchMessages(owner.id, q))
                .sortedByDescending { it.timestamp }
                .take(30)
        }
        panelMessageResults = results
    }

    if (showDeleteConfirm && deleteTargetContact != null) {
        val c = deleteTargetContact!!
        AlertDialog(
            onDismissRequest = {
                if (!deleting) {
                    showDeleteConfirm = false
                    deleteTargetContact = null
                }
            },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
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
                            val currentRight = right
                            if (currentRight is PanelRight.Chat && currentRight.contactId == c.id) {
                                right = PanelRight.Empty
                            }
                            if (right is PanelRight.Verify &&
                                (right as PanelRight.Verify).contactId == c.id) {
                                right = PanelRight.Empty
                            }
                            showDeleteConfirm = false
                            deleteTargetContact = null
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
                        deleteTargetContact = null
                    }
                ) { Text(strings.cancel) }
            }
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
                        if (right is PanelRight.Stadey) right = PanelRight.Empty
                    }
                ) { Text(strings.hideStadeyConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showHideStadeyConfirm = false }) { Text(strings.cancel) }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {

        Surface(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        navigationIcon = {
                            if (showArchived) {
                                IconButton(onClick = { showArchived = false }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = strings.back
                                    )
                                }
                            }
                        },
                        title = {
                            Text(
                                if (showArchived) strings.archivedChatsTitle else strings.appTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        actions = {
                            UpdateAvailableButton(container = container)
                            if (!showArchived) {
                                if (archivedKeys.isNotEmpty()) {
                                    IconButton(onClick = { showArchived = true }) {
                                        Icon(
                                            Icons.Default.Archive,
                                            contentDescription = strings.archivedChatsTitle,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { right = PanelRight.Starred }) {
                                    Icon(
                                        Icons.Default.StarOutline,
                                        contentDescription = strings.starredMessagesTitle,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (contacts.isNotEmpty()) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            placeholder = { Text(strings.searchContactsPlaceholder) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            if (showArchived && query.isBlank()) {
                                item(key = "archiveNotice") {
                                    Text(
                                        if (autoUnarchive) strings.archiveNoticeUnarchiveBanner
                                        else strings.archiveNoticeBanner,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { right = PanelRight.ArchiveSettings }
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                }
                            }
                            if (stadeyVisible && !showArchived && query.isBlank()) {
                                item(key = "stadey") {
                                    PanelStadeyRow(
                                        selected = right is PanelRight.Stadey,
                                        onClick = { right = PanelRight.Stadey },
                                        onHideRequest = { showHideStadeyConfirm = true }
                                    )
                                }
                            }
                            if (contacts.isEmpty() && groups.isEmpty() && stadiums.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillParentMaxHeight()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.PersonAdd, null,
                                                modifier = Modifier.size(52.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(strings.noContactsTitle, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                strings.noContactsHint,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            FilledTonalButton(onClick = { right = PanelRight.AddContact }) {
                                                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(strings.addContactAction)
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(combinedPanelItems, key = { it.key }) { item ->
                                    when (item) {
                                        is PanelChatItem.ContactItem -> {
                                            val contact = item.contact
                                            val lastMsg by remember(contact.id) { container.messages.observeLastMessage(contact.id) }
                                                .collectAsState(initial = remember(contact.id) { container.messages.lastMessage(contact.id) })
                                            val unread by remember(contact.id) { container.messages.observeUnreadCount(contact.id) }
                                                .collectAsState(initial = remember(contact.id) { container.messages.unreadCount(contact.id) })
                                            val preview by remember(lastMsg?.id, strings) {
                                                derivedStateOf { directChatPreview(lastMsg, strings) }
                                            }
                                            val isSelected by remember(contact.id) {
                                                derivedStateOf {
                                                    when (val r = right) {
                                                        is PanelRight.Chat   -> r.contactId == contact.id
                                                        is PanelRight.Verify -> r.contactId == contact.id
                                                        else -> false
                                                    }
                                                }
                                            }
                                            PanelContactRow(
                                                contact = contact,
                                                selected = isSelected,
                                                connected = connectedSet.contains(contact.id),
                                                typing = typingSet.contains(contact.id),
                                                pinned = item.pinnedAt != null,
                                                lastMessage = preview,
                                                lastMessageTs = lastMsg?.timestamp,
                                                unread = unread,
                                                onClick = { right = PanelRight.Chat(contact.id) },
                                                onVerifyRequest = { right = PanelRight.Verify(contact.id) },
                                                onDeleteRequest = {
                                                    deleteTargetContact = contact
                                                    showDeleteConfirm = true
                                                },
                                                onTogglePin = {
                                                    scope.launch { withContext(Dispatchers.Default) { container.pinnedChats.setPinned(owner.id, item.key, item.pinnedAt == null) } }
                                                },
                                                onToggleMute = {
                                                    scope.launch { withContext(Dispatchers.Default) { container.contacts.setMuted(contact.id, !contact.muted) } }
                                                },
                                                archived = archivedKeys.contains(item.key),
                                                onToggleArchive = {
                                                    val next = !archivedKeys.contains(item.key)
                                                    scope.launch {
                                                        withContext(Dispatchers.Default) {
                                                            container.archivedChats.setArchived(owner.id, item.key, next)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        is PanelChatItem.GroupItem -> {
                                            val group = item.group
                                            val lastGroupMsg by remember(group.id) { container.groups.observeLastMessage(group.id) }
                                                .collectAsState(initial = remember(group.id) { container.groups.lastMessage(group.id) })
                                            val groupUnread by remember(group.id) { container.groups.observeUnreadCount(group.id) }
                                                .collectAsState(initial = remember(group.id) { container.groups.unreadCount(group.id) })
                                            val groupPreview by remember(lastGroupMsg?.id, strings) {
                                                derivedStateOf { container.groupChatPreview(group.id, lastGroupMsg, owner, strings) }
                                            }
                                            val isGroupSelected by remember(group.id) {
                                                derivedStateOf {
                                                    when (val r = right) {
                                                        is PanelRight.GroupChat -> r.groupId == group.id
                                                        is PanelRight.GroupMembers -> r.groupId == group.id
                                                        else -> false
                                                    }
                                                }
                                            }
                                            PanelGroupRow(
                                                group = group,
                                                selected = isGroupSelected,
                                                pinned = item.pinnedAt != null,
                                                lastMessage = groupPreview,
                                                lastMessageTs = lastGroupMsg?.timestamp,
                                                unread = groupUnread,
                                                onClick = { right = PanelRight.GroupChat(group.id) },
                                                onTogglePin = {
                                                    scope.launch { withContext(Dispatchers.Default) { container.pinnedChats.setPinned(owner.id, item.key, item.pinnedAt == null) } }
                                                },
                                                onToggleMute = {
                                                    scope.launch { withContext(Dispatchers.Default) { container.groups.setMuted(group.id, !group.muted) } }
                                                },
                                                archived = archivedKeys.contains(item.key),
                                                onToggleArchive = {
                                                    val next = !archivedKeys.contains(item.key)
                                                    scope.launch {
                                                        withContext(Dispatchers.Default) {
                                                            container.archivedChats.setArchived(owner.id, item.key, next)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        is PanelChatItem.StadiumItem -> {
                                            val stadium = item.stadium
                                            val isStadiumSelected by remember(stadium.id) {
                                                derivedStateOf {
                                                    when (val r = right) {
                                                        is PanelRight.Stadium -> r.stadiumId == stadium.id
                                                        is PanelRight.ManageStadium -> r.stadiumId == stadium.id
                                                        else -> false
                                                    }
                                                }
                                            }
                                            PanelStadiumRow(
                                                stadium = stadium,
                                                selected = isStadiumSelected,
                                                pinned = item.pinnedAt != null,
                                                onClick = { right = PanelRight.Stadium(stadium.id) },
                                                onTogglePin = {
                                                    scope.launch { withContext(Dispatchers.Default) { container.pinnedChats.setPinned(owner.id, item.key, item.pinnedAt == null) } }
                                                },
                                                onToggleMute = {
                                                    scope.launch { withContext(Dispatchers.Default) { container.stadiums.setMuted(stadium.id, !stadium.muted) } }
                                                },
                                                archived = archivedKeys.contains(item.key),
                                                onToggleArchive = {
                                                    val next = !archivedKeys.contains(item.key)
                                                    scope.launch {
                                                        withContext(Dispatchers.Default) {
                                                            container.archivedChats.setArchived(owner.id, item.key, next)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                if (query.isNotBlank() && panelMessageResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            strings.searchResultsSectionMessages,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(panelMessageResults, key = { "msg_" + it.messageId }) { result ->
                                        PanelMessageSearchRow(
                                            result = result,
                                            onClick = {
                                                right = when {
                                                    result.isStadium -> PanelRight.Stadium(result.chatId, highlightMessageId = result.messageId)
                                                    result.isGroup -> PanelRight.GroupChat(result.chatId, highlightMessageId = result.messageId)
                                                    else -> PanelRight.Chat(result.chatId, highlightMessageId = result.messageId)
                                                }
                                            }
                                        )
                                    }
                                }
                                if (query.isNotBlank() && combinedPanelItems.isEmpty() && panelMessageResults.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                strings.noSearchResults,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                item { Spacer(Modifier.height(DESKTOP_USER_BAR_HEIGHT + 24.dp)) }
                            }
                        }

                        DesktopUserBar(
                            container = container,
                            owner = owner,
                            onAddContact = { createDialog = CreateDialog.AddContact },
                            onCreateGroup = { createDialog = CreateDialog.CreateGroup },
                            onCreateStadium = { createDialog = CreateDialog.CreateStadium },
                            onJoinStadium = { createDialog = CreateDialog.JoinStadium },
                            onOpenSettings = { right = PanelRight.Settings },
                            onOpenRadar = if (isRadarSupported) ({ right = PanelRight.Radar }) else null,
                            settingsOpen = right is PanelRight.Settings ||
                                right is PanelRight.Security ||
                                right is PanelRight.Transports ||
                                right is PanelRight.About ||
                                right is PanelRight.Language ||
                                right is PanelRight.ArchiveSettings,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            AnimatedContent(
                targetState = right,
                modifier = Modifier.fillMaxSize(),
                contentKey = { panelKey(it) },
                transitionSpec = {
                    val forward = panelDepth(targetState) >= panelDepth(initialState)
                    val enterSlide = tween<IntOffset>(PANEL_SLIDE_MS, easing = PanelEnterEasing)
                    val exitSlide = tween<IntOffset>(PANEL_SLIDE_MS, easing = PanelExitEasing)
                    val enterFade = tween<Float>(PANEL_FADE_MS, easing = LinearEasing)
                    val exitFade = tween<Float>(PANEL_SLIDE_MS, easing = LinearEasing)
                    val transition = if (forward) {
                        (slideInHorizontally(enterSlide) { it } + fadeIn(enterFade)) togetherWith
                            (slideOutHorizontally(exitSlide) { -it / 4 } + fadeOut(exitFade, targetAlpha = 0.85f))
                    } else {
                        (slideInHorizontally(enterSlide) { -it / 4 } + fadeIn(enterFade)) togetherWith
                            (slideOutHorizontally(exitSlide) { it } + fadeOut(exitFade, targetAlpha = 0.85f))
                    }
                    transition.using(SizeTransform(clip = false))
                },
                label = "panelNav"
            ) { rp ->
            when (rp) {
                is PanelRight.Empty -> Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BrandMark(size = 125.dp)

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Stade",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    strings.selectContactHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is PanelRight.Chat -> ChatScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    highlightMessageId = rp.highlightMessageId,
                    onBack = null,
                    onOpenProfile = { right = PanelRight.Verify(rp.contactId, from = PanelRight.Chat(rp.contactId)) },
                    onContactDeleted = { right = PanelRight.Empty }
                )

                is PanelRight.Settings -> SettingsScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onOpenTransports = { right = PanelRight.Transports },
                    onOpenSecurity = { right = PanelRight.Security },
                    onOpenAbout = { right = PanelRight.About },
                    onOpenLanguage = { right = PanelRight.Language },
                    onLogout = onLogout,
                    listState = settingsListState
                )

                is PanelRight.Security -> SecuritySettingsScreen(
                    container = container,
                    onBack = { right = PanelRight.Settings },
                    onOpenPinSetup = { requireCurrent ->
                        right = PanelRight.PinSetup(requireCurrent, PanelRight.Security)
                    },
                    onOpenDuressPinSetup = {
                        right = PanelRight.PinSetup(true, PanelRight.Security, dev.stade.ui.screens.PinSetupMode.Duress)
                    }
                )

                is PanelRight.PinSetup -> PinSetupScreen(
                    vault = container.vault,
                    requireCurrent = rp.requireCurrent,
                    mode = rp.mode,
                    onDone = { right = rp.ret },
                    onCancel = { right = rp.ret }
                )

                is PanelRight.Transports -> TransportsScreen(
                    container = container,
                    onBack = { right = PanelRight.Settings }
                )

                is PanelRight.About -> AboutScreen(
                    onBack = { right = PanelRight.Settings }
                )

                is PanelRight.Language -> LanguageScreen(
                    onBack = { right = PanelRight.Settings }
                )
                is PanelRight.ArchiveSettings -> ArchiveSettingsScreen(
                    container = container,
                    onBack = { right = PanelRight.Empty }
                )

                is PanelRight.Starred -> StarredMessagesScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onOpenMessage = { ref ->
                        right = when (ref.scope) {
                            dev.stade.chat.StarScope.DIRECT -> PanelRight.Chat(ref.chatId, ref.messageId)
                            dev.stade.chat.StarScope.GROUP -> PanelRight.GroupChat(ref.chatId, ref.messageId)
                            dev.stade.chat.StarScope.STADIUM -> PanelRight.Stadium(ref.chatId, ref.messageId)
                        }
                    }
                )

                is PanelRight.Stadey -> StadeyScreen(
                    onBack = { right = PanelRight.Empty }
                )

                is PanelRight.AddContact -> AddContactScreen(
                    container = container,
                    owner = owner,
                    onBack = {
                        container.pendingInvite.value = null
                        right = PanelRight.Empty
                    }
                )

                is PanelRight.Radar -> StadeRadarScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty }
                )

                is PanelRight.Verify -> VerifyContactScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    onBack = { right = rp.from }
                )

                is PanelRight.CreateGroup -> CreateGroupScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onGroupCreated = { groupId -> right = PanelRight.GroupChat(groupId) }
                )

                is PanelRight.GroupChat -> GroupChatScreen(
                    container = container,
                    owner = owner,
                    groupId = rp.groupId,
                    highlightMessageId = rp.highlightMessageId,
                    onBack = { right = PanelRight.Empty },
                    onOpenMembers = { right = PanelRight.GroupMembers(rp.groupId) }
                )

                is PanelRight.GroupMembers -> GroupMembersScreen(
                    container = container,
                    owner = owner,
                    groupId = rp.groupId,
                    onBack = { right = PanelRight.GroupChat(rp.groupId) },
                    onOpenProfile = { memberId ->
                        right = PanelRight.Verify(memberId, from = PanelRight.GroupMembers(rp.groupId))
                    }
                )

                is PanelRight.Stadium -> StadiumScreen(
                    container = container,
                    owner = owner,
                    stadiumId = rp.stadiumId,
                    onBack = { right = PanelRight.Empty },
                    onManage = { right = PanelRight.ManageStadium(rp.stadiumId) }
                )

                is PanelRight.ManageStadium -> ManageStadiumScreen(
                    container = container,
                    owner = owner,
                    stadiumId = rp.stadiumId,
                    onBack = { right = PanelRight.Stadium(rp.stadiumId) },
                    onDeleted = { right = PanelRight.Empty }
                )

                is PanelRight.CreateStadium -> CreateStadiumScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onStadiumCreated = { stadiumId -> right = PanelRight.Stadium(stadiumId) }
                )

                is PanelRight.JoinStadium -> JoinStadiumScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onJoined = { stadiumId -> right = PanelRight.Stadium(stadiumId) }
                )
            }
            }
        }
    }

    val openDialog = createDialog
    if (openDialog != null) {
        DesktopModalHost(onDismiss = { createDialog = null }) {
            when (openDialog) {
                CreateDialog.AddContact -> AddContactScreen(
                    container = container,
                    owner = owner,
                    onBack = {
                        container.pendingInvite.value = null
                        createDialog = null
                    },
                    embedded = true
                )
                CreateDialog.CreateGroup -> CreateGroupScreen(
                    container = container,
                    owner = owner,
                    onBack = { createDialog = null },
                    onGroupCreated = { groupId ->
                        createDialog = null
                        right = PanelRight.GroupChat(groupId)
                    },
                    embedded = true
                )
                CreateDialog.CreateStadium -> CreateStadiumScreen(
                    container = container,
                    owner = owner,
                    onBack = { createDialog = null },
                    onStadiumCreated = { stadiumId ->
                        createDialog = null
                        right = PanelRight.Stadium(stadiumId)
                    },
                    embedded = true
                )
                CreateDialog.JoinStadium -> JoinStadiumScreen(
                    container = container,
                    owner = owner,
                    onBack = { createDialog = null },
                    onJoined = { stadiumId ->
                        createDialog = null
                        right = PanelRight.Stadium(stadiumId)
                    },
                    embedded = true
                )
            }
        }
    }
}

private enum class CreateDialog { AddContact, CreateGroup, CreateStadium, JoinStadium }


@Composable
private fun PanelMessageSearchRow(result: SearchResult, onClick: () -> Unit) {
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name = result.title,
            size = 36.dp,
            icon = when {
                result.isStadium -> Icons.Default.Podcasts
                result.isGroup -> Icons.Default.Group
                else -> null
            }
        )
        Spacer(Modifier.width(12.dp))
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PanelStadeyRow(
    selected: Boolean,
    onClick: () -> Unit,
    onHideRequest: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    val strings = LocalStrings.current

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    }
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press) continue
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                menuOffset = with(density) {
                                    DpOffset(
                                        x = pos.x.toDp(),
                                        y = pos.y.toDp() - rowHeightPx.toDp()
                                    )
                                }
                            }
                            if (event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(8.dp))

            Avatar(name = "Stadey", size = 42.dp, icon = Icons.Default.SmartToy)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Stadey",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    BotBadge()
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    strings.stadeyRowSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(strings.hideStadeyAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onHideRequest()
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PanelContactRow(
    contact: Contact,
    selected: Boolean,
    connected: Boolean,
    typing: Boolean,
    pinned: Boolean,
    lastMessage: String?,
    lastMessageTs: Long?,
    unread: Long,
    onClick: () -> Unit,
    onVerifyRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    archived: Boolean = false,
    onToggleArchive: () -> Unit = {}
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    val strings = LocalStrings.current

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    }
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press) continue
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                menuOffset = with(density) {
                                    DpOffset(
                                        x = pos.x.toDp(),
                                        y = pos.y.toDp() - rowHeightPx.toDp()
                                    )
                                }
                            }
                            if (event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(8.dp))

            Box {
                Avatar(name = contact.nickname, size = 42.dp, keySeed = contact.publicSigningKey, avatarBytes = contact.avatar)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(2.dp)
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(
                            if (connected) StadeColors.online else StadeColors.offline
                        )
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.nickname,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (typing) strings.typingIndicator else lastMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (lastMessageTs != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatChatTime(lastMessageTs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (unread > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) strings.unpinChatAction else strings.pinChatAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (contact.muted) strings.unmuteChatAction else strings.muteChatAction) },
                leadingIcon = {
                    Icon(
                        if (contact.muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleMute()
                }
            )
            DropdownMenuItem(
                text = { Text(if (archived) strings.unarchiveChatAction else strings.archiveChatAction) },
                leadingIcon = {
                    Icon(
                        if (archived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleArchive()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(strings.viewProfileAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onVerifyRequest()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(strings.deleteContact, color = MaterialTheme.colorScheme.error)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showContextMenu = false
                    onDeleteRequest()
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PanelGroupRow(
    group: GroupInfo,
    selected: Boolean,
    pinned: Boolean,
    lastMessage: String?,
    lastMessageTs: Long?,
    unread: Long,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    archived: Boolean = false,
    onToggleArchive: () -> Unit = {}
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    val strings = LocalStrings.current

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    }
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press) continue
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                menuOffset = with(density) {
                                    DpOffset(
                                        x = pos.x.toDp(),
                                        y = pos.y.toDp() - rowHeightPx.toDp()
                                    )
                                }
                            }
                            if (event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(8.dp))

            Avatar(name = group.name, size = 44.dp, icon = Icons.Default.Group)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        lastMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (lastMessageTs != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatChatTime(lastMessageTs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (unread > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) strings.unpinChatAction else strings.pinChatAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (group.muted) strings.unmuteChatAction else strings.muteChatAction) },
                leadingIcon = {
                    Icon(
                        if (group.muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleMute()
                }
            )
            DropdownMenuItem(
                text = { Text(if (archived) strings.unarchiveChatAction else strings.archiveChatAction) },
                leadingIcon = {
                    Icon(
                        if (archived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleArchive()
                }
            )
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelStadiumRow(
    stadium: dev.stade.stadium.StadiumInfo,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    archived: Boolean = false,
    onToggleArchive: () -> Unit = {}
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    val strings = LocalStrings.current

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    }
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press) continue
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) {
                                menuOffset = with(density) {
                                    DpOffset(
                                        x = pos.x.toDp(),
                                        y = pos.y.toDp() - rowHeightPx.toDp()
                                    )
                                }
                            }
                            if (event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(8.dp))

            Avatar(
                name = stadium.name,
                size = 44.dp,
                icon = Icons.Default.Podcasts,
                image = if (stadium.isOfficial) painterResource(Res.drawable.app_icon) else null,
                verified = stadium.isOfficial
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stadium.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) strings.unpinChatAction else strings.pinChatAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (stadium.muted) strings.unmuteStadiumAction else strings.muteStadiumAction) },
                leadingIcon = {
                    Icon(
                        if (stadium.muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleMute()
                }
            )
            DropdownMenuItem(
                text = { Text(if (archived) strings.unarchiveChatAction else strings.archiveChatAction) },
                leadingIcon = {
                    Icon(
                        if (archived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleArchive()
                }
            )
        }
    }
}

@Composable
private fun PanelArchivedRow(unreadCount: Int, onClick: () -> Unit) {
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

private const val PANEL_SLIDE_MS = 300
private const val PANEL_FADE_MS = 200
private val PanelEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val PanelExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private fun panelKey(panel: PanelRight): String = when (panel) {
    is PanelRight.Empty -> "empty"
    is PanelRight.Chat -> "chat:${panel.contactId}"
    is PanelRight.GroupChat -> "group:${panel.groupId}"
    is PanelRight.GroupMembers -> "groupMembers:${panel.groupId}"
    is PanelRight.CreateGroup -> "createGroup"
    is PanelRight.Stadium -> "stadium:${panel.stadiumId}"
    is PanelRight.CreateStadium -> "createStadium"
    is PanelRight.ManageStadium -> "manageStadium:${panel.stadiumId}"
    is PanelRight.JoinStadium -> "joinStadium"
    is PanelRight.Settings -> "settings"
    is PanelRight.Security -> "security"
    is PanelRight.Transports -> "transports"
    is PanelRight.About -> "about"
    is PanelRight.Language -> "language"
    is PanelRight.Starred -> "starred"
    is PanelRight.ArchiveSettings -> "archiveSettings"
    is PanelRight.Stadey -> "stadey"
    is PanelRight.AddContact -> "addContact"
    is PanelRight.Radar -> "radar"
    is PanelRight.Verify -> "verify:${panel.contactId}"
    is PanelRight.PinSetup -> "pinSetup"
}

private fun panelDepth(panel: PanelRight): Int = when (panel) {
    is PanelRight.Empty -> 0
    is PanelRight.Chat,
    is PanelRight.GroupChat,
    is PanelRight.Stadium,
    is PanelRight.Stadey,
    is PanelRight.Settings,
    is PanelRight.Starred,
    is PanelRight.AddContact,
    is PanelRight.CreateGroup,
    is PanelRight.CreateStadium,
    is PanelRight.JoinStadium,
    is PanelRight.Radar -> 1
    is PanelRight.GroupMembers,
    is PanelRight.ManageStadium,
    is PanelRight.Verify,
    is PanelRight.Security,
    is PanelRight.Transports,
    is PanelRight.About,
    is PanelRight.Language,
    is PanelRight.ArchiveSettings -> 2
    is PanelRight.PinSetup -> 3
}
