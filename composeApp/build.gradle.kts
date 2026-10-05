import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties
import java.net.URI
import java.security.MessageDigest

val appVersion = "0.3.5"
val appVersionCode = 29

val appVersionSource = file("src/commonMain/kotlin/dev/stade/AppVersion.kt")
val declaredAppVersion = Regex("""APP_VERSION\s*=\s*"([^"]+)"""")
    .find(appVersionSource.readText())?.groupValues?.get(1)
check(declaredAppVersion == appVersion) {
    "APP_VERSION in ${appVersionSource.name} is $declaredAppVersion but the build declares $appVersion. Update both."
}

val macAppVersion = appVersion.split(".").let { parts ->
    val major = (parts.getOrNull(0)?.toIntOrNull() ?: 0) + 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    "$major.$minor.$patch"
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)

    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.network)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(compose.material3)
            }
        }
        val jvmCommonMain by getting {
            dependencies {
                implementation(libs.bouncycastle)
                implementation(libs.concentus)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.zxing.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.biometric)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.sqldelight.android.driver)
                implementation("androidx.media3:media3-exoplayer:1.4.1")
                implementation("androidx.media3:media3-ui:1.4.1")
                implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
                implementation("com.google.mlkit:vision-common:16.7.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.sqlite.driver)
                runtimeOnly(libs.slf4j.nop)
                implementation("net.java.dev.jna:jna:5.14.0")
                implementation("net.java.dev.jna:jna-platform:5.14.0")
                implementation("uk.co.caprica:vlcj:4.8.2")
            }
            resources.srcDir(layout.buildDirectory.dir("torBinaries"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

android {
    namespace = "dev.stade"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.stade"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    val localProps = Properties().also { props ->
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { props.load(it) }
    }

    signingConfigs {
        create("release") {
            val ksPath = localProps.getProperty("keystore.path")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = localProps.getProperty("keystore.password") ?: ""
                keyAlias = localProps.getProperty("keystore.alias") ?: ""
                keyPassword = localProps.getProperty("keystore.keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ksPath = localProps.getProperty("keystore.path")
            signingConfig = if (ksPath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
        create("profiling") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("torAndroid/jniLibs"))
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("torAndroid/assets"))
}

compose.desktop {
    application {
        mainClass = "dev.stade.MainKt"
        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
        javaHome = localProps.getProperty("java.home") ?: System.getProperty("java.home")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Exe, TargetFormat.Dmg)
            modules(
                "jdk.unsupported",
                "java.sql",
                "java.naming",
                "java.net.http",
                "java.management",
                "java.security.jgss",
                "jdk.crypto.cryptoki",
                "jdk.security.auth",
                "java.desktop"
            )
            packageName = "Stade"
            packageVersion = appVersion
            windows {
                iconFile.set(project.file("src/desktopMain/resources/app_icon_desktop.ico"))
                menuGroup = "Stade"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/app_icon_desktop.png"))
                packageName = "stade"
                menuGroup = "Network"
                appCategory = "Network"
                debMaintainer = "Stade Development <contact@stade.dev>"
                appRelease = "1"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/app_icon_desktop.icns"))
                bundleID = "dev.stade.app"
                packageVersion = macAppVersion
                packageBuildVersion = appVersionCode.toString()
            }
        }
    }
}

val fixDmgVolumeIcon by tasks.registering {
    group = "compose desktop"
    description = "Sets the built DMG's own Finder icon to the app icon (jpackage leaves it generic)."
    doLast {
        runCatching {
            val macIconFile = project.file("src/desktopMain/resources/app_icon_desktop.icns")
            if (!macIconFile.exists()) {
                logger.warn("[dmg-icon] $macIconFile not found, skipping")
                return@doLast
            }
            val setFilePath = runCatching {
                val p = ProcessBuilder("xcrun", "-find", "SetFile").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText().trim()
                if (p.waitFor() == 0 && out.isNotEmpty()) out else null
            }.getOrNull()
            if (setFilePath == null) {
                logger.warn("[dmg-icon] SetFile not found (needs Xcode Command Line Tools: xcode-select --install) — leaving DMG icon as-is")
                return@doLast
            }
            val dmgDirs = listOf(
                layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile,
                layout.buildDirectory.dir("compose/binaries/main-release/dmg").get().asFile
            )
            for (dmgDir in dmgDirs) {
                val dmgFile = dmgDir.takeIf { it.isDirectory }?.listFiles { f -> f.extension == "dmg" }?.firstOrNull()
                    ?: continue
                runCatching { setVolumeIcon(dmgFile, macIconFile, setFilePath) }
                    .onFailure { logger.warn("[dmg-icon] Failed to set volume icon for ${dmgFile.name}: ${it.message}") }
            }
        }.onFailure { logger.warn("[dmg-icon] Skipped: ${it.message}") }
    }
}

fun runProcess(vararg args: String): Boolean {
    val process = ProcessBuilder(*args).redirectErrorStream(true).start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        val output = process.inputStream.bufferedReader().readText().trim()
        throw GradleException("${args.joinToString(" ")} failed ($exitCode): $output")
    }
    return true
}

