plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
  alias(libs.plugins.androidMultiplatformLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.kotlinCompose) apply false
  alias(libs.plugins.ksp) apply false
}

repositories {
  google()
  mavenCentral()
}
