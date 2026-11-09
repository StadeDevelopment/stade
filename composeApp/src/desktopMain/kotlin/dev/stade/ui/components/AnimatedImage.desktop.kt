package dev.stade.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

internal class GifFrames(val frames: List<ImageBitmap>, val delaysMs: List<Int>)

internal fun decodeGif(bytes: ByteArray): GifFrames? = runCatching {
    Codec.makeFromData(Data.makeFromBytes(bytes)).use { codec ->
        val info = codec.imageInfo
        val framePixels = info.width.toLong() * info.height.toLong()
        if (framePixels > MAX_GIF_FRAME_PIXELS) return@use null
        val count = gifFrameBudget(framePixels, codec.frameCount)
        if (count <= 1) return@use null

        val frames = ArrayList<ImageBitmap>(count)
        val delays = ArrayList<Int>(count)
        var previous: Bitmap? = null

        for (index in 0 until count) {
            val bitmap = Bitmap()
            bitmap.allocPixels(info)
            val required = codec.getFrameInfo(index).requiredFrame
            if (required >= 0) {
                previous?.readPixels()?.let { bitmap.installPixels(it) }
            }
            codec.readPixels(bitmap, index)
            frames.add(bitmap.asComposeImageBitmap())
            val duration = codec.getFrameInfo(index).duration
            delays.add(if (duration > 0) duration else DEFAULT_GIF_FRAME_DELAY_MS)
            previous = bitmap
        }
        GifFrames(frames, delays)
    }
}.getOrNull()

@Composable
actual fun AnimatedImage(
    bytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    if (!isAnimatedBytes(bytes)) {
        StaticImageBytes(bytes, contentDescription, modifier, contentScale)
        return
    }

    val key = AnimationFrames(bytes)
    var animation by remember(key) { mutableStateOf<GifFrames?>(null) }
    var settled by remember(key) { mutableStateOf(false) }
    var frameIndex by remember(key) { mutableStateOf(0) }

    LaunchedEffect(key) {
        animation = withContext(Dispatchers.Default) { decodeGif(bytes) }
        settled = true
    }

    val current = animation
    if (current == null) {
        if (settled) StaticImageBytes(bytes, contentDescription, modifier, contentScale)
        return
    }

    LaunchedEffect(current) {
        while (true) {
            delay(current.delaysMs[frameIndex].toLong())
            frameIndex = (frameIndex + 1) % current.frames.size
        }
    }

    Image(
        bitmap = current.frames[frameIndex.coerceIn(0, current.frames.lastIndex)],
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
