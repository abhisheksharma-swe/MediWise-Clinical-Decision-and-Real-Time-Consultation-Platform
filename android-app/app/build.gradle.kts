import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// ── Environment configuration ────────────────────────────────────────────
// Resolution order for every property below: Gradle project property (-Pname=...
// or CI-injected gradle.properties) > local.properties (untracked, developer machine
// only) > environment variable > debug-only hardcoded fallback.
//
// Required developer/CI setup for a RELEASE build (see local.properties, which is
// gitignored, or inject via env vars / -P flags in CI):
//   RELEASE_BASE_URL=https://your.api.host/
//   RELEASE_WS_URL=wss://your.api.host/ws
//   RELEASE_GOOGLE_WEB_CLIENT_ID=<Firebase OAuth web client id>
// Debug builds fall back to a LAN default if DEBUG_* properties are not set.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun resolveProperty(name: String): String? =
    (project.findProperty(name) as String?)
        ?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val placeholderBaseUrl = "https://api.clinicalsystem.com/"
val placeholderClientId = "REPLACE_WITH_FIREBASE_WEB_CLIENT_ID"

val debugBaseUrl = resolveProperty("DEBUG_BASE_URL") ?: "http://192.168.137.1:8080/"
val debugWsUrl = resolveProperty("DEBUG_WS_URL") ?: "ws://192.168.137.1:8080/ws"
val debugGoogleWebClientId = resolveProperty("DEBUG_GOOGLE_WEB_CLIENT_ID") ?: placeholderClientId

val releaseBaseUrl = resolveProperty("RELEASE_BASE_URL")
val releaseWsUrl = resolveProperty("RELEASE_WS_URL")
val releaseGoogleWebClientId = resolveProperty("RELEASE_GOOGLE_WEB_CLIENT_ID")

fun validateReleaseConfig() {
    val problems = mutableListOf<String>()
    if (releaseBaseUrl.isNullOrBlank() || releaseBaseUrl == placeholderBaseUrl) {
        problems += "RELEASE_BASE_URL is missing or still the placeholder ($placeholderBaseUrl). Set it via -PRELEASE_BASE_URL=, local.properties, or an env var."
    } else if (!releaseBaseUrl.startsWith("https://")) {
        problems += "RELEASE_BASE_URL must use https://, got: $releaseBaseUrl"
    }
    if (releaseWsUrl.isNullOrBlank()) {
        problems += "RELEASE_WS_URL is missing. Set it via -PRELEASE_WS_URL=, local.properties, or an env var."
    } else if (!releaseWsUrl.startsWith("wss://")) {
        problems += "RELEASE_WS_URL must use wss://, got: $releaseWsUrl"
    }
    if (releaseGoogleWebClientId.isNullOrBlank() || releaseGoogleWebClientId == placeholderClientId) {
        problems += "RELEASE_GOOGLE_WEB_CLIENT_ID is missing or still the placeholder. Set it via -PRELEASE_GOOGLE_WEB_CLIENT_ID=, local.properties, or an env var."
    }
    if (problems.isNotEmpty()) {
        throw GradleException(
            "Release build configuration is invalid:\n" + problems.joinToString("\n") { "  - $it" }
        )
    }
}

android {
    namespace = "com.mediwise"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.mediwise"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
            buildConfigField("String", "WS_URL", "\"$debugWsUrl\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$debugGoogleWebClientId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Fall back to placeholders so configuration never fails for non-release tasks
            // (e.g. `compileDebugKotlin`); validateReleaseConfig() below fails the build the
            // moment a release task actually runs with incomplete/placeholder configuration.
            buildConfigField("String", "BASE_URL", "\"${releaseBaseUrl ?: placeholderBaseUrl}\"")
            buildConfigField("String", "WS_URL", "\"${releaseWsUrl ?: "wss://REPLACE_ME/ws"}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${releaseGoogleWebClientId ?: placeholderClientId}\"")
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
}

// Validate release configuration lazily, right before a release task executes, so debug
// builds (and plain `./gradlew tasks`) are never blocked by missing release secrets.
tasks.whenTaskAdded {
    if (name.contains("Release")) {
        doFirst { validateReleaseConfig() }
    }
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        val taskName = name
        if (taskName.startsWith("compile") && taskName.endsWith("JavaWithJavac")) {
            val variant = taskName.removePrefix("compile").removeSuffix("JavaWithJavac").replaceFirstChar { it.lowercase() }
            val kotlinClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/$variant")
            val currentClasspath = classpath
            classpath = if (currentClasspath != null) currentClasspath + files(kotlinClassesDir) else files(kotlinClassesDir)
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.play.services.auth)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Image Loading
    implementation(libs.coil.compose)

    // Razorpay
    implementation(libs.razorpay)

    // Biometric
    implementation(libs.androidx.biometric)

    // WorkManager + Hilt Worker support
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WebRTC (audio/video calling)
    implementation(libs.stream.webrtc.android)
}
