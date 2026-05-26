pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SecureMessengerMvp"
include(":app")
include(":core:domain")
include(":core:crypto")
include(":core:storage")
include(":core:transport")
include(":core:ui")
