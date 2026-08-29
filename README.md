# Alpha Thinker

Lightweight project planning app with iterative question-and-answer synthesis. Helps users plan projects through structured and iterative analysis of their project ideas.

Alpha Thinker is available in two versions:
- **Alpha Thinker Edge**: Powered by a local nano-LLM for automated question generation and synthesis.
- **Alpha Thinker Lite**: A lightweight version using a fixed set of seed questions and user-driven manual planning.

## Building

The app is a Kotlin Multiplatform / Compose Multiplatform project. Build and run it with standard Gradle tasks from the root of the repository. The UI implementation lives in `composeApp/`.

### Prerequisites

- **JDK 21**: Required for the KMP shared module and Android app.
- **Android Studio / Xcode**: For platform-specific builds.

### Android

Android is the currently active target in `composeApp/build.gradle.kts`.

```bash
./gradlew :composeApp:assembleDebug   # build the debug APK
./gradlew :composeApp:installDebug    # install the debug APK on a connected device or running emulator
./gradlew :composeApp:testDebugUnitTest  # run unit tests
./gradlew :composeApp:lint            # run the Android linter
```

### iOS, Web, and Desktop

The iOS, web, and desktop targets are not yet enabled in `composeApp/build.gradle.kts`. When a target is enabled, it will be built and run via the generated Gradle tasks:

- **iOS**: Build and run through Xcode (`composeApp/iosApp`); Gradle provides the Kotlin/Native tasks, e.g. `./gradlew :composeApp:iosSimulatorArm64Test` for tests.
- **Web**: `./gradlew :composeApp:wasmJsRun`.
- **Desktop** (JVM): `./gradlew :composeApp:run`.

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