# ENG-DESIGN.md

## Architecture Overview

Alpha Thinker uses a layered architecture with three modules:
- `@androidApp` — Entry point, Room DB, Compose UI, DI
- `@shared` — Models, LLM interface, business logic (KMP)
- `@repository` — Android Room-backed actual for @shared

```
┌────── androidApp (Compose Multiplatform) ──────┐
│  AppsNavGraph │ ProjectsList │ NewProject      │
└──────────────────┬─────────────────────────────┘
                   │ Room DB + DI
┌──────────────────┴─────────────────────────────┐
│           shared (Kotlin Multiplatform)          │
│  Project │ Question │ ExchangeRound             │
│  LLMIntegration (interface)                     │
│  ProjectRepository (business logic)             │
└─────────────────────────────────────────────────┘
```

## Gradle Configuration

```
├── settings.gradle.kts          # Root: @shared + @androidApp
├── build.gradle.kts             # Kotlin KMP project
├── shared/                      # KMP shared logic
│   ├── model/                   # Plain data classes
│   ├── llm/                     # Interface + implementations
│   └── repository/              # ProjectRepository
├── androidApp/                  # Android Compose app
│   ├── dao/                     # Room entities + DAO
│   ├── database/                # Room Database
│   ├── navigation/              # Navigation graphs
│   └── repository/              # Room-backed actual
└── gradle/                      # Wrapper + version catalog

gradle/libs.versions.toml:
compileSdk: 36
minSdk: 26
Kotlin: 2.1.x
Compose Multiplatform: 1.8.0+
kotlinx-serialization: 1.8+
Androidx Navigation: 2.8.0+
```

## Data Model

All data classes live in @shared/models to support KMP sharing.

### Project
```kotlin
data class Project(
    val id: String,
    val synopsis: String,
    val questions: List<Question>,
    val exchangeRounds: List<ExchangeRound>,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

### Question
```kotlin
data class Question(
    val id: String,
    val text: String,
    val timestamp: Instant,
    val contextId: String,
    val archivedAt: Instant?
)
```

### ExchangeRound
```kotlin
data class ExchangeRound(
    val round: Int,
    val questions: List<Question>,
    val contextId: String,
    val createdAt: Instant,
    val answers: List<Answer>,
    val questionsCount: Int
)
```

### Answer
```kotlin
data class Answer(
    val questionId: String,
    val text: String,
    val answeredAt: Instant,
    val modifiedAt: Instant
)
```

## LLM Integration

### Interface (shared)
```kotlin
interface LLMIntegration {
    @Throws(AnalysisFailed::class)
    suspend fun generateInitialQuestions(project: Project): List<Question>

    @Throws(AnalysisFailed::class)
    suspend fun generateFollowUpQuestions(project: Project, previousRound: Int): List<Question>
}

class AnalysisFailed(val message: String) : Exception()
```

### Mock Implementation (shared)
`MockLLMIntegration` provides 8 pre-defined question sets for both initial and follow-up rounds. It does not analyze the content of the answers, but rather selects a template based on the round number to simulate an iterative synthesis process.

## Repository

### @shared (Business Logic)
`ProjectRepository` contains the high-level project management and synthesis logic. It does not handle persistence directly but instead uses the `ProjectStorage` interface to perform data operations.

### @repository (Persistence)
`AndroidProjectStorage` is the Android-specific implementation of `ProjectStorage`, providing Room-backed persistence.

```kotlin
androidApp/
├── dao/
│   ├── ProjectEntity.kt
│   ├── QuestionEntity.kt
│   ├── AnswerEntity.kt
│   └── ProjectDao.kt
└── repository/
    └── AndroidProjectStorage.kt
```

## User Interface

### Flow
```
ProjectsList → NewProject → ProjectsList → ProjectDetail
```

### Navigation
`AppNavGraph` defines the navigation between:
- `Screen.ProjectList`
- `Screen.ProjectCreation`
- `Screen.ProjectDetail`

### ProjectsListScreen
- Displays a list of all projects with their synopsis and creation date.
- Shows the count of unanswered questions for each project.
- Empty state provides a CTA to create a new project.

### ProjectDetailScreen
- Displays the project's synopsis and a sequence of question/answer cards.
- **Active Round Focus**: The current unanswered questions are prioritized at the top.
- **Automated Transition**: Once all questions in the current round are answered, the system automatically triggers the LLM to generate the next round.
- **Auto-Archive**: A toggle that allows questions to be archived immediately upon being answered.
- **Interactive Cards**:
    - Question text and timestamp.
    - Editable answer text area.
    - Inline edit/revise functionality.
    - Visual indicators for completed answers.

### Export
The `ProjectRepository` implements logic to synthesize the entire project (synopsis and all Q&A rounds) into a Markdown string. This is designed to be shared via `Intent.ACTION_CREATE_DOCUMENT`.

## Design Decisions

- Plain data classes in @shared (no @Annotations) to ensure Kotlin Multiplatform portability
- Room in @repository for Android persistence (can be swapped for KMP persistence)
