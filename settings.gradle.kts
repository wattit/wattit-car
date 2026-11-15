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
        // Pleos Connect SDK (Vehicle, TextToSpeech). Public, no login. Only ai.pleos.* groups are taken from
        // it, and those groups only from it, so a compromised mirror cannot supply any other library.
        exclusiveContent {
            forRepository {
                maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
            }
            filter {
                includeGroupByRegex("ai\\.pleos(\\..*)?")
            }
        }
    }
}

rootProject.name = "wattit-car"
include(":app")
