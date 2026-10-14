plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "fr.igntrail.data.storage"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    // Schémas versionnés : nécessaires pour écrire des migrations sans perte de données.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:tracking"))
    api(project(":core:model"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
