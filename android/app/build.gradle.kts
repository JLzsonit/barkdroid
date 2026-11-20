import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional release signing: if android/signing.gradle exists, apply it.
if (rootProject.file("signing.gradle").exists()) {
    apply(from = rootProject.file("signing.gradle"))
}

android {
    namespace = "com.example.barkdroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.barkdroid"
        minSdk = 26
        // 34 而不是 35：Android 15 对 targetSdk 35 应用的 dataSync 前台服务有 6 小时/天限制，
        // 而本 App 需要 7×24 常驻接收短信，保持 34 可避免该限制。
        targetSdk = 34
        versionCode = 13
        versionName = "2.4.0"
    }

    // Read config from config.properties (user-provided)
    val configFile = rootProject.file("config.properties")
    val props = Properties().also { p ->
        if (configFile.exists()) configFile.inputStream().use { p.load(it) }
    }

    defaultConfig {
        // 允许在 config.properties 里覆盖包名与默认 ntfy 服务器
        props.getProperty("APPLICATION_ID")?.takeIf { it.isNotBlank() }?.let { applicationId = it }
        buildConfigField(
            "String",
            "DEFAULT_NTFY_SERVER",
            "\"${props.getProperty("NTFY_SERVER", "https://ntfy.sh").trim()}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")

    // 通知 / 前台服务 / ContextCompat
    implementation("androidx.core:core-ktx:1.13.1")

    // 协程（长连接读取 + 测试推送）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 主题（Manifest 引用的 Theme.Material3.* 来自该库）
    implementation("com.google.android.material:material:1.12.0")

    // Network（SSE 长连接）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
}
