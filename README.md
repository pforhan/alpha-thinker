# Alpha Thinker

Lightweight project planning app with iterative question-and-answer synthesis. Helps users plan projects through structured and iterative analysis of their project ideas.

Alpha Thinker is available in two versions:
- **Alpha Thinker Edge**: Powered by a local nano-LLM for automated question generation and synthesis.
- **Alpha Thinker Lite**: A lightweight version using a fixed set of seed questions and user-driven manual planning.

## Building

The app is a Kotlin Multiplatform / Compose Multiplatform project. Shared UI and domain code (Compose screens, Room persistence, question generation) lives in the `shared/` module. Each platform's app entry point lives in its own thin module that depends on `shared/`; the Android entry point is `androidApp/`. Build and run it with standard Gradle tasks from the root of the repository.

### Prerequisites

- **JDK 21**: Required for the KMP shared module and Android app.
- **Android Studio / Xcode**: For platform-specific builds.

### Android

Android is the currently active platform. The shared module (`shared/build.gradle.kts`) compiles for the Android target via the Android-KMP library plugin; the `androidApp/` module (Activity, Application, manifest) compiles with AGP's built-in Kotlin and depends on `:shared`.

```bash
./gradlew :androidApp:assembleDebug   # build the debug APK
./gradlew :androidApp:installDebug    # install the debug APK on a connected device or running emulator
./gradlew :shared:allTests  # run tests for all targets (currently the JVM host-test run)
./gradlew :androidApp:lint            # run the Android linter
```

### iOS, Web, and Desktop

The iOS, web, and desktop targets are not yet enabled in `shared/build.gradle.kts`. When a target is enabled, the shared module gains the corresponding KMP target and a dedicated app entry-point module (analogous to `androidApp`) is added for the platform:

- **iOS**: Build and run through Xcode; Gradle provides the Kotlin/Native tasks, e.g. `./gradlew :shared:iosSimulatorArm64Test` for tests.
- **Web**: `./gradlew :shared:wasmJsRun`.
- **Desktop** (JVM): `./gradlew :shared:run`.

### All Targets

```bash
./gradlew build   # assemble and test all enabled targets
./gradlew check   # run all verification tasks (tests + lint)
```

## Design Documentation

See [PRD.md](PRD.md) for product requirements.
See [ENG-DESIGN.md](ENG-DESIGN.md) for architecture, data model, LLM interface, and implementation details.

## Implementation Progress

See [IMPLEMENTATION-PLAN.md](./IMPLEMENTATION-PLAN.md) for the project roadmap and status.