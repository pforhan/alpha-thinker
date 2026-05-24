# Alpha Thinker

Edge-LLM powered project planning app with iterative question-and-answer synthesis. Helps users plan projects through structured analysis of their project ideas.

## Architecture

```
┌────── androidApp (Compose Multiplatform) ──────┐
│  ProjectsList │ NewProject │ ProjectDetail      │
└──────────────────┬─────────────────────────────┘
                   │ Room DB + DI
┌──────────────────┴─────────────────────────────┐
│           shared (Kotlin Multiplatform)         │
│  Models │ LLM Interface │ Repository            │
└─────────────────────────────────────────────────┘
```

- `:shared` — Kotlin Multiplatform module (JVM, Android, iOS targets) containing models, LLM interface, business logic
- `:androidApp` — Android Compose UI, Room database, LLM actual implementation

## Quick Start

```bash
# Clone
git clone <repo>
cd alpha-thinker

# Build
./gradlew build

# Run on device/emulator
./gradlew installDebug
```

## Directory Structure

```
├── settings.gradle.kts          # Root: :shared + :androidApp
├── shared/                      # KMP shared logic
│   ├── model/                   # Project, Question, ExchangeRound (Room-agnostic)
│   ├── llm/                     # LLMIntegration interface + Mock implementation
│   └── repository/              # ProjectRepository
└── androidApp/                  # Android Compose app
    ├── dao/                     # Room entities + DAO
    ├── database/                # Room @Database
    ├── navigation/              # Nav graph
    └── ui/screen/               # Compose screens
```

## Tech Stack

| Layer | Technology |
|---|---|
| Platform | Kotlin Multiplatform (Android first, iOS next) |
| UI | Jetpack Compose / Compose Multiplatform |
| Database | Room |
| Navigation | androidx.navigation |
| Serialization | kotlinx-serialization |
| LLM | Swappable interface (Mock → LiteLLM) |
| Min SDK | 26 |
| Target SDK | 36 |

## Design Documentation

See [ENG-DESIGN.md](./ENG-DESIGN.md) for architecture, data model, LLM interface, and implementation details.

## TODO

See [TODO.md](./TODO.md) for the implementation checklist.
