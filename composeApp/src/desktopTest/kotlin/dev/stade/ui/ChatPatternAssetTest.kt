package dev.stade.ui

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.ExperimentalResourceApi
import stade.composeapp.generated.resources.Res

private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

private fun ByteArray.intAt(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)

@OptIn(ExperimentalResourceApi::class)
class ChatPatternAssetTest {

    private val bytes: ByteArray by lazy {
        runBlocking { Res.readBytes("drawable/chat_pattern.png") }
    }

    @Test
    fun theChatPatternShipsWithTheApp() {
        assertTrue(bytes.size > 1024, "the chat wallpaper pattern must be packaged as a resource")
        assertContentStartsWith(PNG_MAGIC, bytes)
    }

    @Test
    fun theTileIsSquareSoItRepeatsSeamlessly() {
        val width = bytes.intAt(16)
        val height = bytes.intAt(20)

        assertEquals(width, height, "a tiled pattern must be square")
        assertTrue(width >= 1024, "the tile must be large enough to stay sharp when scaled up")
    }

    @Test
    fun theTileIsAnAlphaMaskSoItCanBeTinted() {
        val colorType = bytes[25].toInt()

        assertEquals(6, colorType, "the pattern must carry an alpha channel to be tinted by the theme")
    }

    private fun assertContentStartsWith(prefix: ByteArray, actual: ByteArray) {
        prefix.forEachIndexed { index, byte ->
            assertEquals(byte, actual[index], "byte $index of the pattern header")
        }
    }
}
