plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.securemessenger.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // The instrumented-test APK contains libsignal-android, whose AAR metadata requires it.
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        jniLibs.excludes += "**/libsignal_jni_testing.so"
        resources.excludes += listOf("*.dll", "**/*.dll", "*.dylib", "**/*.dylib")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:storage"))
    implementation(project(":core:transport"))
    implementation(project(":core:signalstore"))
    // The v2 protocol pipeline: the only chat path, debug builds only until Phase 5 (DEC-021).
    implementation(project(":core:signal-protocol"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.zxing.embedded)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Step 1f proofs build SignalSessions over Room-backed stores directly.
    androidTestImplementation(libs.libsignal.client)
    androidTestImplementation(libs.libsignal.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)
}
