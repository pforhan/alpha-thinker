pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
          url = uri("https://storage.googleapis.com/download.flutter.io")
        }
    }
}

rootProject.name = "alphathinker"

include(":shared")
include(":composeApp")
