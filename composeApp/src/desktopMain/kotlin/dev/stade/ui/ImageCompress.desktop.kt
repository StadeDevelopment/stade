package dev.stade.ui

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun compressAvatar(bytes: ByteArray): ByteArray = compressJpeg(bytes, AVATAR_MAX_DIM)

actual fun compressWallpaper(bytes: ByteArray): ByteArray = compressJpeg(bytes, WALLPAPER_MAX_DIM)

private fun compressJpeg(bytes: ByteArray, maxDim: Int): ByteArray {
    val img: BufferedImage = ImageIO.read(bytes.inputStream()) ?: return bytes
    val scaled = if (img.width > maxDim || img.height > maxDim) {
        val scale = maxDim.toFloat() / maxOf(img.width, img.height)
        val w = (img.width * scale).toInt().coerceAtLeast(1)
        val h = (img.height * scale).toInt().coerceAtLeast(1)
        val dest = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = dest.createGraphics()
        try {
            g.drawImage(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
        } finally {
            g.dispose()
        }
        dest
    } else img
    val out = ByteArrayOutputStream()
    ImageIO.write(scaled, "jpeg", out)
    return out.toByteArray()
}
