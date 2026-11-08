package dev.stade.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import dev.stade.media.mediaCacheName

@Composable
actual fun VideoPlayerView(bytes: ByteArray, modifier: Modifier, autoPlay: Boolean) {
    val context = LocalContext.current

    var file by remember(bytes) { mutableStateOf<File?>(null) }
    LaunchedEffect(bytes) {
        file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "videos_inline").apply { mkdirs() }
                val target = File(dir, "inline_${mediaCacheName(bytes)}.mp4")
                if (!target.exists() || target.length() != bytes.size.toLong()) {
                    FileOutputStream(target).use { it.write(bytes) }
                }
                target
            }.getOrNull()
        }
    }

    val ready = file
    if (ready == null) {
        Box(modifier)
        return
    }

    val exoPlayer = remember(ready) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(ready.toURI().toString()))
            prepare()
            playWhenReady = false
        }
    }
    LaunchedEffect(exoPlayer, autoPlay) {
        if (autoPlay) exoPlayer.play()
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        }
    )
}
