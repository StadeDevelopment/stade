package dev.stade.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import dev.stade.ui.components.MAX_GIF_FRAME_PIXELS

@Composable
actual fun AnimatedImage(
    bytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val key = AnimationFrames(bytes)
    val playable = remember(key) {
        isAnimatedBytes(bytes) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
    if (!playable) {
        StaticImageBytes(bytes, contentDescription, modifier, contentScale)
        return
    }

    var drawable by remember(key) { mutableStateOf<Drawable?>(null) }
    var failed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        val decoded = withContext(Dispatchers.Default) {
            runCatching {
                ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ) { decoder, info, _ ->
                    val pixels = info.size.width.toLong() * info.size.height.toLong()
                    if (pixels > MAX_GIF_FRAME_PIXELS) {
                        throw IllegalStateException("animation frame too large")
                    }
                    decoder.isMutableRequired = false
                }
            }.getOrNull()
        }
        if (decoded == null) failed = true else drawable = decoded
    }

    DisposableEffect(drawable) {
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }

    val ready = drawable
    if (failed || ready == null) {
        StaticImageBytes(bytes, contentDescription, modifier, contentScale)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                adjustViewBounds = true
                scaleType = if (contentScale == ContentScale.Crop) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.FIT_CENTER
                }
            }
        },
        update = { view ->
            view.contentDescription = contentDescription
            if (view.drawable !== ready) {
                view.setImageDrawable(ready)
                (ready as? AnimatedImageDrawable)?.apply {
                    repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    start()
                }
            }
        }
    )
}
