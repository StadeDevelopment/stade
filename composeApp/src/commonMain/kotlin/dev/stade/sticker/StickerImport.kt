package dev.stade.sticker

const val MAX_IMPORT_ENTRIES = 120
const val MAX_IMPORT_ENTRY_BYTES = dev.stade.message.MAX_ATTACHMENT_BYTES
const val MAX_IMPORT_ARCHIVE_BYTES = 64L * 1024 * 1024

class ImportedImage(val name: String, val bytes: ByteArray)

class ImportedPack(
    val title: String,
    val author: String,
    val images: List<ImportedImage>
)

enum class ImportRejection { NotReadable, Empty, TooLarge }

sealed interface ImportSourceOutcome {
    data class Pack(val pack: ImportedPack) : ImportSourceOutcome
    data class Rejected(val reason: ImportRejection) : ImportSourceOutcome
}

class ImportSummary(
    val packTitle: String,
    val added: Int,
    val duplicates: Int,
    val unreadable: Int
) {
    val isEmpty: Boolean get() = added == 0
}

fun isZipArchive(bytes: ByteArray): Boolean =
    bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
        (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

fun isWebpBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    val riff = bytes.copyOfRange(0, 4).decodeToString()
    val webp = bytes.copyOfRange(8, 12).decodeToString()
    return riff == "RIFF" && webp == "WEBP"
}

fun isAnimatedWebpBytes(bytes: ByteArray): Boolean {
    if (!isWebpBytes(bytes)) return false
    if (bytes.size < 21) return false
    val fourth = bytes.copyOfRange(12, 16).decodeToString()
    if (fourth != "VP8X") return false
    return (bytes[20].toInt() and 0x02) != 0
}

fun isPngBytes(bytes: ByteArray): Boolean =
    bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()

fun isJpegBytes(bytes: ByteArray): Boolean =
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

fun looksLikeSticker(bytes: ByteArray): Boolean =
    isWebpBytes(bytes) || isPngBytes(bytes) || isJpegBytes(bytes) ||
        dev.stade.ui.components.isGifBytes(bytes)

fun keepsItsOwnFrames(bytes: ByteArray): Boolean =
    dev.stade.ui.components.isGifBytes(bytes) || isAnimatedWebpBytes(bytes)

fun packTitleFromFileName(fileName: String): String {
    val stripped = fileName.substringAfterLast('/').substringBeforeLast('.')
    val cleaned = stripped.replace('_', ' ').replace('-', ' ').trim()
    return cleaned.ifBlank { "Stickers" }
}

fun normalizeStickerBytes(bytes: ByteArray): ByteArray? {
    if (bytes.isEmpty() || bytes.size > MAX_IMPORT_ENTRY_BYTES) return null
    if (!looksLikeSticker(bytes)) return null
    if (keepsItsOwnFrames(bytes)) return bytes
    val encoded = runCatching { dev.stade.media.encodeStickerPng(bytes) }.getOrNull() ?: return null
    return if (isPngBytes(encoded) && encoded.isNotEmpty()) encoded else null
}

expect fun readStickerArchive(fileName: String, bytes: ByteArray): ImportSourceOutcome

class ImportResult(
    val packs: List<String>,
    val added: Int,
    val duplicates: Int,
    val unreadable: Int
)

object StickerImporter {

    fun importFiles(
        manager: StickerManager,
        ownerId: String,
        files: List<dev.stade.ui.PickedStickerFile>,
        normalize: (ByteArray) -> ByteArray? = ::normalizeStickerBytes
    ): ImportResult {
        val packs = mutableListOf<String>()
        var added = 0
        var duplicates = 0
        var unreadable = 0
        val loose = mutableListOf<ImportedImage>()

        for (file in files) {
            if (isZipArchive(file.bytes)) {
                when (val outcome = readStickerArchive(file.name, file.bytes)) {
                    is ImportSourceOutcome.Pack -> {
                        val summary = manager.importPack(ownerId, outcome.pack, normalize)
                        if (summary.added > 0) packs.add(summary.packTitle)
                        added += summary.added
                        duplicates += summary.duplicates
                        unreadable += summary.unreadable
                    }
                    is ImportSourceOutcome.Rejected -> unreadable++
                }
            } else if (looksLikeSticker(file.bytes)) {
                loose.add(ImportedImage(file.name, file.bytes))
            } else {
                unreadable++
            }
        }

        if (loose.isNotEmpty()) {
            val summary = manager.importLoose(ownerId, loose, normalize)
            added += summary.added
            duplicates += summary.duplicates
            unreadable += summary.unreadable
        }

        return ImportResult(packs, added, duplicates, unreadable)
    }
}
