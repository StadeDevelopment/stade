package dev.stade.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarIconContrast(statusBarBehind: Color, navigationBarBehind: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val darkStatusIcons = needsDarkSystemIcons(statusBarBehind)
    val darkNavIcons = needsDarkSystemIcons(navigationBarBehind)
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = darkStatusIcons
        controller.isAppearanceLightNavigationBars = darkNavIcons
    }
}
