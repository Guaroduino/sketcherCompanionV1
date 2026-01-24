plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.skecher.sketchercompanionv1"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.skecher.sketchercompanionv1"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // --- LIBRERÍAS DE ANDROID INK 1.0.0 ---
    val ink_version = "1.0.0"
    implementation("androidx.ink:ink-authoring:$ink_version")
    implementation("androidx.ink:ink-strokes:$ink_version")
    implementation("androidx.ink:ink-geometry:$ink_version")
    implementation("androidx.ink:ink-brush:$ink_version")
    implementation("androidx.ink:ink-rendering:$ink_version")
    implementation("androidx.ink:ink-storage:$ink_version")

    // CRÍTICO: Carga de código nativo (C++)
    implementation("androidx.ink:ink-nativeloader:$ink_version")

    // --- LIBRERÍA DE PREDICCIÓN (INPUT) ---
    implementation("androidx.input:input-motionprediction:1.0.0-beta05")
}