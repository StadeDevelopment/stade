package dev.stade.ui

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.ExperimentalResourceApi
import stade.composeapp.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
class StadeyOnionAssetTest {

    private val bytes: ByteArray by lazy {
        runBlocking { Res.readBytes("drawable/stadey_onion.png") }
    }

    private fun intAt(offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    @Test
    fun stadeyShipsWithTheApp() {
        assertTrue(bytes.size > 1024, "the mascot must be packaged as a resource")
        val magic = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        magic.forEachIndexed { index, byte ->
            assertEquals(byte, bytes[index], "byte $index of the mascot header")
        }
    }

    @Test
    fun stadeyHasNoBackgroundToSitOn() {
        assertEquals(
            6,
            bytes[25].toInt(),
            "the mascot must keep its alpha channel, or it peeks out as a rectangle"
        )
    }

    @Test
    fun stadeyIsSharpEnoughToPeekButNotHeavierThanNeeded() {
        val width = intAt(16)
        val height = intAt(20)

        assertTrue(height >= 360, "the mascot must stay sharp at a phone's pixel density")
        assertTrue(height <= 1024, "a decoration this size does not need a full resolution asset")
        assertTrue(width < height, "the artwork is taller than it is wide, so it is cropped to its ink")
    }
}
