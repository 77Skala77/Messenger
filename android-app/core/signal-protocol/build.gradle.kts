plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.securemessenger.signalprotocol"
    compileSdk = 34
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.libsignal.client)
    implementation(libs.libsignal.android)
    testImplementation(libs.junit)
}
