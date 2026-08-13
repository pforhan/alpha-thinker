plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.kotlinCompose) apply false
  alias(libs.plugins.kotlinAndroid) apply false
  alias(libs.plugins.ksp) apply false
}

tasks.register<Exec>("runWeb") {
  group = "application"
  description = "Runs the application on Web"
  workingDir = file("frontend")
  dependsOn(":shared:assemble")
  commandLine("flutter", "run", "-d", "chrome")
}

tasks.register<Exec>("runAndroid") {
  group = "application"
  description = "Runs the application on Android"
  workingDir = file("frontend")
  dependsOn(":shared:assemble")
  commandLine("sh", "-c", "DEVICE=$(flutter devices | awk -F' • ' '/android/{print $2; exit}') && exec flutter run -d \"\$DEVICE\"")
}

tasks.register<Exec>("runIos") {
  group = "application"
  description = "Runs the application on iOS"
  workingDir = file("frontend")
  dependsOn(":shared:assemble")
  commandLine("sh", "-c", "DEVICE=$(flutter devices | awk -F' • ' '/ios/{print $2; exit}') && exec flutter run -d \"\$DEVICE\"")
}

tasks.register<Exec>("runDesktop") {
  group = "application"
  description = "Runs the application on the current desktop platform"
  workingDir = file("frontend")
  dependsOn(":shared:assemble")

  val os = System.getProperty("os.name").lowercase()
  val device = when {
    os.contains("mac") -> "macos"
    os.contains("win") -> "windows"
    os.contains("nix") || os.contains("nux") -> "linux"
    else -> "macos"
  }
  commandLine("flutter", "run", "-d", device)
}

repositories {
  google()
  mavenCentral()
  maven {
    url = uri("https://storage.googleapis.com/download.flutter.io")
  }
}
