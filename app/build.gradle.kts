plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.hiramrg.delphis"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.hiramrg.delphis"
        minSdk = 29          // Android 10: multi-display launching + per-display focus
        targetSdk = 36       // Android 16. The Thor (Android 13) is unaffected: a target's rules apply only on that version and up
        // CI passes -PversionCode=<run number>, so every published build is newer than the last;
        // the in-app updater compares this number with the one in the GitHub release notes.
        val code = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionCode = code
        // CI passes -Pchannel=beta for the beta branch (Settings → Updates → Update channel).
        val channel = (project.findProperty("channel") as String?)?.takeIf { it == "beta" } ?: "stable"
        versionName = if (channel == "beta") "1.0.$code-beta" else "1.0.$code"
        resValue("string", "build_channel", channel)
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
    // The layout check (src/test/…/LayoutCheckTest.kt) draws every screen with Robolectric: it needs the app's
    // resources and real drawing (native graphics) for its screenshots.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.testLogging { showStandardStreams = true }
            }
        }
    }
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
    // Layout check: Delphis' screens drawn off-device.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    // The real org.json for unit tests (Android's is a stub off-device).
    testImplementation("org.json:json:20240303")
}