fun setVolumeIcon(dmgFile: File, icnsFile: File, setFilePath: String) {
    val rwDmg = File(dmgFile.parentFile, "${dmgFile.nameWithoutExtension}-rw-tmp.dmg")
    val mountPoint = File(System.getProperty("java.io.tmpdir"), "stade-dmg-mount-${System.currentTimeMillis()}")
    rwDmg.delete()
    mountPoint.mkdirs()
    try {
        runProcess("hdiutil", "convert", dmgFile.absolutePath, "-format", "UDRW", "-o", rwDmg.absolutePath)
        runProcess("hdiutil", "attach", rwDmg.absolutePath, "-mountpoint", mountPoint.absolutePath, "-nobrowse", "-noautoopen")
        try {
            val volumeIcon = File(mountPoint, ".VolumeIcon.icns")
            icnsFile.copyTo(volumeIcon, overwrite = true)
            runProcess(setFilePath, "-a", "C", mountPoint.absolutePath)
            runProcess(setFilePath, "-a", "V", volumeIcon.absolutePath)
        } finally {
            runCatching { runProcess("hdiutil", "detach", mountPoint.absolutePath, "-force") }
        }
        val fixedDmg = File(dmgFile.parentFile, "${dmgFile.nameWithoutExtension}-fixed-tmp.dmg")
        fixedDmg.delete()
        runProcess("hdiutil", "convert", rwDmg.absolutePath, "-format", "UDZO", "-o", fixedDmg.absolutePath)
        if (fixedDmg.exists() && fixedDmg.length() > 0) {
            dmgFile.delete()
            fixedDmg.renameTo(dmgFile)
        }
    } finally {
        rwDmg.delete()
        mountPoint.deleteRecursively()
    }
}

tasks.matching { it.name == "packageDmg" || it.name == "packageReleaseDmg" }.configureEach {
    finalizedBy(fixDmgVolumeIcon)
}

sqldelight {
    databases {
        create("StadeDb") {
            packageName.set("dev.stade.db")
            deriveSchemaFromMigrations.set(false)
        }
    }
}

val torBundleVersion: String = providers.gradleProperty("tor.bundle.version").getOrElse("13.5.6")
val torDistBase = "https://archive.torproject.org/tor-package-archive/torbrowser"

data class TorPlatform(val key: String, val triple: String, val shaProp: String)

val torPlatforms = listOf(
    TorPlatform("windows-x86_64", "windows-x86_64", "tor.sha256.windows.x86_64"),
    TorPlatform("linux-x86_64", "linux-x86_64", "tor.sha256.linux.x86_64"),
    TorPlatform("macos-x86_64", "macos-x86_64", "tor.sha256.macos.x86_64"),
    TorPlatform("macos-aarch64", "macos-aarch64", "tor.sha256.macos.aarch64")
)

