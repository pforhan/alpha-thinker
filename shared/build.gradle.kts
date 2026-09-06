import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.ksp)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
  android {
    namespace = "alphainterplanetary.thinker.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = 26

    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_21)
      freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    androidResources {
      enable = true
    }

    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  js(IR) {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.material.icons.extended)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlin.inject)
    }

    androidMain.dependencies {
      implementation(libs.room.runtime)
      implementation(libs.room.ktx)
      implementation(libs.sqlite.bundled)
      implementation(libs.androidx.navigation.compose)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

dependencies {
  add("kspAndroid", libs.kotlin.inject.compiler)
  add("kspAndroid", libs.room.compiler)
  add("kspJs", libs.kotlin.inject.compiler)
}
