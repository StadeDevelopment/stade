package dev.stade.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val LIGHT_SURFACE_THRESHOLD = 0.5f

fun needsDarkSystemIcons(behind: Color): Boolean = behind.luminance() > LIGHT_SURFACE_THRESHOLD

@Composable
expect fun SystemBarIconContrast(statusBarBehind: Color, navigationBarBehind: Color)
