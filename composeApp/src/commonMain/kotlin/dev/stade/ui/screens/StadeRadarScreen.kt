package dev.stade.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.stade.ui.components.HomeIdentityHeader
import dev.stade.ui.components.LocalHomeTopBarClearance
import dev.stade.ui.components.HOME_BAR_HEIGHT
import dev.stade.ui.components.LocalHomeBarClearance
import dev.stade.AppContainer
import dev.stade.contact.InviteParseResult
import dev.stade.contact.InvitePayload
import dev.stade.crypto.Encoding
import dev.stade.identity.LocalIdentity
import dev.stade.radar.RadarPeer
import dev.stade.radar.RadarStatus
import dev.stade.radar.getRadarAnonymous
import dev.stade.radar.getRadarIntroSuppressed
import dev.stade.radar.getRadarInvisible
import dev.stade.radar.radarFingerprint
import dev.stade.radar.rememberRadarSession
import dev.stade.radar.setRadarAnonymous
import dev.stade.radar.setRadarIntroSuppressed
import dev.stade.radar.setRadarInvisible
import dev.stade.transport.TransportType
import dev.stade.ui.ACCEPT_INVITE_TIMEOUT_MS
import dev.stade.ui.BeginAcceptResult
import dev.stade.ui.beginAcceptInvite
import dev.stade.ui.components.TopBarPill
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.avatarPaletteIndex
import dev.stade.ui.components.StadeIdCard
import dev.stade.ui.i18n.AppStrings
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.inviteErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class RadarCandidate(
    val peer: RadarPeer,
    val code: String,
    val payload: InvitePayload
)

