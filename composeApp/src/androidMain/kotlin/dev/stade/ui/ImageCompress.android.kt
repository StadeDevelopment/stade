package dev.stade.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

actual fun compressAvatar(bytes: ByteArray): ByteArray = compressJpeg(bytes, AVATAR_MAX_DIM, 85)

actual fun compressWallpaper(bytes: ByteArray): ByteArray = compressJpeg(bytes, WALLPAPER_MAX_DIM, 80)

private fun compressJpeg(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
