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
android {
    namespace = "com.pforhan.alphathinker.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://storage.googleapis.com/download.flutter.io")
        }
    }

    dependencies {
        compileOnly(libs.flutter.embedding)
    }
}