private const val SWEEP_CYCLE_MS = 3800
private const val PULSE_CYCLE_MS = 2600
private val STAGE_MIN_SIZE = 220.dp
private val PEER_DOT_SIZE = 44.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StadeRadarScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    hideTopBar: Boolean = false,
    openSettingsTicket: Int = 0
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val torPlugin = remember { container.transports.get(TransportType.TOR) }
    val torInfo by remember(torPlugin) {
        torPlugin?.info ?: kotlinx.coroutines.flow.MutableStateFlow(null)
    }.collectAsState(initial = null)

    var inviteBytes by remember {
        mutableStateOf(container.handshake.createInvite(owner, container.connections.selfAddresses()).raw)
    }
    LaunchedEffect(torInfo?.running) {
        val refreshed = withContext(Dispatchers.Default) {
            container.handshake.createInvite(owner, container.connections.selfAddresses()).raw
        }
        inviteBytes = refreshed
    }

    val introSuppressed by getRadarIntroSuppressed()
    val anonymous by getRadarAnonymous()
    val invisible by getRadarInvisible()

    var showIntro by remember { mutableStateOf(!introSuppressed) }
    var showSettings by remember { mutableStateOf(false) }
    var handledSettingsTicket by remember { mutableStateOf(openSettingsTicket) }
    LaunchedEffect(openSettingsTicket) {
        if (openSettingsTicket != handledSettingsTicket) {
            handledSettingsTicket = openSettingsTicket
            showSettings = true
        }
    }

    val contacts by remember(owner.id) { container.contacts.observeContacts(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.contacts.contacts(owner.id) })
    val ownFingerprint = remember(owner.publicSigningKey) {
        radarFingerprint(container.crypto::hash, owner.publicSigningKey)
    }
    val ownPalette = remember(owner.publicSigningKey) { avatarPaletteIndex(owner.publicSigningKey) }
    val knownFingerprints = remember(contacts) {
        contacts.filter { it.kind == 0 }
            .map { radarFingerprint(container.crypto::hash, it.publicSigningKey) }
            .toSet()
    }

    val foreground by container.isAppInForeground.collectAsState()
    val session = rememberRadarSession(
        nickname = if (anonymous) "" else owner.nickname,
        fingerprint = if (anonymous) "" else ownFingerprint,
        paletteIndex = if (anonymous) -1 else ownPalette,
        active = foreground,
        broadcasting = !invisible && !showIntro,
        inviteProvider = { inviteBytes }
    )

    val peers = session.peers.filter {
        if (it.fingerprint.isEmpty()) true
        else it.fingerprint != ownFingerprint && it.fingerprint !in knownFingerprints
    }

    var connectingTo by remember { mutableStateOf<RadarPeer?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var candidate by remember { mutableStateOf<RadarCandidate?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var fetchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(status) {
        if (status != null) {
            delay(6000)
            status = null
        }
    }

    fun connect(peer: RadarPeer) {
        if (connectingTo != null || candidate != null) return
        connectingTo = peer
        progress = 0f
        status = null
        fetchJob = scope.launch {
            val raw = session.fetchInvite(peer) { progress = it }
            connectingTo = null
            if (raw == null || raw.isEmpty()) {
                status = strings.radarExchangeFailed
                return@launch
            }
            val code = "STADE2-" + Encoding.toBase32(raw)
            val parsed = container.handshake.parseInviteDetailed(code)
            val payload = (parsed as? InviteParseResult.Ok)?.payload
            if (payload == null) {
                status = inviteErrorText(parsed, strings) ?: strings.invalidInvite
                return@launch
            }
            if (payload.signingPublicKey.contentEquals(owner.publicSigningKey)) {
                status = strings.selfInviteError
                return@launch
            }
            val known = container.contacts.findByStadeId(payload.stadeId)
            if (known != null && known.kind == 0) {
                status = strings.alreadyAdded(payload.stadeId)
                return@launch
            }
            candidate = RadarCandidate(peer, code, payload)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (hideTopBar) return@Scaffold
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { HomeIdentityHeader(container = container, owner = owner) },
                actions = {
                    Row(
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest).then(Modifier.padding(end = 4.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TopBarPill(
                            icon = Icons.Default.Tune,
                            contentDescription = strings.radarSettingsTitle,
                            onClick = { showSettings = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = if (hideTopBar) LocalHomeTopBarClearance.current else padding.calculateTopPadding(),
                    bottom = 0.dp
                )
        ) {
            val density = LocalDensity.current
            val systemBottom = with(density) {
                WindowInsets.navigationBars.getBottom(density).toDp()
            }
            val chromeBottom =
                if (hideTopBar) systemBottom + HOME_BAR_HEIGHT else padding.calculateBottomPadding()
            if (session.status == RadarStatus.Scanning) {
                Column(Modifier.fillMaxSize()) {
                    RadarStage(
                        peers = peers,
                        owner = owner,
                        onPeerClick = { connect(it) },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    RadarPanel(
                        peers = peers,
                        discoverable = session.discoverable,
                        anonymous = anonymous,
                        invisible = invisible,
                        bottomChrome = chromeBottom,
                        onPeerClick = { connect(it) }
                    )
                }
            } else {
                RadarBlockedState(
                    status = session.status,
                    strings = strings,
                    onResolve = { session.resolve() },
                    modifier = Modifier.fillMaxSize().padding(bottom = chromeBottom)
                )
            }

            status?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 3.dp
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    connectingTo?.let { peer ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(strings.radarConnectingTo(peer.nickname.ifBlank { strings.radarUnknownPeer })) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(strings.radarScanning, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    fetchJob?.cancel()
                    fetchJob = null
                    connectingTo = null
                }) { Text(strings.cancel) }
            }
        )
    }

    candidate?.let { found ->
        AlertDialog(
            onDismissRequest = { candidate = null },
            title = { Text(strings.radarConfirmTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.radarConfirmBody(found.payload.nickname))
                    StadeIdCard(stadeId = found.payload.stadeId)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (val result = container.beginAcceptInvite(owner, found.code, "", strings)) {
                        is BeginAcceptResult.Error -> status = result.message
                        is BeginAcceptResult.NoAddress -> status = strings.inviteAcceptedNoAddr
                        is BeginAcceptResult.Dialing -> {
                            val accepted = strings.inviteAccepted(result.payload.nickname, result.addressCount)
                            status = if (result.lanOnly) {
                                accepted + "\n" + strings.inviteLanOnlyWarning
                            } else {
                                accepted
                            }
                            val targetId = result.payload.stadeId
                            scope.launch {
                                val added = withTimeoutOrNull(ACCEPT_INVITE_TIMEOUT_MS) {
                                    container.contacts.observeContacts(owner.id)
                                        .first { list -> list.any { it.id == targetId } }
                                    true
                                } ?: false
                                if (added) status = strings.contactAdded(result.payload.nickname)
                            }
                        }
                    }
                    candidate = null
                }) { Text(strings.addAction) }
            },
            dismissButton = {
                TextButton(onClick = { candidate = null }) { Text(strings.notNowAction) }
            }
        )
    }

    if (showSettings) {
        RadarSettingsSheet(
            anonymous = anonymous,
            invisible = invisible,
            onAnonymousChange = { setRadarAnonymous(it) },
            onInvisibleChange = { setRadarInvisible(it) },
            onDismiss = { showSettings = false }
        )
    }

    if (showIntro) {
        RadarIntroDialog(
            onDismiss = { suppress ->
                if (suppress) setRadarIntroSuppressed(true)
                showIntro = false
            }
        )
    }
}

@Composable
private fun RadarIntroDialog(onDismiss: (Boolean) -> Unit) {
    val strings = LocalStrings.current
    var dontShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        title = {
            Text(
                strings.radarIntroTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    strings.radarIntroBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RadarIntroPoint(Icons.Default.WifiTethering, strings.radarIntroStepOne)
                RadarIntroPoint(Icons.Default.PersonAdd, strings.radarIntroStepTwo)
                RadarIntroPoint(Icons.Default.Lock, strings.radarIntroStepThree)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { dontShowAgain = !dontShowAgain },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        strings.radarIntroDontShowAgain,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(dontShowAgain) }) {
                Text(strings.radarIntroAction)
            }
        }
    )
}

@Composable
private fun RadarIntroPoint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadarSettingsSheet(
    anonymous: Boolean,
    invisible: Boolean,
    onAnonymousChange: (Boolean) -> Unit,
    onInvisibleChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val barClearance = LocalHomeBarClearance.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp)
                .padding(bottom = 28.dp + barClearance)
        ) {
            Text(
                strings.radarSettingsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            RadarToggleRow(
                icon = Icons.Default.NoAccounts,
                title = strings.radarAnonymousTitle,
                body = if (invisible) strings.radarAnonymousLockedBody else strings.radarAnonymousBody,
                checked = anonymous,
                enabled = !invisible,
                onCheckedChange = onAnonymousChange
            )
            RadarToggleRow(
                icon = Icons.Default.VisibilityOff,
                title = strings.radarGhostTitle,
                body = strings.radarGhostBody,
                checked = invisible,
                enabled = true,
                onCheckedChange = onInvisibleChange
            )
        }
    }
}

