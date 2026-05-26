# Alpha Thinker

Edge-LLM powered project planning app with iterative question-and-answer synthesis. Helps users plan projects through structured analysis of their project ideas.

## Architecture

```
┌────── androidApp (Compose) ──────┐
│ ProjectsList │ NewProject │ ProjectDetail │
└──────────────────┬─────────────────────────────┘
                    │ Room DB + DI
┌──────────────────┴─────────────────────────────┐
│           shared (Kotlin Multiplatform)         │
│  Models │ LLM Interface │ ProjectRepository     │
└─────────────────────────────────────────────────┘
```

- `:shared` — Kotlin Multiplatform module containing models, LLM interface, and `ProjectRepository` (business logic).
- `:androidApp` — Android Compose UI, Room database, and `AndroidProjectStorage` (persistence implementation).

## Quick Start

```bash
# Clone
git clone <repo>
cd alpha-thinker

# Build
./gradlew build

# Run on device/emulator
./gradlew :androidApp:installDebug
```

## Directory Structure

```
├── settings.gradle.kts          # Root: :shared + :androidApp
├── shared/                      # KMP shared logic
│   ├── model/                   # Project, Question, ExchangeRound, Answer
│   ├── llm/                     # LLMIntegration interface + Mock implementation
│   └── repository/              # ProjectRepository (business logic)
└── androidApp/                  # Android Compose app
    ├── dao/                     # Room entities + DAO
    ├── database/                # Room @Database
    ├── navigation/              # Nav graph
    ├── repository/              # AndroidProjectStorage (persistence)
    └── ui/                      # Compose screens, viewmodels, theme
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
