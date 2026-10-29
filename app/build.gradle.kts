import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Optional online keys. Never committed: provide them in the untracked
 * `android/local.properties` or in the environment.
 *
 *   GOOGLE_DIRECTIONS_API_KEY=...   traffic-aware route (otherwise OSRM)
 *   GROQ_API_KEY=...                cloud vision for vehicle details
 *   GROQ_VISION_MODEL=...           defaults to qwen/qwen3.8-27b
 *
 * Empty values keep those features off and the app falls back to the offline
 * estimate / on-device metrics.
 */
val directionsApiKey: String = run {
    val props = Properties()
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { props.load(it) }
    (props.getProperty("GOOGLE_DIRECTIONS_API_KEY")
        ?: System.getenv("GOOGLE_DIRECTIONS_API_KEY")
        ?: "")
        .trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

val groqApiKey: String = run {
    val props = Properties()
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { props.load(it) }
    (props.getProperty("GROQ_API_KEY")
        ?: System.getenv("GROQ_API_KEY")
        ?: "")
        .trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

val groqVisionModel: String = run {
    val props = Properties()
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { props.load(it) }
    (props.getProperty("GROQ_VISION_MODEL")
        ?: System.getenv("GROQ_VISION_MODEL")
        ?: "qwen/qwen3.8-27b")
        .trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

android {
    namespace = "com.roadsos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roadsos.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DIRECTIONS_API_KEY", "\"$directionsApiKey\"")
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
        buildConfigField("String", "GROQ_VISION_MODEL", "\"$groqVisionModel\"")
    }

    buildTypes {
        release {
            // R8 also strips the unused Material icon glyphs, which is most of the APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Demo builds are signed with the local debug key so the release APK installs
            // on a phone without any keystore setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
