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
        maven {
            name = "SignalBuildArtifacts"
            url = uri("https://build-artifacts.signal.org/libraries/maven/")
            content { includeGroup("org.signal") }
        }
    }
}

rootProject.name = "SecureMessengerMvp"
include(":app")
include(":core:domain")
include(":core:crypto")
include(":core:storage")
include(":core:transport")
include(":core:delivery")
include(":core:ui")
include(":core:signal-protocol")