@Composable
private fun RadarToggleRow(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun RadarStage(
    peers: List<RadarPeer>,
    owner: LocalIdentity,
    onPeerClick: (RadarPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SWEEP_CYCLE_MS, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_CYCLE_MS, easing = LinearEasing)),
        label = "pulse"
    )

    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight).coerceAtLeast(STAGE_MIN_SIZE)
        val sidePx = with(density) { side.toPx() }
        val selfSize = (side * 0.19f).coerceIn(42.dp, 72.dp)

        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val middle = Offset(size.width / 2f, size.height / 2f)
                val stroke = radius * 0.006f + 1f

                repeat(4) { index ->
                    drawCircle(
                        color = ring,
                        radius = radius * (index + 1) / 4f,
                        center = middle,
                        alpha = 0.26f - index * 0.045f,
                        style = Stroke(width = stroke)
                    )
                }

                drawCircle(
                    color = accent,
                    radius = radius * pulse,
                    center = middle,
                    alpha = (1f - pulse) * 0.35f,
                    style = Stroke(width = stroke * 1.6f)
                )

                rotate(degrees = sweep, pivot = middle) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to accent.copy(alpha = 0f),
                            0.16f to accent.copy(alpha = 0.20f),
                            0.25f to accent.copy(alpha = 0f),
                            1f to accent.copy(alpha = 0f),
                            center = middle
                        ),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = Offset(middle.x - radius, middle.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                }
            }

            Avatar(
                name = owner.nickname,
                size = selfSize,
                keySeed = owner.publicSigningKey,
                avatarBytes = owner.avatar
            )

            peers.forEach { peer ->
                val angle = peerAngle(peer.id) * (PI.toFloat() / 180f)
                val distance = sidePx / 2f * peerDistance(peer.rssi)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            IntOffset(
                                (cos(angle) * distance).roundToInt(),
                                (sin(angle) * distance).roundToInt()
                            )
                        }
                        .clip(CircleShape)
                        .clickable { onPeerClick(peer) }
                ) {
                    Avatar(
                        name = peer.nickname.ifBlank { "?" },
                        size = PEER_DOT_SIZE,
                        paletteIndex = peer.paletteIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarPanel(
    peers: List<RadarPeer>,
    discoverable: Boolean,
    anonymous: Boolean,
    invisible: Boolean,
    bottomChrome: Dp,
    onPeerClick: (RadarPeer) -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(
                start = 18.dp,
                end = 18.dp,
                top = 16.dp,
                bottom = 16.dp + bottomChrome
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (peers.isEmpty()) strings.radarScanning else strings.radarNearbyCount(peers.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (peers.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    invisible -> strings.radarGhostActive
                    !discoverable -> strings.radarNotDiscoverable
                    anonymous -> strings.radarAnonymousActive
                    else -> strings.radarDiscoverable
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (peers.isEmpty()) {
                Text(
                    strings.radarEmptyBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(peers, key = { it.id }) { peer ->
                        RadarPeerRow(peer = peer, onClick = { onPeerClick(peer) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarPeerRow(peer: RadarPeer, onClick: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name = peer.nickname.ifBlank { "?" },
            size = 40.dp,
            paletteIndex = peer.paletteIndex
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                peer.nickname.ifBlank { strings.radarUnknownPeer },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                proximityLabel(peer.rssi, strings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.PersonAdd,
            contentDescription = strings.addAction,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RadarBlockedState(
    status: RadarStatus,
    strings: AppStrings,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (status) {
        RadarStatus.PermissionRequired -> strings.radarPermissionTitle
        RadarStatus.BluetoothOff -> strings.radarBluetoothOffTitle
        else -> strings.radarUnsupportedTitle
    }
    val body = when (status) {
        RadarStatus.PermissionRequired -> strings.radarPermissionBody
        RadarStatus.BluetoothOff -> strings.radarBluetoothOffBody
        else -> strings.radarUnsupportedBody
    }
    val action = when (status) {
        RadarStatus.PermissionRequired -> strings.radarGrantAction
        RadarStatus.BluetoothOff -> strings.radarEnableBluetoothAction
        else -> null
    }

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onResolve, shape = MaterialTheme.shapes.medium) {
                Text(action)
            }
        }
    }
}

private fun proximityLabel(rssi: Int, strings: AppStrings): String = when {
    rssi >= -60 -> strings.radarProximityNear
    rssi >= -80 -> strings.radarProximityMedium
    else -> strings.radarProximityFar
}

private fun peerAngle(id: String): Float {
    var hash = -2128831035
    for (char in id) {
        hash = (hash xor char.code) * 16777619
    }
    return ((hash and 0x7fffffff) % 3600) / 10f
}

private fun peerDistance(rssi: Int): Float {
    val clamped = rssi.coerceIn(-100, -40)
    return 0.30f + ((-40 - clamped) / 60f) * 0.50f
}
