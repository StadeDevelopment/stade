package dev.stade.transport.tor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun bundle(vararg sizes: Pair<String, Long>): List<TorResourceEntry> =
    sizes.map { (path, size) -> TorResourceEntry(path, size, path.endsWith("tor")) }

private val baseEntries = bundle(
    "tor/tor" to 6_120_000L,
    "tor/pluggable_transports/lyrebird" to 4_300_000L,
    "data/geoip" to 2_100_000L,
    "data/geoip6" to 1_700_000L
)

class TorExtractionMarkerTest {

    @Test
    fun `marker is stable for an unchanged bundle`() {
        val first = torExtractionMarker("macos-aarch64", "15.0.26", baseEntries)
        val second = torExtractionMarker("macos-aarch64", "15.0.26", baseEntries.reversed())
        assertEquals(first, second)
    }

    @Test
    fun `marker changes when the bundle version changes`() {
        val old = torExtractionMarker("macos-aarch64", "15.0.25", baseEntries)
        val new = torExtractionMarker("macos-aarch64", "15.0.26", baseEntries)
        assertNotEquals(old, new, "a version bump must force re-extraction")
    }

    @Test
    fun `marker changes when a binary changes size`() {
        val patched = baseEntries.map {
            if (it.relativePath == "tor/tor") it.copy(size = it.size + 4_096L) else it
        }
        assertNotEquals(
            torExtractionMarker("macos-aarch64", "15.0.26", baseEntries),
            torExtractionMarker("macos-aarch64", "15.0.26", patched),
            "replaced binaries must force re-extraction even at the same version"
        )
    }

    @Test
    fun `marker changes when an entry is added or removed`() {
        val extra = baseEntries + TorResourceEntry("docs/README", 1_200L, false)
        assertNotEquals(
            torExtractionMarker("macos-aarch64", "15.0.26", baseEntries),
            torExtractionMarker("macos-aarch64", "15.0.26", extra)
        )
        assertNotEquals(
            torExtractionMarker("macos-aarch64", "15.0.26", baseEntries),
            torExtractionMarker("macos-aarch64", "15.0.26", baseEntries.drop(1))
        )
    }

    @Test
    fun `marker is platform scoped`() {
        assertNotEquals(
            torExtractionMarker("macos-aarch64", "15.0.26", baseEntries),
            torExtractionMarker("linux-x86_64", "15.0.26", baseEntries)
        )
    }

    @Test
    fun `marker records format platform and version readably`() {
        val marker = torExtractionMarker("linux-x86_64", "15.0.26", baseEntries)
        assertTrue(marker.startsWith("$TOR_MARKER_FORMAT-linux-x86_64-15.0.26-"), marker)
    }

    @Test
    fun `an unknown bundle version still distinguishes bundle content`() {
        val patched = baseEntries.map {
            if (it.relativePath == "data/geoip") it.copy(size = it.size + 1L) else it
        }
        assertNotEquals(
            torExtractionMarker("windows-x86_64", "unknown", baseEntries),
            torExtractionMarker("windows-x86_64", "unknown", patched)
        )
    }
}
