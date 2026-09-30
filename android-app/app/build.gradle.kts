plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.securemessenger"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.securemessenger"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Debug installs only need the ABI of the test phones. libsignal ships four ~90 MB
            // native libraries; the release build keeps all four (owner decision, 2026-09-29).
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Step 1f: minified exactly like release (same R8 rules, not debuggable) but debug-signed,
        // with the debug QA resources, so libsignal can be exercised under R8 on test phones.
        // Installs side by side (".qa"); never distributed.
        create("qaMinified") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            matchingFallbacks += listOf("debug")
        }
    }
    // The QA build needs the debug-only cleartext policy for the LAN / adb-reverse test relay.
    sourceSets.getByName("qaMinified").res.srcDir("src/debug/res")
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by libsignal-android's AAR metadata (see :core:signal-protocol).
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        // libsignal_jni_testing.so (~100 MB per ABI) is for Signal's own tests and must never ship.
        jniLibs.excludes += "**/libsignal_jni_testing.so"
        // libsignal-client's jar also carries desktop natives (Windows/macOS, including *_testing)
        // as Java resources: ~150 MB of dead weight on Android.
        resources.excludes += listOf("*.dll", "**/*.dll", "*.dylib", "**/*.dylib")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:ui"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
