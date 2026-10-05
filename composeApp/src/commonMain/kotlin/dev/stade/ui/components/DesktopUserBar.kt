package dev.stade.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import dev.stade.AppContainer
import dev.stade.transport.TransportType
import dev.stade.ui.rememberNetworkOnline
import kotlinx.coroutines.flow.MutableStateFlow
import dev.stade.identity.LocalIdentity
import dev.stade.ui.i18n.LocalStrings

val DESKTOP_USER_BAR_HEIGHT = 54.dp

@Composable
fun DesktopUserBar(
    container: AppContainer,
    owner: LocalIdentity,
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onCreateStadium: () -> Unit,
    onJoinStadium: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRadar: (() -> Unit)? = null,
    settingsOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var createOpen by remember { mutableStateOf(false) }
    val torInfo by remember {
        container.transports.get(TransportType.TOR)?.info ?: MutableStateFlow(null)
    }.collectAsState()
    val onionState = onionStateOf(rememberNetworkOnline(), torInfo)
    val plusRotation by animateFloatAsState(
        targetValue = if (createOpen) 135f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "desktopCreateRotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth().height(DESKTOP_USER_BAR_HEIGHT),
        shape = RoundedCornerShape(27.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                name = owner.nickname,
                size = 38.dp,
                keySeed = owner.publicSigningKey,
                avatarBytes = owner.avatar
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    owner.nickname,
                    style = MaterialTheme.typography.titleMedium.copy(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                OnionIndicator(
                    state = onionState,
                    contentDescription = onionStateLabel(onionState),
                    size = 14.dp
                )
            }
            Spacer(Modifier.width(6.dp))
            Box {
                IconButton(onClick = { createOpen = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = strings.navCreateAction,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = plusRotation }
                    )
                }
                DropdownMenu(
                    expanded = createOpen,
                    onDismissRequest = { createOpen = false },
                    offset = DpOffset(x = 0.dp, y = 8.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.addContactTitle) },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                        onClick = {
                            createOpen = false
                            onAddContact()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.createGroupTitle) },
                        leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                        onClick = {
                            createOpen = false
                            onCreateGroup()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.createStadiumAction) },
                        leadingIcon = { Icon(Icons.Default.Podcasts, contentDescription = null) },
                        onClick = {
                            createOpen = false
                            onCreateStadium()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.joinStadiumAction) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        onClick = {
                            createOpen = false
                            onJoinStadium()
                        }
                    )
                    if (onOpenRadar != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(strings.radarTitle) },
                            leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                            onClick = {
                                createOpen = false
                                onOpenRadar()
                            }
                        )
                    }
                }
            }
            SpinningGearButton(
                contentDescription = strings.settingsAction,
                onClick = onOpenSettings,
                alreadyOpen = settingsOpen,
                buttonSize = 38.dp,
                iconSize = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DesktopModalHost(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val strings = LocalStrings.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(560.dp).height(620.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                content()
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.closeAction,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
