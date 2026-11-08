package dev.stade.security

internal const val SQLITE_FIXTURE_PAGE_SIZE = 4096

internal fun sqliteFixture(marker: String): ByteArray {
    val out = ByteArray(SQLITE_FIXTURE_PAGE_SIZE)
    val magic = "SQLite format 3".encodeToByteArray() + byteArrayOf(0)
    magic.copyInto(out, 0)
    out[16] = 0x10
    out[17] = 0x00
    marker.encodeToByteArray().copyInto(out, 24)
    return out
}

internal fun sqliteFixtureMarker(bytes: ByteArray): String =
    bytes.copyOfRange(24, 48).decodeToString().trimEnd('\u0000')
