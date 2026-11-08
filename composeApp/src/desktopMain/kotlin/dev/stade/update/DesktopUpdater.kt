package dev.stade.update

import dev.stade.AppContainer
import dev.stade.crypto.Encoding
import dev.stade.transport.TorTransport
import dev.stade.transport.TransportType
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

private const val METADATA_TIMEOUT_MS = 20_000L
private const val READ_BUFFER = 64 * 1024

private const val UPDATE_DIR_PREFIX = "stade-update-"

sealed interface UpdateDownload {
    data class Ready(val installer: File, val sha256: String) : UpdateDownload
    data object NoAsset : UpdateDownload
    data object Failed : UpdateDownload
    data object Corrupt : UpdateDownload
}

private val json = Json { ignoreUnknownKeys = true }

private fun socksProxy(container: AppContainer): Pair<String, Int>? =
    (container.transports.get(TransportType.TOR) as? TorTransport)?.socksProxyAddress()

private fun httpClient(container: AppContainer, timeoutMs: Long): HttpClient? {
    val socks = socksProxy(container) ?: return null
    return HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = timeoutMs }
        engine { proxy = ProxyBuilder.socks(socks.first, socks.second) }
    }
}

suspend fun checkForUpdate(container: AppContainer, currentVersion: String): AvailableUpdate? =
    withContext(Dispatchers.IO) {
        val parsed = runCatching { Url(UPDATE_RELEASES_URL) }.getOrNull() ?: return@withContext null
        if (parsed.protocol.name != "https") return@withContext null

        val body = withTimeoutOrNull(METADATA_TIMEOUT_MS) {
            runCatching {
                val client = httpClient(container, METADATA_TIMEOUT_MS) ?: return@runCatching null
                try {
                    val response: HttpResponse = client.get(UPDATE_RELEASES_URL) {
                        header("Accept", "application/vnd.github+json")
                        header("User-Agent", "Stade")
                    }
                    if (response.status.value != 200) return@runCatching null
                    val channel = response.bodyAsChannel()
                    val sink = StringBuilder()
                    val chunk = ByteArray(READ_BUFFER)
                    var total = 0
                    while (total <= MAX_UPDATE_METADATA_BYTES) {
                        val read = channel.readAvailable(chunk, 0, chunk.size)
                        if (read == -1) break
                        if (read > 0) {
                            total += read
                            sink.append(String(chunk, 0, read, Charsets.UTF_8))
                        }
                    }
                    if (total <= 0 || total > MAX_UPDATE_METADATA_BYTES) null else sink.toString()
                } finally {
                    client.close()
                }
            }.getOrNull()
        } ?: return@withContext null

        runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            if (root["draft"]?.jsonPrimitive?.content == "true") return@runCatching null
            if (root["prerelease"]?.jsonPrimitive?.content == "true") return@runCatching null
            val tag = root["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
            val version = normalizeVersion(tag)
            if (!isNewerVersion(currentVersion, version)) return@runCatching null

            val assets = root["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val size = obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                UpdateAsset(name, url, size, obj["digest"]?.jsonPrimitive?.content)
            }
            val installer = selectAsset(assets, installerKindFor(System.getProperty("os.name").orEmpty()))
            AvailableUpdate(
                version = version,
                releaseUrl = root["html_url"]?.jsonPrimitive?.content ?: "",
                asset = installer,
                signature = installer?.let { signatureAssetFor(assets, it) }
            )
        }.getOrNull()
    }

