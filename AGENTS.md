# AGENTS.md

## Project Overview

Alpha Thinker is an edge-LLM powered project planning app with iterative
question-and-answer synthesis. It helps users plan projects through
structured analysis of their project ideas.

See [README.md](README.md) and [ENG-DESIGN.md](ENG-DESIGN.md) for
architecture details and the full design specification.

## Project Structure

```
alpha-thinker/
├── settings.gradle.kts              (root project: :shared + :androidApp)
├── shared/                          (Kotlin Multiplatform module)
│   ├── build.gradle.kts
│   └── src/commonMain/kotlin/com/pforhan/alphathinker/
│       ├── model/                   (Project, Question, ExchangeRound)
│       ├── llm/                     (LLMIntegration interface + MockLLMIntegration)
│       └── repository/              (ProjectRepository — business logic)
├── androidApp/                      (Android Compose app)
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/pforhan/alphathinker/
│       ├── AlphaThinkerApp.kt       (Application + DI wiring)
│       ├── dao/                     (ProjectEntity, QuestionEntity, AnswerEntity, ProjectDao)
│       ├── database/                (AppDatabase — Room @Database)
│       ├── navigation/              (AppNavGraph, Screen)
│       ├── repository/              (AndroidProjectStorage — Room-backed implementation)
│       ├── ui/                      (MainActivity, MainViewModel, screens, components, theme)
│       └── src/debug/kotlin/        (Compose Previews)
└── gradle/
    └── libs.versions.toml           (dependency catalog: Kotlin 2.1.x, Compose 1.8.x, Room 2.6.1)
```

## Building and Running

### Building

To build the project (all variants), run:

```bash
./gradlew build
```

To build specific variants, use:

```bash
# Debug APK
./gradlew :androidApp:assembleDebug

# Release APK
./gradlew :androidApp:assembleRelease
```

**Module targets:** Prefix tasks with the module name to target a specific subproject:

| Task | Meaning |
|------|---------|
| `./gradlew :androidApp:build` | Build only the Android app |
| `./gradlew :shared:build` | Build only the KMP library |
| `./gradlew build` | Build all modules |

### Running

To install and run on a connected device or emulator:

```bash
# Debug variant
./gradlew :androidApp:installDebug
```

### Testing

```bash
# Unit tests (all modules)
./gradlew test

# Unit tests (shared module only)
./gradlew :shared:test

# Instrumentation tests
./gradlew connectedAndroidTest

# Instrumentation tests (androidApp only)
./gradlew :androidApp:connectedAndroidTest
```

## Development Conventions

### Packages

- `com.pforhan.alphathinker` — application namespace
- `com.pforhan.alphathinker.shared` — shared KMP module namespace
- `com.pforhan.alphathinker.model` — plain data classes in `shared/src/commonMain`
- `com.pforhan.alphathinker.llm` — LLM interface and implementations
- `com.pforhan.alphathinker.repository` — business logic and persistence adapters
- `com.pforhan.alphathinker.ui` — Compose UI (screens, components, theme)

### Data Flow

1. **UI** reads from `MainViewModel` (StateFlow)
2. **ViewModel** calls `ProjectRepository` (shared business logic)
3. **AndroidProjectStorage** (Room-backed) provides persistence
4. **LLM layer** generates initial and follow-up questions (swappable Mock → LiteLLM)

### LLM Integration

- `LLMIntegration` in `shared/src/commonMain` — KMP-safe interface
- `MockLLMIntegration` in `shared/src/commonMain` — template sets for offline dev
- **Next step:** replace `MockLLMIntegration` with a real LLM implementation

### Room Schema

| Entity         | Table       | Fields                                         |
|----------------|-------------|------------------------------------------------|
| `ProjectEntity` | `projects`  | id, title, synopsis, createdAt, updatedAt      |
| `QuestionEntity`| `questions` | id, projectId, round, text, timestamp, archivedAt |
| `AnswerEntity`  | `answers`   | questionId, text, answeredAt, modifiedAt       |

> **Migrations:** The app is in green-field development — always assume v1 fresh installs.
> Ignore Room migration logic during development; add `fallbackToDestructiveMigration()` in
> `AlphaThinkerApp.kt` when the schema is stable and migrations become necessary.

### UI Conventions

- All UI is Jetpack Compose. Extract widgets into a `ui` package subdirectory.
- Compose Previews live in `src/debug` to avoid restricting dependencies in release.
- State is managed via `MutableStateFlow` in view models.
- Styling is in the `ui.theme` package.

### Dependencies

- Version catalog at `gradle/libs.versions.toml`
- Centralized dependency management via `[versions]` and `[libraries]` sections
- Plugins managed via `[plugins]` section

## Deferred / Roadmap

| Feature                    | Status                    |
|----------------------------|---------------------------|
| Real LLM integration       | Interface ready; swap Mock |
| iOS app (`:iosApp`)        | Models already portable   |
| Project titles             | MVP skips titles          |
| Undo / history             | MVP edits in place        |
| Search / filter            | Not in MVP                |
| Theme toggle               | System-adaptive only      |
| App icon / color palette   | Open                      |
