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

To get the application running, simply execute one of the following commands from the root directory:

```bash
./gradlew runWeb
./gradlew runDesktop
./gradlew runAndroid
./gradlew runIos
```

This command automatically handles the generation of Pigeon bindings and builds the KMP shared module before launching the app.
Note: using gradle to launch flutter means the flutter TUI won't be responsive even if it displays its run key commands.

### Build Details (Optional)

For developers who need more granular control, the following tasks are available:
- `./gradlew generatePigeon`: Generates type-safe communication bindings between Flutter and the KMP core.
- `./gradlew :shared:assemble`: Builds the KMP shared module.
- `./gradlew run`: Runs the full application.

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
