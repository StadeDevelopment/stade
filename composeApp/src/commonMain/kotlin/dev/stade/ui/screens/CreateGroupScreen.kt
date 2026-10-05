package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.stade.ui.components.HomeIdentityHeader
import dev.stade.ui.components.LocalHomeBarClearance
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.ui.components.Avatar
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    onGroupCreated: (String) -> Unit,
    embedded: Boolean = false
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val allContacts by remember(owner.id) { container.contacts.observeContacts(owner.id) }.collectAsState(initial = emptyList())
    val contacts = remember(allContacts) { allContacts.filter { it.kind == 0 } }
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateOf(setOf<String>()) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                title = {
                    if (embedded) {
                        Text(strings.createGroupTitle, style = MaterialTheme.typography.titleMedium)
                    } else {
                        HomeIdentityHeader(container = container, owner = owner)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(bottom = LocalHomeBarClearance.current).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text(strings.groupNameLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = {
                    Icon(Icons.Default.Group, contentDescription = null)
                }
            )

            Text(
                strings.selectMembersHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(contacts, key = { it.id }) { contact ->
                    val selected = selectedIds.value.contains(contact.id)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds.value = if (selected) {
                                        selectedIds.value - contact.id
                                    } else {
                                        selectedIds.value + contact.id
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Avatar(name = contact.nickname, size = 44.dp, keySeed = contact.publicSigningKey, avatarBytes = contact.avatar)
                                if (selected) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(2.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                contact.nickname,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            Button(
                enabled = groupName.isNotBlank() && selectedIds.value.isNotEmpty() && !creating,
                onClick = {
                    creating = true
                    scope.launch {
                        val group = withContext(Dispatchers.Default) {
                            val g = container.groups.createGroup(owner.id, owner.stadeId, groupName.trim())

                            runCatching {
                                container.groups.setMemberIdentity(g.id, container.groups.selfRosterEntry(owner))
                            }
                            g
                        }

                        val inviteCode = container.groups.generateInviteLink(
                            groupId = group.id,
                            groupName = group.name,
                            inviteToken = group.inviteToken,
                            creatorStadeId = owner.stadeId
                        )
                        selectedIds.value.forEach { cid ->
                            runCatching {
                                container.groupChat.sendGroupInviteToContact(owner, cid, inviteCode)
                            }
                        }
                        creating = false
                        onGroupCreated(group.id)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(strings.createGroupAction)
            }
        }
    }
}

