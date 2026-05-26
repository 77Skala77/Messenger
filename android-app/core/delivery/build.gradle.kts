plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:crypto"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
}
