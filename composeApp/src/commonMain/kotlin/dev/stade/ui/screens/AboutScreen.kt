package dev.stade.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stade.ui.components.BrandIcons
import dev.stade.ui.components.BrandMark
import dev.stade.ui.components.PlatformVerticalScrollbar
import dev.stade.APP_VERSION
import dev.stade.ui.i18n.LocalStrings


private data class SocialLink(
    val label: String,
    val handle: String,
    val icon: ImageVector,
    val url: String
)

private val socialLinks = listOf(
    SocialLink("X", "@stadeapp", BrandIcons.X, "https://x.com/stadeapp"),
    SocialLink("Instagram", "@stade.dev", BrandIcons.Instagram, "https://instagram.com/stade.dev"),
    SocialLink("GitHub", "StadeDevelopment", BrandIcons.GitHub, "https://github.com/StadeDevelopment"),
    SocialLink("Discord", "", BrandIcons.Discord, "https://discord.gg/MScqr7KeSP"),
    SocialLink("Website", "stade.dev", Icons.Default.Public, "https://stade.dev"),
    SocialLink("Email", "contact@stade.dev", Icons.Default.Email, "mailto:contact@stade.dev")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.aboutTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item { AboutHeader() }

                item {
                    AboutSectionLabel(strings.aboutFollowUs)
                    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    val largeCorner = 16.dp
                    val smallCorner = 4.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        socialLinks.forEachIndexed { index, link ->
                            val isFirst = index == 0
                            val isLast = index == socialLinks.lastIndex
                            val shape = RoundedCornerShape(
                                topStart = if (isFirst) largeCorner else smallCorner,
                                topEnd = if (isFirst) largeCorner else smallCorner,
                                bottomStart = if (isLast) largeCorner else smallCorner,
                                bottomEnd = if (isLast) largeCorner else smallCorner
                            )
                            SocialRow(
                                link = link,
                                comingSoonLabel = strings.aboutLinkComingSoon,
                                onClick = {
                                    if (link.url.isNotBlank()) uriHandler.openUri(link.url)
                                },
                                modifier = Modifier
                                    .padding(bottom = if (isLast) 0.dp else 2.dp)
                                    .background(color = bgColor, shape = shape)
                                    .clip(shape)
                            )
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
private fun AboutHeader() {
    val strings = LocalStrings.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(top = 24.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 96.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                strings.appTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${strings.aboutVersionLabel} $APP_VERSION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                strings.aboutAppDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 6.dp, end = 16.dp)
    )
}

@Composable
private fun SocialRow(
    link: SocialLink,
    comingSoonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasLink = link.url.isNotBlank()
    val subtitle = if (hasLink) link.handle.ifBlank { link.url } else comingSoonLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .clickable(enabled = hasLink, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SocialIconBox(icon = link.icon, tint = MaterialTheme.colorScheme.primary, enabled = hasLink)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(link.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hasLink) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SocialIconBox(icon: ImageVector, tint: Color, enabled: Boolean) {
    val effectiveTint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(effectiveTint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveTint,
            modifier = Modifier.size(20.dp)
        )
    }
}