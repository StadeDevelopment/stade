package dev.stade.ui.i18n

import androidx.compose.runtime.State
import androidx.compose.ui.unit.LayoutDirection

enum class AppLocale(val code: String, val rightToLeft: Boolean = false) {
    English("en"),
    Turkish("tr"),
    Korean("ko"),
    Persian("fa", rightToLeft = true),
    Arabic("ar", rightToLeft = true)
}

expect fun getLocalePreference(): State<AppLocale>
expect fun setLocalePreference(locale: AppLocale)
expect fun getSystemLocale(): AppLocale

fun localeToStrings(locale: AppLocale): AppStrings = when (locale) {
    AppLocale.English -> EnglishStrings
    AppLocale.Turkish -> TurkishStrings
    AppLocale.Korean -> KoreanStrings
    AppLocale.Persian -> PersianStrings
    AppLocale.Arabic -> ArabicStrings
}

fun localeToLayoutDirection(locale: AppLocale): LayoutDirection =
    if (locale.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr

fun localeDisplayName(locale: AppLocale): String = when (locale) {
    AppLocale.English -> "English"
    AppLocale.Turkish -> "Türkçe"
    AppLocale.Korean -> "한국어"
    AppLocale.Persian -> "فارسی"
    AppLocale.Arabic -> "العربية"
}

fun localeEnglishName(locale: AppLocale): String = when (locale) {
    AppLocale.English -> "English"
    AppLocale.Turkish -> "Turkish"
    AppLocale.Korean -> "Korean"
    AppLocale.Persian -> "Persian"
    AppLocale.Arabic -> "Arabic"
}

private val SEARCH_FOLDING = mapOf(
    '\u0131' to 'i', '\u00FC' to 'u', '\u00F6' to 'o', '\u00E7' to 'c', '\u015F' to 's', '\u011F' to 'g',
    '\u00E2' to 'a', '\u00EE' to 'i', '\u00FB' to 'u', '\u00E9' to 'e', '\u00E8' to 'e',
    '\u0622' to '\u0627', '\u0623' to '\u0627', '\u0625' to '\u0627',
    '\u064A' to '\u06CC', '\u0643' to '\u06A9', '\u0629' to '\u0647'
)

fun foldForSearch(value: String): String =
    value.lowercase().map { SEARCH_FOLDING[it] ?: it }.joinToString("").trim()

fun localeMatches(locale: AppLocale, query: String): Boolean {
    val folded = foldForSearch(query)
    if (folded.isEmpty()) return true
    return foldForSearch(localeDisplayName(locale)).contains(folded) ||
        foldForSearch(localeEnglishName(locale)).contains(folded) ||
        locale.code.startsWith(folded)
}
