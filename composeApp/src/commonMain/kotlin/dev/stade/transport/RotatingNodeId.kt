package dev.stade.transport

import kotlinx.datetime.Clock

const val NODE_ID_ROTATION_MS = 6L * 60 * 60 * 1000

class RotatingNodeId(
    private val seed: String,
    private val rotationMs: Long = NODE_ID_ROTATION_MS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val digest: (String) -> String = ::defaultDigest
) {
    private var epoch = -1L
    private var value = ""

    fun current(): String = synchronized(this) {
        val slot = nowMs() / rotationMs
        if (slot != epoch) {
            epoch = slot
            value = digest("$seed|$slot").take(32)
        }
        value
    }
}

private fun defaultDigest(input: String): String {
    var h1 = -0x61c88647L
    var h2 = 0x9e3779b9L
    input.encodeToByteArray().forEach { byte ->
        val b = byte.toLong() and 0xff
        h1 = (h1 xor b) * 0x100000001b3L
        h2 = (h2 + b) * 0x5851f42d4c957f2dL + 0x14057b7ef767814fL
    }
    val a = h1.toULong().toString(16).padStart(16, '0')
    val b = h2.toULong().toString(16).padStart(16, '0')
    return a + b
}
