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
include(":core:storage")
include(":core:transport")
include(":core:ui")
include(":core:signal-protocol")
include(":core:signalstore")
