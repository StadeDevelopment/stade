package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.identity.LocalIdentity
import dev.stade.stadium.MAX_STADIUM_ABOUT_LEN
import dev.stade.stadium.isOfficial
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.formatExactDate
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StadiumInfoScreen(
    container: AppContainer,
    owner: LocalIdentity,
    stadiumId: String,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val stadiums by remember(owner.id) { container.stadiums.observeStadiums(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.stadiums.allStadiums(owner.id) })
    val stadium = stadiums.find { it.id == stadiumId }
    var about by remember(stadium?.id, stadium?.about) { mutableStateOf(stadium?.about ?: "") }
    var saved by remember(stadium?.id) { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            delay(1800)
            saved = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                title = { Text(strings.stadiumInfoTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
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
        if (stadium == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(
                        name = stadium.name,
                        size = 92.dp,
                        icon = Icons.Default.Podcasts,
                        image = if (stadium.isOfficial) painterResource(Res.drawable.app_icon) else null,
                        verified = stadium.isOfficial
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stadium.name,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (stadium.isOwner) strings.stadiumRoleOwner else strings.stadiumRoleSubscriber,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        InfoRow(
                            icon = Icons.Default.Groups,
                            text = strings.stadiumSubscriberCount(stadium.memberCount)
                        )
                        InfoRow(
                            icon = Icons.Default.CalendarMonth,
                            text = "${strings.stadiumCreatedLabel}: ${formatExactDate(stadium.createdAt)}"
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            strings.profileAboutSection,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        if (stadium.isOwner) {
                            OutlinedTextField(
                                value = about,
                                onValueChange = { about = it.take(MAX_STADIUM_ABOUT_LEN) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(strings.stadiumAboutPlaceholder) },
                                minLines = 3,
                                maxLines = 8,
                                shape = MaterialTheme.shapes.medium,
                                supportingText = {
                                    Text(
                                        "${about.length}/$MAX_STADIUM_ABOUT_LEN",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.stadiumAboutOwnerHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                enabled = about.trim() != stadium.about,
                                onClick = {
                                    val next = about.trim()
                                    about = next
                                    container.appScope.launch {
                                        container.stadiumChat.setAbout(owner, stadium, next)
                                    }
                                    saved = true
                                }
                            ) { Text(if (saved) strings.stadiumAboutSaved else strings.saveAction) }
                        } else if (stadium.about.isBlank()) {
                            Text(
                                strings.stadiumAboutEmpty,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(stadium.about, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