val torBinariesRoot = layout.buildDirectory.dir("torBinaries/tor")

val downloadTorBinaries by tasks.registering {
    group = "tor"
    description = "Downloads and extracts the Tor Expert Bundle for each desktop platform."
    val outRoot = torBinariesRoot
    doNotTrackState("Tor binaries are large external downloads managed via version markers.")
    inputs.property("torVersion", torBundleVersion)
    outputs.upToDateWhen {
        val rootDir = outRoot.get().asFile
        torPlatforms.all { plat ->
            rootDir.resolve("${plat.key}/.ok-$torBundleVersion").exists()
        }
    }
    doLast {
        val rootDir = outRoot.get().asFile
        rootDir.mkdirs()
        torPlatforms.forEach { plat ->
            val targetDir = rootDir.resolve(plat.key)
            val marker = targetDir.resolve(".ok-$torBundleVersion")
            if (marker.exists()) return@forEach
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            val fname = "tor-expert-bundle-${plat.triple}-$torBundleVersion.tar.gz"
            val urlStr = "$torDistBase/$torBundleVersion/$fname"
            logger.lifecycle("Downloading $urlStr")
            val tmp = File.createTempFile("tor-bundle-${plat.key}-", ".tar.gz")
            try {
                URI(urlStr).toURL().openStream().use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                val actualSha = MessageDigest.getInstance("SHA-256")
                    .digest(tmp.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                val expected = providers.gradleProperty(plat.shaProp).orNull?.trim()?.takeIf { it.isNotEmpty() }
                if (expected == null) {
                    logger.warn("[tor] ${plat.key} SHA-256 NOT pinned. Actual=$actualSha  -> set ${plat.shaProp} in gradle.properties")
                } else if (!expected.equals(actualSha, ignoreCase = true)) {
                    throw GradleException("Tor binary ${plat.key} hash mismatch. expected=$expected actual=$actualSha")
                }
                copy {
                    from(tarTree(resources.gzip(tmp)))
                    into(targetDir)
                }
                marker.writeText(actualSha)
            } finally {
                tmp.delete()
            }
        }
    }
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "jvmProcessResources" }.configureEach {
    dependsOn(downloadTorBinaries)
}

data class AndroidTorAbi(val bundleTriple: String, val abi: String, val shaProp: String)

val androidTorAbis = listOf(
    AndroidTorAbi("android-aarch64", "arm64-v8a",   "tor.sha256.android.aarch64"),
    AndroidTorAbi("android-armv7",   "armeabi-v7a", "tor.sha256.android.armv7"),
    AndroidTorAbi("android-x86_64",  "x86_64",      "tor.sha256.android.x86_64"),
    AndroidTorAbi("android-x86",     "x86",         "tor.sha256.android.x86")
)

val androidTorRoot = layout.buildDirectory.dir("torAndroid")

val downloadAndroidTorBinaries by tasks.registering {
    group = "tor"
    description = "Downloads Tor Expert Bundle for Android ABIs and stages jniLibs + assets."
    val outRoot = androidTorRoot
    doNotTrackState("Tor binaries are large external downloads managed via version markers; Gradle must not delete them on Windows while the emulator holds file locks.")
    inputs.property("torVersion", torBundleVersion)
    outputs.upToDateWhen {
        val rootDir = outRoot.get().asFile
        val assetsOk = rootDir.resolve("assets/tor/geoip").exists() && rootDir.resolve("assets/tor/geoip6").exists()
        assetsOk && androidTorAbis.all { abi ->
            rootDir.resolve("jniLibs/${abi.abi}/libtor.so").exists() &&
            rootDir.resolve("jniLibs/${abi.abi}/liblyrebird.so").exists() &&
            rootDir.resolve("jniLibs/${abi.abi}/.ok-$torBundleVersion").exists()
        }
    }
    doLast {
        val rootDir = outRoot.get().asFile
        val jniLibsDir = rootDir.resolve("jniLibs")
        val assetsDir = rootDir.resolve("assets/tor")
        jniLibsDir.mkdirs()
        assetsDir.mkdirs()
        androidTorAbis.forEach { abi ->
            val jniDir = jniLibsDir.resolve(abi.abi)
            val marker = jniDir.resolve(".ok-$torBundleVersion")
            if (marker.exists() && jniDir.resolve("libtor.so").exists() && jniDir.resolve("liblyrebird.so").exists()) return@forEach
            jniDir.deleteRecursively()
            jniDir.mkdirs()
            val fname = "tor-expert-bundle-${abi.bundleTriple}-$torBundleVersion.tar.gz"
            val urlStr = "$torDistBase/$torBundleVersion/$fname"
            logger.lifecycle("Downloading $urlStr")
            val tmp = File.createTempFile("tor-android-${abi.abi}-", ".tar.gz")
            try {
                URI(urlStr).toURL().openStream().use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                val actualSha = MessageDigest.getInstance("SHA-256")
                    .digest(tmp.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                val expected = providers.gradleProperty(abi.shaProp).orNull?.trim()?.takeIf { it.isNotEmpty() }
                if (expected == null) {
                    logger.warn("[tor-android] ${abi.abi} SHA-256 NOT pinned. Actual=$actualSha  -> set ${abi.shaProp} in gradle.properties")
                } else if (!expected.equals(actualSha, ignoreCase = true)) {
                    throw GradleException("Android Tor binary ${abi.abi} hash mismatch. expected=$expected actual=$actualSha")
                }
                val extractDir = File.createTempFile("tor-android-${abi.abi}-extract", "").apply {
                    delete(); mkdirs()
                }
                try {
                    copy {
                        from(tarTree(resources.gzip(tmp)))
                        into(extractDir)
                    }
                    val torBin = sequenceOf(
                        extractDir.resolve("tor/tor"),
                        extractDir.resolve("tor/libtor.so"),
                        extractDir.resolve("tor/libTor.so"),
                        extractDir.resolve("tor")
                    ).firstOrNull { it.isFile }
                        ?: throw GradleException("tor binary not found in bundle for ${abi.abi}")
                    torBin.copyTo(jniDir.resolve("libtor.so"), overwrite = true)
                    val lyrebirdBin = sequenceOf(
                        extractDir.resolve("tor/pluggable_transports/lyrebird"),
                        extractDir.resolve("pluggable_transports/lyrebird")
                    ).firstOrNull { it.isFile }
                    if (lyrebirdBin != null) {
                        lyrebirdBin.copyTo(jniDir.resolve("liblyrebird.so"), overwrite = true)
                    } else {
                        logger.warn("[tor-android] lyrebird (obfs4) binary not found in bundle for ${abi.abi} — bridge support will be unavailable")
                    }
                    listOf("geoip", "geoip6").forEach { gn ->
                        val candidate = sequenceOf(
                            extractDir.resolve("data/$gn"),
                            extractDir.resolve("tor/$gn"),
                            extractDir.resolve(gn)
                        ).firstOrNull { it.isFile }
                        if (candidate != null) candidate.copyTo(assetsDir.resolve(gn), overwrite = true)
                    }
                } finally {
                    extractDir.deleteRecursively()
                }
                marker.writeText(actualSha)
            } finally {
                tmp.delete()
            }
        }
    }
}

tasks.matching {
    val n = it.name
    n.startsWith("merge") && (n.endsWith("JniLibFolders") || n.endsWith("Assets") || n.endsWith("Resources")) ||
        n == "preBuild" || n == "generateDebugAssets" || n == "generateReleaseAssets"
}.configureEach {
    dependsOn(downloadAndroidTorBinaries)
}

