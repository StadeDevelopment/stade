package dev.stade.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

const val THEME_TRANSITION_MS = 320

@Composable
private fun shade(target: Color, label: String): Color {
    val value by animateColorAsState(
        targetValue = target,
        animationSpec = tween(THEME_TRANSITION_MS, easing = FastOutSlowInEasing),
        label = label
    )
    return value
}

@Composable
fun rememberBlendedColorScheme(target: ColorScheme): ColorScheme = target.copy(
    primary = shade(target.primary, "primary"),
    onPrimary = shade(target.onPrimary, "onPrimary"),
    primaryContainer = shade(target.primaryContainer, "primaryContainer"),
    onPrimaryContainer = shade(target.onPrimaryContainer, "onPrimaryContainer"),
    inversePrimary = shade(target.inversePrimary, "inversePrimary"),
    secondary = shade(target.secondary, "secondary"),
    onSecondary = shade(target.onSecondary, "onSecondary"),
    secondaryContainer = shade(target.secondaryContainer, "secondaryContainer"),
    onSecondaryContainer = shade(target.onSecondaryContainer, "onSecondaryContainer"),
    tertiary = shade(target.tertiary, "tertiary"),
    onTertiary = shade(target.onTertiary, "onTertiary"),
    tertiaryContainer = shade(target.tertiaryContainer, "tertiaryContainer"),
    onTertiaryContainer = shade(target.onTertiaryContainer, "onTertiaryContainer"),
    background = shade(target.background, "background"),
    onBackground = shade(target.onBackground, "onBackground"),
    surface = shade(target.surface, "surface"),
    onSurface = shade(target.onSurface, "onSurface"),
    surfaceVariant = shade(target.surfaceVariant, "surfaceVariant"),
    onSurfaceVariant = shade(target.onSurfaceVariant, "onSurfaceVariant"),
    surfaceTint = shade(target.surfaceTint, "surfaceTint"),
    inverseSurface = shade(target.inverseSurface, "inverseSurface"),
    inverseOnSurface = shade(target.inverseOnSurface, "inverseOnSurface"),
    error = shade(target.error, "error"),
    onError = shade(target.onError, "onError"),
    errorContainer = shade(target.errorContainer, "errorContainer"),
    onErrorContainer = shade(target.onErrorContainer, "onErrorContainer"),
    outline = shade(target.outline, "outline"),
    outlineVariant = shade(target.outlineVariant, "outlineVariant"),
    scrim = shade(target.scrim, "scrim"),
    surfaceBright = shade(target.surfaceBright, "surfaceBright"),
    surfaceDim = shade(target.surfaceDim, "surfaceDim"),
    surfaceContainer = shade(target.surfaceContainer, "surfaceContainer"),
    surfaceContainerHigh = shade(target.surfaceContainerHigh, "surfaceContainerHigh"),
    surfaceContainerHighest = shade(target.surfaceContainerHighest, "surfaceContainerHighest"),
    surfaceContainerLow = shade(target.surfaceContainerLow, "surfaceContainerLow"),
    surfaceContainerLowest = shade(target.surfaceContainerLowest, "surfaceContainerLowest")
)
