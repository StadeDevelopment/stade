package dev.stade.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import dev.stade.ui.components.TorBootstrapCard
import dev.stade.AppContainer
import dev.stade.transport.TransportType
import dev.stade.transport.isTorBuiltIn
import dev.stade.transport.torBridgesSupported
import dev.stade.ui.components.PlatformVerticalScrollbar
import dev.stade.ui.components.maskAddress
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportsScreen(container: AppContainer, onBack: () -> Unit) {
    val lockEnabled = remember { container.secrets.isTransportsLockEnabled() }
    var pinVerified by remember { mutableStateOf(!lockEnabled) }

    if (!pinVerified) {
        SecurityPinGate(
            container = container,
            onVerified = { pinVerified = true },
            onBack = onBack
        )
        return
    }

    val strings = LocalStrings.current
    var configs by remember { mutableStateOf(container.transportSettings.all()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(strings.transportsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        val torInfo by remember {
            container.transports.get(TransportType.TOR)?.info ?: MutableStateFlow(null)
        }.collectAsState()
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val bootstrapping = torInfo?.takeIf { it.bootstrapPercent != null && !it.running }
                if (bootstrapping != null) {
                    item(key = "torBootstrapProgress") {
                        TorBootstrapCard(
                            percent = bootstrapping.bootstrapPercent ?: 0,
                            phase = bootstrapping.bootstrapPhase,
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                    }
                }
                items(configs, key = { it.type.name }) { cfg ->
                    val plugin = container.transports.get(cfg.type)
                    val info by (plugin?.info?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(transportLabel(cfg.type, strings), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        when {
                                            info == null -> strings.notRegistered
                                            info!!.running -> strings.transportRunning(info!!.message)
                                            info!!.available -> strings.transportReady
                                            else -> strings.transportUnavailable(info!!.message)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = cfg.enabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            runCatching { container.connections.setTransportEnabled(cfg.type, enabled) }
                                            configs = container.transportSettings.all()
                                        }
                                    }
                                )
                            }
                            plugin?.selfAddress()?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    strings.transportStatus(maskAddress(it)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val allAddrs = plugin?.let { runCatching { it.selfAddresses() }.getOrDefault(emptyList()) } ?: emptyList()
                            if (allAddrs.size > 1) {
                                Text(
                                    strings.transportChannelsReady(allAddrs.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (cfg.type == TransportType.TOR) {
                                Spacer(Modifier.height(12.dp))
                                if (isTorBuiltIn) {
                                    Text(
                                        strings.torBuiltinNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    TorConfigEditor(
                                        initial = cfg.config,
                                        onSave = { newCfg ->
                                            container.transportSettings.setConfig(TransportType.TOR, newCfg)
                                            configs = container.transportSettings.all()
                                            scope.launch {
                                                runCatching { container.connections.restart(TransportType.TOR) }
                                            }
                                        }
                                    )
                                }
                                if (torBridgesSupported) {
                                    TorBridgesEditor(
                                        initial = cfg.config,
                                        onSave = { newCfg ->
                                            container.transportSettings.setConfig(TransportType.TOR, newCfg)
                                            configs = container.transportSettings.all()
                                            scope.launch {
                                                runCatching { container.transports.get(TransportType.TOR)?.reload() }
                                                runCatching { container.connections.restart(TransportType.TOR) }
                                            }
                                        }
                                    )
                                } else {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        strings.bridgesNotSupportedNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PlatformVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TorConfigEditor(initial: String, onSave: (String) -> Unit) {
    val strings = LocalStrings.current
    val initialMap = remember(initial) {
        initial.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }
    var onion by remember(initial) { mutableStateOf(initialMap["onion"] ?: "") }
    var port by remember(initial) { mutableStateOf(initialMap["port"] ?: "5901") }
    var listenPort by remember(initial) { mutableStateOf(initialMap["listenPort"] ?: "") }
    var socksHost by remember(initial) { mutableStateOf(initialMap["socksHost"] ?: "127.0.0.1") }
    var socksPort by remember(initial) { mutableStateOf(initialMap["socksPort"] ?: "9050") }

    Column {
        Text(
            strings.hiddenServiceDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = onion,
            onValueChange = { onion = it.trim() },
            label = { Text(strings.hiddenServiceId) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text(strings.onionVirtport) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = listenPort,
            onValueChange = { listenPort = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text(strings.localPortLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Text(
            strings.socks5Note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = socksHost,
            onValueChange = { socksHost = it.trim() },
            label = { Text(strings.socks5Host) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = socksPort,
            onValueChange = { socksPort = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text(strings.socks5Port) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val cfg = buildString {
                    if (onion.isNotBlank()) append("onion=").append(onion).append('\n')
                    if (port.isNotBlank()) append("port=").append(port).append('\n')
                    if (listenPort.isNotBlank()) append("listenPort=").append(listenPort).append('\n')
                    append("listenHost=127.0.0.1\n")
                    if (socksHost.isNotBlank()) append("socksHost=").append(socksHost).append('\n')
                    if (socksPort.isNotBlank()) append("socksPort=").append(socksPort).append('\n')
                }
                onSave(cfg)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(strings.saveAndRestart) }
    }
}

@Composable
private fun TorBridgesEditor(initial: String, onSave: (String) -> Unit) {
    val strings = LocalStrings.current
    val parsedLines = remember(initial) {
        initial.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            .toList()
    }
    val otherLines = remember(initial) {
        parsedLines
            .filter { (key, _) -> key != "bridgesEnabled" && key != "bridgesUseBuiltIn" && key != "bridge" }
            .map { (key, value) -> "$key=$value" }
    }
    var bridgesEnabled by remember(initial) {
        mutableStateOf(parsedLines.any { it.first == "bridgesEnabled" && it.second.equals("true", ignoreCase = true) })
    }
    var useBuiltIn by remember(initial) {
        mutableStateOf(
            parsedLines.firstOrNull { it.first == "bridgesUseBuiltIn" }?.second?.equals("true", ignoreCase = true) ?: true
        )
    }
    var customText by remember(initial) {
        mutableStateOf(parsedLines.filter { it.first == "bridge" }.joinToString("\n") { it.second })
    }

    fun save() {
        val customLines = customText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        val cfg = buildString {
            otherLines.forEach { appendLine(it) }
            appendLine("bridgesEnabled=$bridgesEnabled")
            appendLine("bridgesUseBuiltIn=$useBuiltIn")
            customLines.forEach { appendLine("bridge=$it") }
        }
        onSave(cfg)
    }

    Column {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.useBridgesTitle, style = MaterialTheme.typography.bodyMedium)
                Text(
                    strings.useBridgesHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = bridgesEnabled, onCheckedChange = { bridgesEnabled = it })
        }
        if (bridgesEnabled) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.useBuiltInBridgesTitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = useBuiltIn, onCheckedChange = { useBuiltIn = it })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                strings.customBridgesHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text(strings.customBridgesLabel) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(strings.saveAndRestart) }
    }
}

private fun transportLabel(type: TransportType, strings: dev.stade.ui.i18n.AppStrings): String = when (type) {
    TransportType.LAN -> strings.lanLabel
    TransportType.TOR -> strings.torLabel
}
