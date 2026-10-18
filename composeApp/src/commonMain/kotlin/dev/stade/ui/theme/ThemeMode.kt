package dev.stade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

enum class ThemeMode(val code: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromCode(code: String?): ThemeMode =
            entries.firstOrNull { it.code == code } ?: System
    }
}

expect fun getThemeMode(): State<ThemeMode>

expect fun setThemeMode(mode: ThemeMode)

@Composable
fun isDarkTheme(): Boolean = when (getThemeMode().value) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
