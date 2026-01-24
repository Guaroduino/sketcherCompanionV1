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
    dependencies {
        implementation("androidx.core:core-ktx:1.12.0") // (O la versión que tengas)
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
        implementation("androidx.activity:activity-compose:1.8.2")
        implementation(platform("androidx.compose:compose-bom:2023.08.00"))
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-graphics")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.material3:material3")

        // --- SOLO LIBRERÍAS DE INK ---
        implementation("androidx.ink:ink-authoring:1.0.0")
        implementation("androidx.ink:ink-authoring-compose:1.0.0")
        implementation("androidx.ink:ink-brush:1.0.0")
        implementation("androidx.ink:ink-brush-compose:1.0.0")
        implementation("androidx.ink:ink-geometry:1.0.0")
        implementation("androidx.ink:ink-geometry-compose:1.0.0")
        implementation("androidx.ink:ink-nativeloader:1.0.0")
        implementation("androidx.ink:ink-rendering:1.0.0")
        implementation("androidx.ink:ink-storage:1.0.0")
        implementation("androidx.ink:ink-strokes:1.0.0")

        implementation("androidx.input:input-motionprediction:1.0.0-beta04")
    }
}