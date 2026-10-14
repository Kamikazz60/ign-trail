plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "fr.igntrail"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.igntrail"
        minSdk = 29
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Usage personnel : signée avec la clé de debug locale (Phase 7 : clé dédiée).
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:geo"))
    implementation(project(":data:location"))
    implementation(project(":data:settings"))
    implementation(project(":core:tracking"))
    implementation(project(":core:gpx"))
    implementation(project(":core:navigation"))
    implementation(project(":core:planning"))
    implementation(project(":core:stats"))
    implementation(project(":data:storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.maplibre.android)
    implementation(libs.okhttp)
    // Affichage des photos locales (pas de module réseau : photos uniquement sur l'appareil).
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
