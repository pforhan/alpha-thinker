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

The shared module (`shared/build.gradle.kts`) compiles for the Android target via the Android-KMP library plugin; the `androidApp/` module (Activity, Application, manifest) compiles with AGP's built-in Kotlin and depends on `:shared`.

```bash
./gradlew :androidApp:assembleDebug   # build the debug APK
./gradlew :androidApp:installDebug    # install the debug APK on a connected device or running emulator
./gradlew :shared:allTests  # run tests for all targets (Android host, desktop, JS, wasmJS, and iOS)
./gradlew :androidApp:lint            # run the Android linter
```

### Web

The web target is enabled: `shared/` exposes the `wasmJs` (and legacy `js(IR)`) targets, and the `webApp/` module is the web entry point. The shared UI renders with the Skia/Canvas backend via Compose for Web.

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # start the dev server (http://localhost:8080)
./gradlew :webApp:wasmJsBrowserDistribution     # build the production bundle
./gradlew :webApp:wasmJsBrowserProductionRun    # serve the production bundle
```

The production bundle lands in `webApp/build/dist/wasmJs/productionExecutable/` — serve that directory with any static server. Browser tests run via `./gradlew :shared:wasmJsBrowserTest`; like the JS browser test, they require a Chromium binary, wired from the `CHROME_EXECUTABLE`/`CHROME_BIN` environment variable (the test disables itself when absent).

### Desktop

The desktop target is enabled: `shared/` exposes a `desktop` (JVM) target, and the `desktopApp/` module is the desktop entry point.

```bash
./gradlew :desktopApp:run            # run the desktop app
./gradlew :desktopApp:build          # build the desktop app
# create a distributable app/installer:
./gradlew :desktopApp:createDistributable
```

### iOS

The iOS target is enabled: `shared/` compiles for `iosArm64` (device) and `iosSimulatorArm64` (simulator), packaged as a static `Shared` XCFramework. The `iosApp/` directory holds the Xcode project entry point.

Build and run through Xcode (`iosApp/iosApp.xcodeproj`); Gradle provides the Kotlin/Native tasks, e.g.:

```bash
./gradlew :shared:iosSimulatorArm64Test   # run iOS simulator tests
./gradlew :shared:embedAndSignAppleFrameworkForXcode   # invoked by the Xcode build to embed the framework
```

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