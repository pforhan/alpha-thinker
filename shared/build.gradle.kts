import com.android.build.api.dsl.LibraryExtension

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.kotlinCompose)
}

kotlin {
  androidTarget {
    compilations.all {
      compileTaskProvider {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
      }
    }
  }

  // jvm("desktop") {
  //   compilerOptions {
  //     jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  //   }
  // }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.datetime)
      implementation(libs.room.runtime)
      implementation(libs.room.ktx)
      implementation(libs.sqlite.bundled)
      implementation(libs.composeMultiplatform.runtime)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    androidMain.dependencies {
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.appcompat)
    }
  }
}
configure<LibraryExtension> {
  namespace = "alphainterplanetary.thinker.shared"
  compileSdk = libs.versions.compileSdk.get().toInt()
  defaultConfig {
    minSdk = 26
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

dependencies {
  compileOnly(libs.flutter.embedding)
}
