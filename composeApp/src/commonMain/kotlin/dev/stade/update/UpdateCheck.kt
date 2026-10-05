package dev.stade.update

const val UPDATE_RELEASES_URL = "https://api.github.com/repos/StadeDevelopment/stade/releases/latest"
const val MAX_UPDATE_METADATA_BYTES = 256 * 1024
const val MAX_UPDATE_ASSET_BYTES = 600L * 1024 * 1024
const val MAX_UPDATE_SIGNATURE_BYTES = 4 * 1024
const val UPDATE_SIGNATURE_SUFFIX = ".sig"

const val UPDATE_SIGNING_PUBLIC_KEY = "9256b3be672836dd7d48f7102cde1c3db1eabf527d61279cb44e76d34bb1db4e"

val updateSignatureRequired: Boolean get() = UPDATE_SIGNING_PUBLIC_KEY.length == 64

enum class InstallerKind { Exe, Deb, Rpm, Dmg }

class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?
)

class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    val asset: UpdateAsset?,
    val signature: UpdateAsset? = null
)

fun normalizeVersion(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

fun compareVersions(left: String, right: String): Int {
    val a = normalizeVersion(left).split('.', '-')
    val b = normalizeVersion(right).split('.', '-')
    val size = maxOf(a.size, b.size)
    for (i in 0 until size) {
        val ai = a.getOrNull(i)
        val bi = b.getOrNull(i)
        val an = ai?.toIntOrNull()
        val bn = bi?.toIntOrNull()
        when {
            an != null && bn != null -> if (an != bn) return an.compareTo(bn)
            ai == null && bi == null -> return 0
            ai == null -> return if (bn != null) -1 else 1
            bi == null -> return if (an != null) 1 else -1
            else -> {
                val c = ai.compareTo(bi)
                if (c != 0) return c
            }
        }
    }
    return 0
}

fun isNewerVersion(current: String, candidate: String): Boolean =
    compareVersions(candidate, current) > 0

fun installerKindFor(osName: String): InstallerKind? {
    val os = osName.lowercase()
    return when {
        os.contains("win") -> InstallerKind.Exe
        os.contains("mac") || os.contains("darwin") -> InstallerKind.Dmg
        os.contains("nux") || os.contains("nix") || os.contains("aix") -> InstallerKind.Deb
        else -> null
    }
}

fun selectAsset(assets: List<UpdateAsset>, kind: InstallerKind?): UpdateAsset? {
    if (kind == null) return null
    val wanted = when (kind) {
        InstallerKind.Exe -> ".exe"
        InstallerKind.Deb -> ".deb"
        InstallerKind.Rpm -> ".rpm"
        InstallerKind.Dmg -> ".dmg"
    }
    return assets.firstOrNull { it.name.lowercase().endsWith(wanted) && it.sizeBytes in 1..MAX_UPDATE_ASSET_BYTES }
}

fun expectedSha256(asset: UpdateAsset): String? =
    asset.sha256?.substringAfter("sha256:", asset.sha256)?.lowercase()?.takeIf { it.length == 64 }

fun signatureAssetFor(assets: List<UpdateAsset>, installer: UpdateAsset): UpdateAsset? =
    assets.firstOrNull {
        it.name == installer.name + UPDATE_SIGNATURE_SUFFIX &&
            it.sizeBytes in 1..MAX_UPDATE_SIGNATURE_BYTES.toLong()
    }
