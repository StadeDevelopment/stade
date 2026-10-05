package dev.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.ui.components.BrandMark
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val IDENTITY_WAIT_MS = 2500L

@Composable
fun OnboardingScreen(container: AppContainer, presetNickname: String? = null, onReady: (LocalIdentity) -> Unit) {
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val strings = LocalStrings.current

    LaunchedEffect(Unit) {
        val identities = container.identities.observeIdentities()

        if (presetNickname != null) {
            val existing = identities.first().firstOrNull()
            onReady(existing ?: container.identities.create(presetNickname.trim()))
            return@LaunchedEffect
        }

        val adopting = launch { onReady(identities.first { it.isNotEmpty() }.first()) }
        delay(IDENTITY_WAIT_MS)
        if (adopting.isActive) loading = false
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                BrandMark(size = 72.dp)
                Spacer(Modifier.height(16.dp))
                Text(strings.loading, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BrandMark(size = 96.dp)
                Spacer(Modifier.height(20.dp))
                Text(strings.welcomeTitle, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.welcomeDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(strings.nicknamePlaceholder) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = nickname.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val id = container.identities.create(nickname.trim())
                            onReady(id)
                        }
                    },
                    modifier = Modifier.widthIn(min = 280.dp, max = 420.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(strings.createIdentity) }
            }
        }
    }
}
