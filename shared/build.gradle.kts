import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

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
    nodejs()
    browser {
      testTask {
        val chromeBin = System.getenv("CHROME_EXECUTABLE") ?: System.getenv("CHROME_BIN")
        if (chromeBin != null) {
          environment("CHROME_BIN", chromeBin)
        } else {
          enabled = false
        }
      }
    }
    compilerOptions {
      freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
    binaries.executable()
  }

  wasmJs {
    nodejs()
    browser {
      testTask {
        val chromeBin = System.getenv("CHROME_EXECUTABLE") ?: System.getenv("CHROME_BIN")
        if (chromeBin != null) {
          environment("CHROME_BIN", chromeBin)
        } else {
          enabled = false
        }
      }
    }
    compilerOptions {
      freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
    binaries.executable()
  }

  jvm("desktop") {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_21)
      freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
  }

  sourceSets {
    all {
      languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.material.icons.extended)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlin.inject)
      implementation(libs.room.runtime)
    }

    androidMain.dependencies {
      implementation(libs.sqlite.bundled)
      implementation(libs.androidx.navigation.compose)
    }

    jsMain.dependencies {
      implementation(libs.sqlite.web)
      implementation(npm("sqlite-wasm-worker", layout.projectDirectory.dir("webWorker/worker").asFile))
      implementation(npm("@sqlite.org/sqlite-wasm", "3.50.4-build1"))
    }

    wasmJsMain.dependencies {
      implementation(libs.sqlite.web)
      implementation(libs.kotlinx.browser)
      implementation(npm("sqlite-wasm-worker", layout.projectDirectory.dir("webWorker/worker").asFile))
      implementation(npm("@sqlite.org/sqlite-wasm", "3.50.4-build1"))
    }

    val desktopMain by getting {
      dependencies {
        implementation(libs.sqlite.bundled)
      }
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
  add("kspJs", libs.room.compiler)
  add("kspWasmJs", libs.kotlin.inject.compiler)
  add("kspWasmJs", libs.room.compiler)
  add("kspDesktop", libs.kotlin.inject.compiler)
  add("kspDesktop", libs.room.compiler)
}

tasks.named<KotlinJsTest>("wasmJsNodeTest") {
  enabled = false
}
