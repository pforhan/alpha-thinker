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
    val createdAt: Instant,
    val updatedAt: Instant
)
```

### Question
```kotlin
data class Question(
    val id: String,
    val text: String,
    val timestamp: Instant
)
```

### ExchangeRound
```kotlin
data class ExchangeRound(
    val round: Int,
    val questions: List<Question>,
    val answer: List<Answer>
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
@JvmDefault
interface LLMIntegration {
    @Throws(AnalysisFailed::class)
    suspend fun generateInitialQuestions(project: Project): List<Question>

    @Throws(AnalysisFailed::class)
    suspend fun generateAnswerRound(project: Project): List<Answer>
}

class AnalysisFailed(val message: String) : Exception()
```

### Mock Implementation (shared)
MockLLMIntegration provides 5-8 template sets that cycle. Each set contains 2-3 questions. When all questions in a round are answered, the next set is used. A counter appends to questions when they repeat.

## Repository

### @shared (business logic)
ProjectRepository contains only business logic. It delegates data access to the actual Repository implementation.

### @repository (Room-backed actual)
AndroidProjectRepository provides Room-backed persistence.

```kotlin
@Suppress
interface Repository
androidApp/
├── dao/
│   ├── QuestionEntity.kt (Room @Entity @ColumnInfo)
│   ├── AnswerEntity.kt (@Entity @ColumnInfo)
│   ├── ProjectDao.kt (@Dao)
│   └── AppDatabase.kt (@Database)
└── repository/
    └── AndroidProjectRepository.kt (@Component actual)
```

## User Interface

### Flow
```
ProjectsList → NewProject → ProjectsList → ProjectDetail
```

### Navigation
AppNavGraph defines three Compose destinations:
- `Screen.ProjectList`
- `Screen.ProjectCreation`
- `Screen.ProjectDetail`

### ProjectsListScreen
- Empty state: CTA to new project
- When projects exist: list of project synopsis with creation date

### ProjectsDetail
- Header: synopsis, back button, export button
- Q-A cards (current round unanswered questions at top)
- "Answer all" button loads the next round
- Exported content includes all rounds of question + answers

### Question + Answer Cards
Each card contains:
- Question text
- Question timestamp
- Answer textarea (if unanswered)
- Answer text (if answered, with inline edit)
- Revise button (opens inline edit)
- Completed indicator (checkmark)
- Collapse + expand functionality for the round

### Q-A Flow Logic
1. User creates project (title + synopsis)
2. ProjectRepository creates project → save → trigger analysis
3. If LLM (Mock) integration is active: load template question set
4. User can answer + edit questions
5. When all questions are answered: load next round or complete
6. Export: concatenate title + synopsis + Q-A rounds

### Mock Question Templates (5-8 sets)
```kotlin
listOf(
  listOf(
    "What problem does this project solve?",
    "Who is the primary user?",
    "What are the key features?"
  ),
  listOf(
    "What's the core value proposition?",
    "What is the biggest constraint?",
    "What does success look like?"
  )
  // ...
)
```

### Export
ProjectRepository exports the project:
- Read all stored data → synthesize markdown → send to user via Intent.ACTION_CREATE_DOCUMENT
- Content saved via markdown format

## Design Decisions

- Plain data classes in @shared (no @Annotations) to ensure Kotlin Multiplatform portability
- Room in @repository for Android persistence (can be swapped for KMP persistence)
