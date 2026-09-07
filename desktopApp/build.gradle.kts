import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.kotlinCompose)
}

kotlin {
  jvm("desktop") {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_21)
    }
  }

  sourceSets {
    val desktopMain by getting
    desktopMain.dependencies {
      implementation(project(":shared"))
      implementation(compose.desktop.currentOs)
    }
  }
}

compose.desktop {
  application {
    mainClass = "alphainterplanetary.thinker.MainKt"
  }
}