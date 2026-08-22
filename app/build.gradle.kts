plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.rdndds.forcefastboot"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.rdndds.forcefastboot"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // CI: use environment variables passed from GitHub Actions secrets
            val ciKeystorePath = System.getenv("CI_KEYSTORE_FILE")
            if (ciKeystorePath != null) {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("CI_KEYSTORE_PASSWORD") ?: error("CI_KEYSTORE_PASSWORD not set")
                keyAlias = System.getenv("CI_KEY_ALIAS") ?: error("CI_KEY_ALIAS not set")
                keyPassword = System.getenv("CI_KEY_PASSWORD") ?: error("CI_KEY_PASSWORD not set")
            } else {
                // Local convenience only: reuse the debug keystore when present so
                // `assembleRelease` produces an installable APK. Fresh clones without
                // one get an unsigned APK instead of a failed build.
                val keystoreFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "**/kotlin/**"
            excludes += "**/*.version"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner, mocking
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // USB Serial
    implementation("com.github.mik3y:usb-serial-for-android:3.11.0")

    // Startup Baseline Profile generation (androidx.baselineprofile 1.5.0-rc01)
    baselineProfile(project(":baselineprofile"))
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_function-naming" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}