suspend fun downloadUpdate(
    container: AppContainer,
    update: AvailableUpdate,
    onProgress: (Float) -> Unit
): UpdateDownload = withContext(Dispatchers.IO) {
    val asset = update.asset ?: return@withContext UpdateDownload.NoAsset
    val parsed = runCatching { Url(asset.downloadUrl) }.getOrNull()
        ?: return@withContext UpdateDownload.Failed
    if (parsed.protocol.name != "https") return@withContext UpdateDownload.Failed

    val dir = newUpdateDirectory() ?: return@withContext UpdateDownload.Failed
    val target = File(dir, safeAssetName(asset.name))
    val digest = MessageDigest.getInstance("SHA-256")

    val ok = runCatching {
        val client = httpClient(container, Long.MAX_VALUE) ?: return@runCatching false
        try {
            val response: HttpResponse = client.get(asset.downloadUrl) {
                header("User-Agent", "Stade")
            }
            if (response.status.value != 200) return@runCatching false
            val channel = response.bodyAsChannel()
            FileOutputStream(target).use { out ->
                val chunk = ByteArray(READ_BUFFER)
                var written = 0L
                while (true) {
                    val read = channel.readAvailable(chunk, 0, chunk.size)
                    if (read == -1) break
                    if (read > 0) {
                        written += read
                        if (written > MAX_UPDATE_ASSET_BYTES) return@runCatching false
                        out.write(chunk, 0, read)
                        digest.update(chunk, 0, read)
                        if (asset.sizeBytes > 0) {
                            onProgress((written.toFloat() / asset.sizeBytes).coerceIn(0f, 1f))
                        }
                    }
                }
                written == asset.sizeBytes || asset.sizeBytes <= 0L
            }
        } finally {
            client.close()
        }
    }.getOrDefault(false)

    if (!ok) {
        runCatching { target.delete() }
        return@withContext UpdateDownload.Failed
    }

    val digestBytes = digest.digest()
    val actual = digestBytes.joinToString("") { b ->
        ((b.toInt() and 0xff) + 0x100).toString(16).substring(1)
    }
    val expected = expectedSha256(asset)
    if (expected != null && actual != expected) {
        runCatching { target.delete() }
        return@withContext UpdateDownload.Corrupt
    }

    if (updateSignatureRequired) {
        val signed = verifySignature(container, update, digestBytes)
        if (!signed) {
            runCatching { target.delete() }
            return@withContext UpdateDownload.Corrupt
        }
    }
    UpdateDownload.Ready(target, actual)
}

private fun safeAssetName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
    val cleaned = base.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
    return cleaned.ifBlank { "stade-update" }
}

private fun newUpdateDirectory(): File? = runCatching {
    val root = File(System.getProperty("java.io.tmpdir"))
    root.listFiles { f -> f.isDirectory && f.name.startsWith(UPDATE_DIR_PREFIX) }
        ?.forEach { runCatching { it.deleteRecursively() } }
    val suffix = ByteArray(8).also { SecureRandom().nextBytes(it) }
        .joinToString("") { byte -> "%02x".format(byte) }
    val dir = File(root, UPDATE_DIR_PREFIX + suffix)
    if (!dir.mkdirs()) return@runCatching null
    runCatching {
        java.nio.file.Files.setPosixFilePermissions(
            dir.toPath(),
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
        )
    }
    dir
}.getOrNull()

private fun sha256Of(file: File): String? = runCatching {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(READ_BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            digest.update(buffer, 0, n)
        }
    }
    digest.digest().joinToString("") { byte -> ((byte.toInt() and 0xff) + 0x100).toString(16).substring(1) }
}.getOrNull()

fun launchInstaller(installer: File, expectedSha256: String): Boolean = runCatching {
    if (!installer.isFile) return false
    if (sha256Of(installer) != expectedSha256) return false
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        os.contains("win") -> {
            ProcessBuilder(installer.absolutePath).start()
            true
        }
        else -> {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(installer)
                true
            } else {
                false
            }
        }
    }
}.getOrDefault(false)

fun openReleasePage(url: String): Boolean = runCatching {
    if (url.isBlank()) return false
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(java.net.URI(url))
        true
    } else {
        false
    }
}.getOrDefault(false)

private suspend fun verifySignature(
    container: AppContainer,
    update: AvailableUpdate,
    digestBytes: ByteArray
): Boolean {
    val sigAsset = update.signature ?: return false
    val parsed = runCatching { Url(sigAsset.downloadUrl) }.getOrNull() ?: return false
    if (parsed.protocol.name != "https") return false

    val hex = withTimeoutOrNull(METADATA_TIMEOUT_MS) {
        runCatching {
            val client = httpClient(container, METADATA_TIMEOUT_MS) ?: return@runCatching null
            try {
                val response: HttpResponse = client.get(sigAsset.downloadUrl) {
                    header("User-Agent", "Stade")
                }
                if (response.status.value != 200) return@runCatching null
                val channel = response.bodyAsChannel()
                val chunk = ByteArray(MAX_UPDATE_SIGNATURE_BYTES)
                var total = 0
                while (total < chunk.size) {
                    val read = channel.readAvailable(chunk, total, chunk.size - total)
                    if (read == -1) break
                    if (read > 0) total += read
                }
                if (total <= 0) null else String(chunk, 0, total, Charsets.UTF_8).trim()
            } finally {
                client.close()
            }
        }.getOrNull()
    } ?: return false

    val signature = runCatching { Encoding.fromHex(hex) }.getOrNull() ?: return false
    val publicKey = runCatching { Encoding.fromHex(UPDATE_SIGNING_PUBLIC_KEY) }.getOrNull() ?: return false
    return runCatching { container.crypto.verify(publicKey, digestBytes, signature) }.getOrDefault(false)
}
