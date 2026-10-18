package dev.stade.ui.theme

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.prefs.Preferences

private val themePrefs = Preferences.userRoot().node("dev/stade")

private val _themeMode by lazy {
    mutableStateOf(ThemeMode.fromCode(themePrefs.get("theme_mode", null)))
}

actual fun getThemeMode(): State<ThemeMode> = _themeMode

actual fun setThemeMode(mode: ThemeMode) {
    _themeMode.value = mode
    themePrefs.put("theme_mode", mode.code)
    runCatching { themePrefs.flush() }
}
