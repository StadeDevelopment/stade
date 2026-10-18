package dev.stade.ui

import dev.stade.ui.i18n.AppLocale
import dev.stade.ui.i18n.localeMatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun matching(query: String): List<AppLocale> =
    AppLocale.entries.filter { localeMatches(it, query) }

class LocaleSearchTest {

    @Test
    fun anEmptyQueryKeepsEveryLanguage() {
        assertEquals(AppLocale.entries, matching(""))
        assertEquals(AppLocale.entries, matching("   "))
    }

    @Test
    fun aLanguageIsFoundByItsEnglishName() {
        assertEquals(listOf(AppLocale.Turkish), matching("turkish"))
        assertEquals(listOf(AppLocale.Korean), matching("Korean"))
        assertEquals(listOf(AppLocale.Persian), matching("pers"))
    }

    @Test
    fun aLanguageIsFoundByItsOwnName() {
        assertEquals(listOf(AppLocale.Turkish), matching("Türkçe"))
        assertEquals(listOf(AppLocale.Korean), matching("한국어"))
        assertEquals(listOf(AppLocale.Arabic), matching("العربية"))
    }

    @Test
    fun turkishIsFoundWithoutItsDiacritics() {
        assertEquals(listOf(AppLocale.Turkish), matching("turkce"))
        assertEquals(listOf(AppLocale.Turkish), matching("TURKCE"))
    }

    @Test
    fun persianIsFoundWithEitherFormOfYeh() {
        assertEquals(listOf(AppLocale.Persian), matching("فارسي"))
        assertEquals(listOf(AppLocale.Persian), matching("فارسی"))
    }

    @Test
    fun aLanguageIsFoundByItsCode() {
        assertEquals(listOf(AppLocale.Korean), matching("ko"))
        assertEquals(listOf(AppLocale.Arabic), matching("ar"))
    }

    @Test
    fun anUnknownQueryMatchesNothing() {
        assertTrue(matching("klingon").isEmpty())
    }

    @Test
    fun aCodeQueryNarrowsToOneLanguage() {
        assertEquals(listOf(AppLocale.English), matching("en"))
        assertEquals(listOf(AppLocale.Turkish), matching("tr"))
    }
}
