package dev.stade.ui

const val AVATAR_MAX_DIM = 512
const val WALLPAPER_MAX_DIM = 1280

expect fun compressAvatar(bytes: ByteArray): ByteArray

expect fun compressWallpaper(bytes: ByteArray): ByteArray
