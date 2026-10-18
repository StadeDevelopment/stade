package dev.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stade.ui.components.SettingsGroup
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.theme.ThemeMode
import dev.stade.ui.theme.getDynamicColorEnabled
import dev.stade.ui.theme.getThemeMode
import dev.stade.ui.theme.isDynamicColorSupported
import dev.stade.ui.theme.setDynamicColorEnabled
import dev.stade.ui.theme.setThemeMode
import dev.stade.ui.theme.stadeColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val mode by getThemeMode()
    val dynamicColorEnabled by getDynamicColorEnabled()
    val systemDark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                title = { Text(strings.themeTitle) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                strings.themeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeMode.entries.forEach { option ->
                    ThemePreviewTile(
                        label = themeModeLabel(option),
                        dark = when (option) {
                            ThemeMode.System -> systemDark
                            ThemeMode.Light -> false
                            ThemeMode.Dark -> true
                        },
                        split = option == ThemeMode.System,
                        selected = option == mode,
                        onClick = { setThemeMode(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (isDynamicColorSupported) {
                Spacer(Modifier.height(10.dp))
                SettingsSectionLabel(strings.themeColorSection)
                SettingsGroup {
                    row {
                        SwitchSettingsRow(
                            icon = Icons.Default.Palette,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = strings.dynamicColorTitle,
                            subtitle = strings.dynamicColorSubtitle,
                            checked = dynamicColorEnabled,
                            onCheckedChange = { setDynamicColorEnabled(it) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    val strings = LocalStrings.current
    return when (mode) {
        ThemeMode.System -> strings.themeModeSystem
        ThemeMode.Light -> strings.themeModeLight
        ThemeMode.Dark -> strings.themeModeDark
    }
}

@Composable
internal fun themeModeSubtitle(mode: ThemeMode): String = themeModeLabel(mode)

@Composable
private fun ThemePreviewTile(
    label: String,
    dark: Boolean,
    split: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.clip(shape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape
                )
        ) {
            if (split) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxSize()) { ThemeMock(dark = false) }
                    Box(Modifier.weight(1f).fillMaxSize()) { ThemeMock(dark = true) }
                }
            } else {
                ThemeMock(dark = dark)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ThemeMock(dark: Boolean) {
    val scheme = stadeColorScheme(dark)
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.background),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(scheme.surfaceContainerHighest)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .height(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }
        MockBubble(color = scheme.surfaceContainerHigh, alignRight = false, widthFraction = 0.72f)
        MockBubble(color = scheme.primary, alignRight = true, widthFraction = 0.6f)
        MockBubble(color = scheme.surfaceContainerHigh, alignRight = false, widthFraction = 0.52f)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(scheme.surfaceContainer)
        )
    }
}

@Composable
private fun MockBubble(color: Color, alignRight: Boolean, widthFraction: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = if (alignRight) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(13.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color)
        )
    }
}
