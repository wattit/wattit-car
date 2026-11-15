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
        // Pleos Connect SDK (Vehicle, TextToSpeech). Public, no login.
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}

rootProject.name = "wattit-car"
include(":app")
