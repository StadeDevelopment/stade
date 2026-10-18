package dev.stade.ui.theme

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.stade.StadeApplication

private val _themeMode by lazy {
    val prefs = StadeApplication.instance
        .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)
    mutableStateOf(ThemeMode.fromCode(prefs.getString("theme_mode", null)))
}

actual fun getThemeMode(): State<ThemeMode> = _themeMode

actual fun setThemeMode(mode: ThemeMode) {
    _themeMode.value = mode
    StadeApplication.instance
        .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("theme_mode", mode.code)
        .apply()
}
