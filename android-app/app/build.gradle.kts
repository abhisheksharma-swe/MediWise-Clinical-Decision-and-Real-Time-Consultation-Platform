plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
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

        buildConfigField("String", "BASE_URL", "\"http://10.142.33.102:8080/\"")
        buildConfigField("String", "WS_URL", "\"ws://10.142.33.102:8080/ws\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"REPLACE_WITH_FIREBASE_WEB_CLIENT_ID\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL", "\"https://api.clinicalsystem.com/\"")
            buildConfigField("String", "WS_URL", "\"wss://api.clinicalsystem.com/ws\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"REPLACE_WITH_FIREBASE_WEB_CLIENT_ID\"")
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
}
