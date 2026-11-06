plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.hiramrg.delphis"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hiramrg.delphis"
        minSdk = 29          // Android 10: multi-display launching + per-display focus
        targetSdk = 34       // The AYN Thor ships Android 13
        // CI passes -PversionCode=<run number>, so every published build is newer than the last;
        // the in-app updater compares this number with the one in the GitHub release notes.
        val code = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionCode = code
        versionName = "1.0.$code"
    }

    // Release APKs are built unsigned; CI signs both APKs with apksigner (see .github/workflows/build.yml
    // and signing/README.md), using the encrypted key in signing/ and the SIGNING_PASSWORD secret.
    buildTypes {
        release {
            isMinifyEnabled = true
            // Drops unused resources (e.g. unused icons) for a smaller APK.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
