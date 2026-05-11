# AGENTS.md

## Project Overview

See README.md for a description of the project.

## Building and Running

### Building

To build the project (all variants), you can use the following command:

```bash
./gradlew build
```

To build specific variants (e.g., for release), use:

```bash
# To build a debug APK
./gradlew assembleDebug

# To build a release APK
./gradlew assembleRelease
```

### Running

To install and run the application on a connected device or emulator, use the following command:

```bash
# Debug variant
./gradlew installDebug

# Release variant
./gradlew installRelease
```

### Testing

The project contains unit and instrumentation tests. To run them, use the following commands:

```bash
# To run unit tests
./gradlew test

# To run instrumentation tests
./gradlew connectedAndroidTest
```

Note: Compose Previews are located in the `src/debug` source set to avoid issues with restricted dependencies in the release build.

## Development Conventions

*   **UI:** The UI is built entirely with Jetpack Compose. UI widgets should be extracted into a `ui` package for better readability and maintainability.
*   **Previews:** Compose Previews are kept in the `src/debug` source set (e.g., `MainActivityPreview.kt`) to keep the release build footprint minimal and avoid compilation errors related to `ui-tooling-preview`.
*   **State Management:** The UI state is managed using a `MutableStateFlow` in the `MainActivity`.
*   **Styling:** The app uses a custom theme defined in a `theme` package.
*   **Dependencies:** The project uses Gradle for dependency management. The dependencies are defined in `app/build.gradle.kts`. The project uses a centralized dependency management system using the `gradle/libs.versions.toml` file. This file defines the versions of the dependencies and plugins used in the project. The `app/build.gradle.kts` file then references these versions. This makes it easier to manage the versions of the dependencies and plugins used in the project.
