plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.securemessenger.signalprotocol"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Applied to any app that depends on this module (R8 keep rules; step 1c, unverified for release).
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // libsignal-android's AAR metadata requires desugaring even at minSdk 31; the build fails
        // without it. Any app module that depends on this module must enable it as well.
        isCoreLibraryDesugaringEnabled = true
    }
    sourceSets {
        // Run the same libsignal tests on a device so the Android JNI library (libsignal_jni.so)
        // is exercised, not only the desktop native library used by the JVM unit tests.
        getByName("androidTest").java.srcDir("src/test/java")
    }
    packaging {
        // libsignal-android also ships libsignal_jni_testing.so (~100 MB per ABI) for Signal's own
        // tests. It must never be packaged. Any app module that depends on this module needs the
        // same exclusion, since packaging rules apply to the final APK.
        jniLibs.excludes += "**/libsignal_jni_testing.so"
        // Desktop natives are Java resources of libsignal-client: needed by JVM unit tests (not
        // affected by this), useless in a device test APK.
        resources.excludes += listOf("*.dll", "**/*.dll", "*.dylib", "**/*.dylib")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:signalstore"))
    // TransportClient / RelayEncryptedBlob for the v2 conversation pipeline.
    api(project(":core:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.libsignal.client)
    implementation(libs.libsignal.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
