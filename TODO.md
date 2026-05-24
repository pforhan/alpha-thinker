# TODO.md — Alpha Thinker Implementation Checklist

## Phase 1 — Gradle Scaffolding

- [ ] 1. `settings.gradle.kts` — Root: :shared + :androidApp
- [ ] 2. `build.gradle.kts` — Kotlin multiplatform project
- [ ] 3. `gradle.properties` — JDK, optimization flags
- [ ] 4. `gradle/libs.versions.toml` — compileSdk 36, minSdk 26
- [ ] 5. `gradle/wrapper/gradle-wrapper.properties`
- [ ] 6. `gradle/wrapper/gradle-wrapper.jar`

## Phase 2 — Shared Kotlin Module (KMP)

- [ ] 7. `shared/build.gradle.kts` — KMP: jvm, android, iosSimulatorArm64, iosArm64, iosX64
- [ ] 8. `shared/model/Project.kt` — plain Project data class
- [ ] 9. `shared/model/Question.kt` — plain Question data class
- [ ] 10. `shared/model/ExchangeRound.kt` — questions + answer containers
- [ ] 11. `shared/llm/LLMIntegration.kt` — interface
- [ ] 12. `shared/llm/MockLLMIntegration.kt` — 5-8 template sets, cycling
- [ ] 13. `shared/repository/ProjectRepository.kt` — CRUD + analyze flow

## Phase 3 — Android App Module

- [ ] 14. `androidApp/build.gradle.kts` — AGP + compose + shared
- [ ] 15. `androidApp/src/main/AndroidManifest.xml`
- [ ] 16. `androidApp/src/main/res/values/colors.xml`
- [ ] 17. `androidApp/src/main/res/values/themes.xml`
- [ ] 18. `AlphaThinkerApp.kt` — Application + DI
- [ ] 19. `dao/ProjectEntity.kt` — Room @Entity
- [ ] 20. `dao/QuestionEntity.kt` — Room @Entity
- [ ] 21. `dao/AnswerEntity.kt` — Room @Entity
- [ ] 22. `dao/ProjectDao.kt` — Room @Dao
- [ ] 23. `database/AppDatabase.kt` — Room @Database
- [ ] 24. `navigation/AppNavGraph.kt`
- [ ] 25. `ui/screen/ProjectsListScreen.kt`
- [ ] 26. `ui/screen/NewProjectScreen.kt`
- [ ] 27. `ui/screen/ProjectDetailScreen.kt`
- [ ] 28. `ui/component/QACard.kt`
- [ ] 29. `debug/MainActivityPreviews.kt`
- [ ] 30. `repository/AndroidProjectRepository.kt` — Room-backed actual

## Phase 4 — Documentation

- [ ] 31. `README.md` — architecture, quick start, directory, tech stack
- [ ] 32. `TODO.md` — GFM checklists (this file)
- [ ] 33. `ENG-DESIGN.md` — full design specification

## Room Schema

| Entity | Table | Fields |
|---|---|---|
| `ProjectEntity` | `projects` | id, title, synopsis, createdAt, updatedAt |
| `QuestionEntity` | `questions` | id, projectId, round, text, timestamp |
| `AnswerEntity` | `answers` | questionId, text, answeredAt, modifiedAt |

## Deferred

| Feature | When |
|---|---|
| Real LLM integration (LiteLLM) | Interface ready; swap MockImpl |
| iOS app (`:iosApp`) | Models are already portable |
| Project titles | Added later |
| Undo / history | MVP edits in place |
| Search / filter | Not in MVP |
| Theme toggle | System-adaptive only |

## Open Questions

- App icon design
- Color palette specifics
- Onboarding flow
