plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/** Semantic base; CI appends +run. Bump patch/minor when releasing meaningful changes. */
val ncarouselBaseVersionName = "0.2.15"

/**
 * Monotonic [versionCode] is required to upgrade over an existing install without uninstalling.
 * - Local builds: [ncarouselLocalVersionCode] — **increment on every commit/push** that ships an APK.
 * - GitHub Actions: [GITHUB_RUN_NUMBER] → 1000 + run (each workflow run increases).
 * - Override: `-Pncarousel.versionCode=123` or env `NCAROUSEL_VERSION_CODE`.
 */
val ncarouselLocalVersionCode = 18

val ncarouselVersionCode: Int =
    (project.findProperty("ncarousel.versionCode") as String?)?.toIntOrNull()
        ?: System.getenv("NCAROUSEL_VERSION_CODE")?.toIntOrNull()
        ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 1_000 + it }
        ?: ncarouselLocalVersionCode

val ncarouselVersionName: String =
    System.getenv("GITHUB_RUN_NUMBER")?.let { "$ncarouselBaseVersionName+$it" } ?: ncarouselBaseVersionName

/**
 * Optional: same keystore on every CI run so debug APKs from GitHub Actions upgrade each other
 * without uninstall (see README). Set env in workflow: NCAROUSEL_SIGNING_STORE_FILE (absolute path),
 * NCAROUSEL_SIGNING_STORE_PASSWORD, NCAROUSEL_SIGNING_KEY_ALIAS, NCAROUSEL_SIGNING_KEY_PASSWORD.
 */
val ncarouselCiKeystoreFile: java.io.File? =
    System.getenv("NCAROUSEL_SIGNING_STORE_FILE")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { file(it) }
        ?.takeIf { it.isFile }

android {
    namespace = "dev.nemeyes.ncarousel"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.nemeyes.ncarousel"
        minSdk = 26
        targetSdk = 35
        versionCode = ncarouselVersionCode
        versionName = ncarouselVersionName
    }

    signingConfigs {
        val ciKs = ncarouselCiKeystoreFile
        if (ciKs != null) {
            create("ci") {
                storeFile = ciKs
                storePassword = System.getenv("NCAROUSEL_SIGNING_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("NCAROUSEL_SIGNING_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("NCAROUSEL_SIGNING_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            if (ncarouselCiKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (ncarouselCiKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
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
    }

    packaging {
        jniLibs {
            // Prebuilt androidx.graphics path .so: avoids "Unable to strip" from stripDebugDebugSymbols.
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Nextcloud Login Flow v2: open default browser / custom tabs
    implementation("androidx.browser:browser:1.8.0")

    // Offline-first metadata cache
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
