plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.ksp) apply false
}

val pigeonDartOut = "frontend/lib/thinker_api.dart"
val pigeonKotlinOut = "shared/src/androidMain/kotlin/com/pforhan/alphathinker/ThinkerApi.kt"

tasks.register("clean") {
    group = "build"
    description = "Deletes build directories and generated Pigeon bindings"
    doLast {
        delete(file("build"))
        delete(file(pigeonDartOut))
        delete(file(pigeonKotlinOut))
    }
}

tasks.register<Exec>("generatePigeon") {
    group = "build"
    description = "Generates Pigeon bindings for Flutter and KMP"
    workingDir = file("frontend")
    
    commandLine("bash", "-c", "flutter pub get && dart run pigeon --input pigeons/messages.dart --dart_out $pigeonDartOut --kotlin_out $pigeonKotlinOut --kotlin_package \"com.pforhan.alphathinker\" --package_name \"com.pforhan.alphathinker\"")
}


tasks.register<Exec>("run") {
    group = "application"
    description = "Runs the application"
    workingDir = file("frontend")
    dependsOn(":shared:assemble", "generatePigeon")
    commandLine("flutter", "run")
}



gradle.projectsEvaluated {
    project(":shared").tasks.configureEach {
        if (name.contains("compile") || name.contains("assemble")) {
            dependsOn(":generatePigeon")
        }
    }
}


