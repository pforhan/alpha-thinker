import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

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

  val xcf = XCFramework("Shared")
  listOf(
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "Shared"
      isStatic = true
      binaryOption("bundleId", "alphainterplanetary.thinker.Shared")
      xcf.add(this)
    }
    iosTarget.compilerOptions {
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
      implementation(libs.koog.agents)
    }

    androidMain.dependencies {
      implementation(libs.sqlite.bundled)
      // For BackHandler only: Android's back event is the one platform back
      // affordance, and it used to arrive for free with Jetpack Navigation.
      implementation(libs.androidx.activity.compose)
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

    val iosMain by creating {
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
  add("kspWasmJs", libs.kotlin.inject.compiler)
  add("kspWasmJs", libs.room.compiler)
  add("kspDesktop", libs.kotlin.inject.compiler)
  add("kspDesktop", libs.room.compiler)
  add("kspIosArm64", libs.kotlin.inject.compiler)
  add("kspIosArm64", libs.room.compiler)
  add("kspIosSimulatorArm64", libs.kotlin.inject.compiler)
  add("kspIosSimulatorArm64", libs.room.compiler)
}

// The wasm tests are browser-only: skiko's wasm glue cannot load under Node.
// In skiko.mjs (skiko-js-wasm-runtime 0.9.37.4) the whole Node loader is
// compiled out — `var read_, readAsync, readBinary; if (false) { const
// {createRequire} = await import("module"); ... }` — so skiko.wasm is only ever
// fetched over http(s) and Node aborts with "failed to asynchronously prepare
// wasm" before the first test runs, no matter how skiko.mjs/skiko.wasm are
// staged next to the executable. `wasmJsBrowserTest` covers the same tests.
tasks.named("wasmJsNodeTest") {
  enabled = false
}
