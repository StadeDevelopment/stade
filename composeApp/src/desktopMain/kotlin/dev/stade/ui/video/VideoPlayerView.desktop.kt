package dev.stade.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import dev.stade.ui.i18n.I18n
import dev.stade.ui.openVideoExternally
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.io.File
import dev.stade.media.mediaCacheName

private val vlcAvailable: Boolean by lazy {
    runCatching { NativeDiscovery().discover() }.getOrDefault(false)
}

@Composable
actual fun VideoPlayerView(bytes: ByteArray, modifier: Modifier, autoPlay: Boolean) {
    if (!vlcAvailable) {
        VideoPlayerFallback(bytes, modifier)
        return
    }

    val file = remember(bytes) {
        val dir = File(System.getProperty("java.io.tmpdir"), "stade_videos_inline").apply { mkdirs() }
        val f = File(dir, "inline_${mediaCacheName(bytes)}.mp4")
        if (!f.exists() || f.length() != bytes.size.toLong()) f.writeBytes(bytes)
        f
    }
    val component = remember(file) { EmbeddedMediaPlayerComponent() }
    DisposableEffect(component) {
        if (autoPlay) {
            component.mediaPlayer().media().play(file.absolutePath)
        } else {
            component.mediaPlayer().media().startPaused(file.absolutePath)
        }
        onDispose {
            component.mediaPlayer().release()
            runCatching { file.delete() }
        }
    }
    SwingPanel(modifier = modifier, factory = { component })
}

@Composable
private fun VideoPlayerFallback(bytes: ByteArray, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                I18n.current.vlcNotFoundHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { scope.launch { openVideoExternally(bytes) } }) {
                Text(I18n.current.tapToPlayVideo)
            }
        }
    }
}
