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
        maven { url = uri("https://packages.jetbrains.com/kotlinwmks-internal-release") }
    }
}

rootProject.name = "alphathinker"
include(":androidApp")
project(":androidApp").projectDir = file("androidApp")

include(":shared")
project(":shared").projectDir = file("shared")
