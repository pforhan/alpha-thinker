plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.kotlinCompose)
}

kotlin {
  js(IR) {
    browser()
    binaries.executable()
    }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":shared"))
       }

    jsMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.html)
       }
      }
}
