plugins {
  // Same artifact family as com.android.application; version pinned by root
  // build via libs.plugins.android.library apply-false, so requested bare.
  id("com.android.test")
  alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.rdndds.forcefastboot.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Profiles are generated against this project's app module.
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
