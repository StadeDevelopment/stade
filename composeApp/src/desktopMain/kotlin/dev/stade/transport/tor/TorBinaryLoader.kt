package dev.stade.transport.tor

import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal data class TorResourceEntry(
    val relativePath: String,
    val size: Long,
    val executable: Boolean
)

internal const val TOR_MARKER_FORMAT = "v3"

internal fun torExtractionMarker(
    platformKey: String,
    bundleVersion: String,
    entries: List<TorResourceEntry>
): String {
    val manifest = entries
        .sortedBy { it.relativePath }
        .joinToString("\n") { "${it.relativePath}:${it.size}" }
    val digest = MessageDigest.getInstance("SHA-256").digest(manifest.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { byte -> "%02x".format(byte) }
    return "$TOR_MARKER_FORMAT-$platformKey-$bundleVersion-$hex"
}

internal object TorBinaryLoader {

    private const val UNKNOWN_BUNDLE_VERSION = "unknown"
    private const val BUNDLE_VERSION_RESOURCE = ".bundle-version"

    fun prepare(appRoot: File): TorLayout {
        val plat = detectPlatformKey()
        val resourcePath = "/tor/$plat"
        val targetRoot = File(appRoot, "tor/$plat")
        if (!targetRoot.exists()) targetRoot.mkdirs()
        val torRunDir = File(targetRoot, "bin")

        val entries = listResourceEntries(resourcePath)
        if (entries.isEmpty()) {
            error("Embedded Tor binaries missing for platform '$plat' (expected resources at $resourcePath). Did the Gradle task downloadTorBinaries run?")
        }

        val markerVersionFile = File(targetRoot, ".extracted")
        val expectedMarker = torExtractionMarker(plat, readBundleVersion(resourcePath), entries)
        val markerMatches = markerVersionFile.exists() &&
            runCatching { markerVersionFile.readText().trim() }.getOrNull() == expectedMarker
        val needsExtract = !markerMatches || locateTorExecutable(torRunDir) == null

        if (needsExtract) {
            runCatching { markerVersionFile.delete() }
            extractInto(targetRoot, torRunDir, resourcePath, entries)
            markerVersionFile.writeText(expectedMarker)
        }

        val torExe = locateTorExecutable(torRunDir) ?: error("Tor executable not found under $torRunDir")
        runCatching { torExe.setExecutable(true, false) }

        val dataDir = File(targetRoot, "data")
        if (!dataDir.exists()) dataDir.mkdirs()
        runCatching {
            val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
            java.nio.file.Files.setPosixFilePermissions(dataDir.toPath(), perms)
        }
        val geoip = File(torRunDir, "data/geoip").takeIf { it.exists() }
            ?: File(torRunDir, "geoip").takeIf { it.exists() }
        val geoip6 = File(torRunDir, "data/geoip6").takeIf { it.exists() }
            ?: File(torRunDir, "geoip6").takeIf { it.exists() }
        val obfs4Exe = locateObfs4Executable(torRunDir)
        runCatching { obfs4Exe?.setExecutable(true, false) }
        return TorLayout(torRunDir, torExe, dataDir, geoip, geoip6, obfs4Exe)
    }

    private fun extractInto(
        targetRoot: File,
        torRunDir: File,
        resourcePath: String,
        entries: List<TorResourceEntry>
    ) {
        val staging = File(targetRoot, "bin.staging")
        staging.deleteRecursively()
        if (!staging.mkdirs()) error("Cannot create Tor staging directory $staging")

        entries.forEach { entry ->
            val out = File(staging, entry.relativePath)
            out.parentFile?.mkdirs()
            val written = javaClass.getResourceAsStream("$resourcePath/${entry.relativePath}")
                ?.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                ?: error("Resource missing: $resourcePath/${entry.relativePath}")
            if (entry.size >= 0L && written != entry.size) {
                staging.deleteRecursively()
                error("Truncated Tor resource ${entry.relativePath}: wrote $written of ${entry.size} bytes")
            }
            if (entry.executable) {
                runCatching { out.setExecutable(true, false) }
            }
        }

        adhocSignMacBinaries(staging)
        if (locateTorExecutable(staging) == null) {
            staging.deleteRecursively()
            error("Staged Tor bundle has no executable under $staging")
        }

        val previous = File(targetRoot, "bin.previous")
        previous.deleteRecursively()
        if (torRunDir.exists() && !torRunDir.renameTo(previous)) {
            torRunDir.deleteRecursively()
        }
        if (!staging.renameTo(torRunDir)) {
            staging.deleteRecursively()
            error("Cannot activate staged Tor bundle at $torRunDir")
        }
        previous.deleteRecursively()
    }

    private fun readBundleVersion(resourcePath: String): String =
        runCatching {
            javaClass.getResourceAsStream("$resourcePath/$BUNDLE_VERSION_RESOURCE")
                ?.use { it.readBytes().toString(Charsets.UTF_8).trim() }
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: UNKNOWN_BUNDLE_VERSION

    private fun adhocSignMacBinaries(dir: File) {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        if (!os.contains("mac") && !os.contains("darwin")) return
        dir.walkTopDown()
            .filter { it.isFile && (it.name == "tor" || it.name == "lyrebird" || it.extension == "dylib") }
            .forEach { f ->
                runCatching {
                    ProcessBuilder("codesign", "-s", "-", "-f", f.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                }
            }
    }

    private fun locateObfs4Executable(dir: File): File? {
        val candidates = listOf(
            File(dir, "tor/pluggable_transports/lyrebird.exe"),
            File(dir, "tor/pluggable_transports/lyrebird"),
            File(dir, "pluggable_transports/lyrebird.exe"),
            File(dir, "pluggable_transports/lyrebird")
        )
        return candidates.firstOrNull { it.isFile }
    }

    fun detectPlatformKey(): String {
        val osRaw = System.getProperty("os.name").lowercase(Locale.ROOT)
        val archRaw = System.getProperty("os.arch").lowercase(Locale.ROOT)
        val os = when {
            osRaw.contains("win") -> "windows"
            osRaw.contains("mac") || osRaw.contains("darwin") -> "macos"
            osRaw.contains("nux") || osRaw.contains("nix") -> "linux"
            else -> error("Unsupported OS for embedded Tor: $osRaw")
        }
        val arch = when {
            archRaw.contains("aarch64") || archRaw.contains("arm64") -> "aarch64"
            archRaw.contains("64") -> "x86_64"
            else -> error("Unsupported arch for embedded Tor: $archRaw")
        }
        if (os == "windows" && arch != "x86_64") error("Embedded Tor only ships windows-x86_64")
        if (os == "linux" && arch != "x86_64") error("Embedded Tor only ships linux-x86_64")
        return "$os-$arch"
    }

    private fun locateTorExecutable(dir: File): File? {
        val candidates = listOf(
            File(dir, "tor/tor.exe"),
            File(dir, "tor/tor"),
            File(dir, "tor.exe"),
            File(dir, "tor")
        )
        return candidates.firstOrNull { it.isFile }
    }

    private fun listResourceEntries(resourcePath: String): List<TorResourceEntry> {
        val url = javaClass.getResource(resourcePath) ?: return emptyList()
        val proto = url.protocol
        val collected = mutableListOf<TorResourceEntry>()
        if (proto == "file") {
            val rootFile = java.nio.file.Paths.get(url.toURI()).toFile()
            rootFile.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(rootFile).path.replace(File.separatorChar, '/')
                collected += TorResourceEntry(rel, f.length(), isExecutableName(rel))
            }
        } else if (proto == "jar") {
            val spec = url.toString()
            val bang = spec.indexOf("!/")
            val jarPath = spec.substring("jar:file:".length, bang)
            val inside = spec.substring(bang + 2).trimEnd('/')
            java.util.jar.JarFile(java.io.File(java.net.URLDecoder.decode(jarPath, Charsets.UTF_8))).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val name = e.name
                    if (!name.startsWith("$inside/")) continue
                    val rel = name.substring(inside.length + 1)
                    collected += TorResourceEntry(rel, e.size, isExecutableName(rel))
                }
            }
        }
        return collected
    }

    private fun isExecutableName(rel: String): Boolean {
        val name = rel.substringAfterLast('/')
        if (name.equals("tor", ignoreCase = true)) return true
        if (name.equals("tor.exe", ignoreCase = true)) return true
        if (name.equals("lyrebird", ignoreCase = true)) return true
        if (name.equals("lyrebird.exe", ignoreCase = true)) return true
        if (name.endsWith(".so") || name.contains(".so.") || name.endsWith(".dylib")) return true
        return false
    }
}
