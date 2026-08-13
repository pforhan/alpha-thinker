plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
   androidTarget {
     compilerOptions {
       jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
     }
   }

  // jvm("desktop")

  sourceSets {
     commonMain.dependencies {
       implementation(compose.runtime)
       implementation(compose.foundation)
       implementation(compose.material3)
       implementation(compose.ui)
       implementation(compose.components.resources)
       implementation(libs.kotlinx.serialization.json)
       implementation(libs.kotlinx.coroutines.core)
       implementation(libs.kotlinx.datetime)
       implementation(libs.room.runtime)
       implementation(libs.room.ktx)
       implementation(libs.sqlite.bundled)
       implementation(libs.composeMultiplatform.runtime)
       implementation(libs.material.icons.core)
       implementation(libs.material.icons.extended)
     }

    androidMain.dependencies {
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.lifecycle.runtime.ktx)
      implementation(libs.androidx.navigation.compose)
    }

    // val desktopMain by getting {
    //     dependencies {
    //         implementation(compose.desktop.currentOs)
    //     }
    // }
  }
}

android {
  namespace = "com.pforhan.alphathinker"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.pforhan.alphathinker"
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
