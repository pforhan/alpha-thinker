# Alpha Thinker

Lightweight project planning app with iterative question-and-answer synthesis. Helps users plan projects through structured and iterative analysis of their project ideas.

Alpha Thinker is available in two versions:
- **Alpha Thinker Edge**: Powered by a local nano-LLM for automated question generation and synthesis.
- **Alpha Thinker Lite**: A lightweight version using a fixed set of seed questions and user-driven manual planning.

## Building

### Prerequisites
- **Flutter SDK**: Required for the frontend.
- **JDK 21**: Required for the KMP shared module and Android app.
- **Android Studio / Xcode**: For platform-specific builds.

### Setup Instructions

1.  **Gradle Wrapper**:
    The project uses a single Gradle wrapper located in the root directory. Use `./gradlew` for all Gradle-related tasks (building the `shared` module, etc.).

2.  **Generate Pigeon Bindings**:
    Type-safe communication between Flutter and the KMP core is handled by Pigeon. Generate the bindings using:
    ```bash
    cd frontend
    flutter pub get
    dart run pigeon --input pigeons/messages.dart --dart_out lib/pigeon.dart --kotlin_out ../shared/src/androidMain/kotlin/com/pforhan/alphathinker/Pigeon.kt --kotlin_package "com.pforhan.alphathinker"
    ```

3.  **Build Shared Module**:
    Build the KMP shared module:
    ```bash
    ./gradlew :shared:assemble
    ```

4.  **Run Flutter App**:
    ```bash
    cd frontend
    flutter run
    ```

### Configuration Notes

- **Flutter Engine Version**: The `shared` module requires a specific `flutter_embedding_debug` version that matches your local Flutter SDK's engine version. This is tracked in `gradle/libs.versions.toml` under `flutterEngine`. To update it to match your local SDK:
    ```bash
    cat $(flutter doctor -v | grep "Flutter SDK at" | awk '{print $4}')/bin/internal/engine.version
    ```
    Then update `flutterEngine` and the `flutter-embedding` library entry in `libs.versions.toml`.

## Design Documentation

See [PRD.md](PRD.md) for product requirements.
See [ENG-DESIGN.md](ENG-DESIGN.md) for architecture, data model, LLM interface, and implementation details.

## Implementation Progress

See [IMPLEMENTATION-PLAN.md](./IMPLEMENTATION-PLAN.md) for the project roadmap and status.
