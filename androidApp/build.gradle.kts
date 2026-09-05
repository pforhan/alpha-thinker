import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.kotlinCompose)
}

kotlin {
  target {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_21)
    }
  }

  dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
  }
}

android {
  namespace = "alphainterplanetary.thinker"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "alphainterplanetary.thinker"
    minSdk = 26
    targetSdk = libs.versions.compileSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}