package dev.stade.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.media.MediaEditorDialog
import dev.stade.ui.compressWallpaper
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.rememberMediaPickerLauncher
import dev.stade.ui.theme.isDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.imageResource
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.chat_pattern

private const val PATTERN_OPACITY = 0.10f
private val PATTERN_TILE_COMPACT = 560.dp
private val PATTERN_TILE_WIDE = 760.dp
private val PATTERN_WIDE_THRESHOLD = 600.dp

@Composable
fun chatWallpaperBytes(container: AppContainer, chatId: String): ByteArray? {
    val bytes by remember(chatId) { container.chatWallpapers.observe(chatId) }
        .collectAsState(initial = remember(chatId) { container.chatWallpapers.get(chatId) })
    return bytes
}

@Composable
fun StadeChatPattern(
    modifier: Modifier = Modifier,
    tileSize: Dp? = null,
    opacity: Float = PATTERN_OPACITY
) {
    val mask = imageResource(Res.drawable.chat_pattern)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val brush = remember(mask) {
        ShaderBrush(ImageShader(mask, TileMode.Repeated, TileMode.Repeated))
    }
    Canvas(modifier) {
        if (mask.width <= 0) return@Canvas
        val tile = tileSize ?: if (size.width.toDp() >= PATTERN_WIDE_THRESHOLD) {
            PATTERN_TILE_WIDE
        } else {
            PATTERN_TILE_COMPACT
        }
        val factor = tile.toPx() / mask.width.toFloat()
        if (factor <= 0f) return@Canvas
        scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
            drawRect(
                brush = brush,
                size = Size(size.width / factor, size.height / factor),
                alpha = opacity.coerceIn(0f, 1f),
                colorFilter = ColorFilter.tint(tint)
            )
        }
    }
}

@Composable
fun BoxScope.ChatWallpaper(bytes: ByteArray?) {
    if (bytes == null) {
        StadeChatPattern(Modifier.matchParentSize())
        return
    }
    val bitmap = remember(bytes) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
    if (bitmap == null) {
        StadeChatPattern(Modifier.matchParentSize())
        return
    }
    val scrim = if (isDarkTheme()) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.25f)
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
    )
    Box(Modifier.matchParentSize().background(scrim))
}

class ChatWallpaperPicker internal constructor(
    private val onLaunch: () -> Unit,
    val hasWallpaper: Boolean,
    val onRemove: () -> Unit
) {
    fun launch() = onLaunch()
}

@Composable
fun rememberChatWallpaperPicker(
    container: AppContainer,
    chatId: String,
    onSaved: () -> Unit = {}
): ChatWallpaperPicker {
    val scope = rememberCoroutineScope()
    var pending by remember(chatId) { mutableStateOf<ByteArray?>(null) }
    val current = chatWallpaperBytes(container, chatId)
    val picker = rememberMediaPickerLauncher(
        onImages = { images -> images.firstOrNull()?.let { pending = it } },
        onVideo = {},
        imagesOnly = true
    )

    pending?.let { raw ->
        MediaEditorDialog(
            imageBytes = raw,
            onSave = { edited ->
                pending = null
                scope.launch {
                    val stored = withContext(Dispatchers.Default) { compressWallpaper(edited) }
                    container.chatWallpapers.set(chatId, stored)
                    onSaved()
                }
            },
            onCancel = { pending = null }
        )
    }

    return remember(chatId, current != null) {
        ChatWallpaperPicker(
            onLaunch = { picker.launch() },
            hasWallpaper = current != null,
            onRemove = { container.chatWallpapers.set(chatId, null) }
        )
    }
}

@Composable
fun ChatWallpaperPreview(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) {
        bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            StadeChatPattern(
                modifier = Modifier.fillMaxSize(),
                tileSize = 150.dp,
                opacity = 0.22f
            )
        }
    }
}

@Composable
fun ChatWallpaperCard(container: AppContainer, chatId: String, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val bytes = chatWallpaperBytes(container, chatId)
    val picker = rememberChatWallpaperPicker(container, chatId)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
        onClick = { picker.launch() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatWallpaperPreview(
                bytes = bytes,
                modifier = Modifier
                    .size(width = 34.dp, height = 46.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(strings.chatWallpaperTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (bytes != null) strings.chatWallpaperOnSubtitle
                    else strings.chatWallpaperOffSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (bytes != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { picker.onRemove() }) {
                    Text(strings.removeWallpaperAction, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
