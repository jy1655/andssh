import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

fun runCommand(vararg args: String): String? =
    try {
        val process =
            ProcessBuilder(*args)
                .directory(rootProject.projectDir)
                .redirectErrorStream(true)
                .start()
        process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

// Git tag 기반 버전 자동 추출 (v0.2.2 형식)
val gitVersionName: String? =
    runCommand("git", "describe", "--tags", "--abbrev=0")
        ?.removePrefix("v")
        ?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+""")) }

// versionCode 계산: major*10000 + minor*100 + patch (예: 0.2.2 → 202)
val gitVersionCode: Int? =
    gitVersionName?.split(".")?.let { parts ->
        if (parts.size == 3) {
            parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
        } else {
            null
        }
    }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("com.github.triplet.play")
}

val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

fun releaseConfig(
    key: String,
    env: String,
): String? {
    val fromProperties = keystoreProperties.getProperty(key)
    val fromEnv = System.getenv(env)
    return (fromProperties ?: fromEnv)?.takeIf { it.isNotBlank() }
}

val releaseStoreFile = releaseConfig("storeFile", "ANDROID_UPLOAD_STORE_FILE")
val releaseStorePassword = releaseConfig("storePassword", "ANDROID_UPLOAD_STORE_PASSWORD")
val releaseKeyAlias = releaseConfig("keyAlias", "ANDROID_UPLOAD_KEY_ALIAS")
val releaseKeyPassword = releaseConfig("keyPassword", "ANDROID_UPLOAD_KEY_PASSWORD")
val ciVersionCode =
    providers
        .gradleProperty("andssh.ciVersionCode")
        .orElse(providers.environmentVariable("ANDROID_VERSION_CODE"))
        .orNull
        ?.toIntOrNull()
val ciVersionName =
    providers
        .gradleProperty("andssh.ciVersionName")
        .orElse(providers.environmentVariable("ANDROID_VERSION_NAME"))
        .orNull
        ?.takeIf { it.isNotBlank() }
val playTrack =
    providers
        .gradleProperty("andssh.playTrack")
        .orElse(providers.environmentVariable("PLAY_TRACK"))
        .orElse("internal")
        .get()
val playReleaseStatusName =
    providers
        .gradleProperty("andssh.playReleaseStatus")
        .orElse(providers.environmentVariable("PLAY_RELEASE_STATUS"))
        .orElse("COMPLETED")
        .get()
        .uppercase()
val playReleaseStatus =
    runCatching {
        ReleaseStatus.valueOf(playReleaseStatusName)
    }.getOrElse {
        error(
            "Invalid play release status: $playReleaseStatusName. " +
                "Use one of ${ReleaseStatus.values().joinToString()}.",
        )
    }
val playCredentialsPath =
    providers
        .gradleProperty("andssh.playServiceAccountCredentials")
        .orElse(providers.environmentVariable("ANDROID_PUBLISHER_CREDENTIALS"))
        .orNull
        ?.takeIf { it.isNotBlank() }
val hasReleaseSigningConfig =
    listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { it != null }

android {
    namespace = "com.opencode.sshterminal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.opencode.sshterminal"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode ?: gitVersionCode ?: 5
        versionName = ciVersionName ?: gitVersionName ?: "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                // Include stripped native symbols in AAB/APK for Play Console native crash deobfuscation.
                debugSymbolLevel = "FULL"
            }
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

play {
    track.set(playTrack)
    releaseStatus.set(playReleaseStatus)
    defaultToAppBundles.set(true)
    if (playCredentialsPath != null) {
        serviceAccountCredentials.set(file(playCredentialsPath))
    }
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        md.required.set(true)
        sarif.required.set(false)
        txt.required.set(false)
    }
}

tasks.matching { it.name in setOf("bundleRelease", "assembleRelease", "packageRelease") }.configureEach {
    doFirst {
        check(hasReleaseSigningConfig) {
            "Missing release signing config. Set keystore.properties or ANDROID_UPLOAD_* env vars."
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")

    // DocumentFile (SAF helper)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Security
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.biometric:biometric:1.1.0")

    // SSH
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    // Termux terminal emulator
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.1")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.1")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
