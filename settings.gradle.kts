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

include(":shared")
project(":shared").projectDir = file("shared")
