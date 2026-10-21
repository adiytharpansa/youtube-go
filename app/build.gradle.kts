plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.app.youtubego"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.app.youtubego"
        minSdk = 24
        targetSdk = 34
        versionCode = 55
        versionName = "1.54.0"
    }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
        debug { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    // Networking: OkHttp (downloader NewPipeExtractor dan unduhan) + Kotlinx Serialization (penyimpanan lokal)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-session:1.5.0")
    // Sumber data HTTP berbasis OkHttp: HTTP/2, koneksi dipakai ulang (video + audio satu host), handshake TLS lebih jarang.
    implementation("androidx.media3:media3-datasource-okhttp:1.5.0")
    // Coil (cache hemat data)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // NewPipeExtractor: ekstraksi stream YouTube DI PERANGKAT (IP pengguna), jalur putar utama spike.
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
