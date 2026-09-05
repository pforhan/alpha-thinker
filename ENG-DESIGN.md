# ENG-DESIGN.md

## Engineering Strategy & Investigations

This document outlines the technical investigations and design decisions required to implement Alpha Thinker.

### Build System
**Gradle** is the primary build system for the **Kotlin Multiplatform (KMP) / Compose Multiplatform** application. Builds run standard Gradle tasks and cover the platform targets enabled in `composeApp/build.gradle.kts` (Android today; iOS, web, and desktop as they are enabled).

### Key Decisions
- **Layered Architecture:** The UI layer (Compose Multiplatform) remains "logic-free," acting as a presentation layer that observes the KMP engine. Complex business logic and data management reside within the KMP layer.
- **Inference Engine:** Under evaluation — options include **ondevice-ai** (KMP library for system-installed edge LLMs like Gemini Nano and Apple Foundation) or **Google's LiteRT-LM and MediaPipe** (`litertlm-kmp`). The choice will depend on seamlessness of integration and device support.
- **Resilience & Fallback:** If the LLM inference fails (e.g., due to resource constraints or malformed output), the app will transparently fall back to the **Alpha Thinker Lite** implementation using the hardcoded seed questions.
- **State Management:** UI state follows Compose Multiplatform conventions, with ViewModels exposing `StateFlow` state.
- **Unified UX:** The visual styling and user interface will remain consistent across both the Lite and Edge editions.
- **Data Persistence:** For the development phase, complex schema migrations will be ignored.

## Target Architecture (v1.0)
This iteration proposes a clear separation of concerns:
1. **Frontend UI:** Compose Multiplatform for a single, unified, and cross-platform user experience.
2. **Core Logic/Engine:** Kotlin Multiplatform (KMP) for handling core domain logic, data persistence, and heavy computational lifting.
3. **LLM Inference Layer:** Local edge-LLM execution for offline-first autonomous question generation and synthesis. Solution under evaluation: [ondevice-ai](https://github.com/nicklama/ondevice-ai) (system-installed LLMs) or [litertlm-kmp](https://github.com/sagar-develop/litertlm-kmp) (LiteRT-LM).

This model allows the KMP core to be the 'source of truth' for the application's business logic, decoupling it from UI platform specifics.

### Data Persistence Layer: Room/Android Architecture Components
Given the KMP logic core, the ideal solution for persistent storage is **Room KMP** (or a similar KMP wrapper for SQLite). This will provide a robust, type-safe abstraction over local persistence for our core entities (Projects, Questions, Answers).

Furthermore, to minimize boilerplate, we should implement a custom KSP (Kotlin Symbol Processing) step. This custom processor will observe our data model definitions and automatically generate necessary classes annotated with Room annotations, extension functions for conversions, and companion objects, preventing manual duplication and keeping the domain model clean.

### Core Data Schema Design

We propose a set of interconnected, technology-neutral entities to serve as the foundational data store.

1. **Project:**
    *   `projectId` (Unique ID)
    *   `synopsis` (String: Initial project idea provided by the user.)
    *   `editableTitle` (String: Title generated/edited during the process.)
    *   `creationDate` (Timestamp)
    *   `lastUpdated` (Timestamp)
    *   `status` (Enum: Draft, Complete, InReview)

2. **Question:**
    *   `questionId` (Unique ID)
    *   `projectId` (Foreign Key: Links to the parent Project.)
    *   `roundId` (Foreign Key: Links to the Round that surfaced this question —
        the former `contextId`. See the Rounds note below.)
    *   `text` (String: The full question text, either seed, user-input, or LLM-generated.)
    *   `isArchived` (Boolean: Tracks manual deactivation.)
    *   `createdAt` (Timestamp: When the question was first surfaced.)

3. **Answer:**
    *   `answerId` (Unique ID)
    *   `questionId` (Foreign Key: Links to the parent Question.)
    *   `responseText` (String: The user's written answer.)
    *   `answeredAt` (Timestamp: When the answer was filled. All committed versions are stored; the most recent is the active answer. UI shall allow viewing/restoring previous versions by making a new version.)

4. **LLMInteraction:**
    *   `llmInteractionId` (Unique ID)
    *   `projectId` (Foreign Key: Links to the parent Project.)
    *   `promptUsed` (String: The full input prompt sent to the LLM.)
    *   `generationPayload` (JSON/Text: The LLM's raw output or a synthesized structured prompt.)
    *   `suggestedQuestions` (JSON/Text: Any structured list of new questions derived by the LLM.)
    *   `toolCalls` (JSON/Text: Any tool invocations made during the interaction — tool name, arguments, results, and per-call timing. Populated when the LLM performs lookup / web search.)
    *   `durationMs` (Long: Time taken for the LLM to generate the response.)
    *   `timestamp` (Timestamp: When the interaction occurred.)

5. **GlobalQuestion:**
   *   `globalQuestionId` (Unique ID)
   *   `text` (String: The question text)
   *   `category` (String, Optional: To help organize global questions)
   *   `createdAt` (Timestamp)

6. **Round:**
   *   `roundId` (Unique ID — the value questions carry via `roundId`; one row per
       generation/generation round, whether initial, follow-up, or user-created.)
   *   `projectId` (Foreign Key: Links to the parent Project.)
   *   `roundNumber` (Int: 1-based ordering within the project.)
   *   `startedAt` (Timestamp: When the round's questions were first surfaced.)
   *   `completedAt` (Timestamp, Optional: Set when the user wraps up the round;
       `null` means the round is in progress.)
   *   `status` (Derived: `InProgress` when `completedAt == null`, else `Completed`.)

### Rounds (formerly "Question Context")

A **round** is the set of questions surfaced together (a generation batch).
It is the user-facing "checkpoint" in the planning flow: users answer a round,
wrap it up, and move to the next. Rounds are first-class entities so that
round state (in-progress vs. wrapped, timing) survives restarts and so the
LLM review (Phase 3) has something concrete to anchor to.

- `Question.roundId` is a foreign key to `Round.roundId`; today's `contextId`
  column becomes this FK — one UUID plays both roles, so no mapping table is
  needed (the repository already stamps questions with a fresh `randomUUID()`
  per generation round).
- Reconstructing rounds is just a `GROUP BY roundId` query; wrap-up sets
  `completedAt`, and the planning stage (Phase 2.8) equals the count of
  completed rounds.
- Hardcoded/fallback questions historically carried an empty `contextId`; with
  a real Round on creation and per follow-up round, every question gets a
  valid `roundId`.

### Generation Task Framework

LLM work — initial question generation, follow-up rounds, synopsis rewrites,
cohesive document synthesis, auto-archive evaluation — is inherently
long-running (seconds to minutes on edge devices). The core must never block a
calling coroutine or the UI on inference; instead, generation is modeled as an
observable background task.

**GenerationTask model:**

- `taskId` (Unique ID)
- `projectId` (Foreign Key: Links to the parent Project.)
- `kind` (Enum/type: `InitialQuestions`, `FollowUpQuestions`,
  `SynopsisRewrite`, `AutoArchive`, ...)
- `status` (Enum: `Queued`, `Running`, `Succeeded`, `Failed`)
- `progress` (Float 0..1, Optional: indeterminate `null` for discrete question
  rounds; denser values when an LLM streams a rewrite/synthesis)
- `error` (String?, set when `Failed`)
- `createdAt` / `startedAt` / `finishedAt` (Timestamps)

**TaskRunner:**

- One app-scoped instance owning a `CoroutineScope` (injected, app-lifetime).
- `enqueue(projectId, kind, onProgress?, body: suspend () -> T): GenerationTask`
  wraps a suspend body, transitions the task through
  `Queued -> Running -> Succeeded | Failed`, and exposes live tasks via an
  observable flow (`StateFlow<List<GenerationTask>>`, filterable by
  `projectId`).
- Body suspensions must be cooperative/cancellable; cancellation policy is
  decided when background notification lands (IMPLEMENTATION-PLAN.md Phase 3).

**Repository contract (LLM-ready):**

- Mutating writes (`createProject`, `updateAnswer`, `updateProject`) persist
  the user-facing state immediately and return immediately.
- Generation is enqueued as a task instead of awaited inline. On success the
  task body re-reads the project, performs the generation, and persists the
  result (e.g., new questions appended). The UI observes task completion and
  reloads the affected project.
- `QuestionGenerator` is invoked statelessly with the project context it
  needs: initial generation gets `synopsis` + the generated `editableTitle`;
  follow-up generation gets the project's questions (completed answers via
  `Question.currentAnswer`, skipped ones via `Question.isIgnored`). The same
  call shape works for the hardcoded stand-in and a real LLM alike.

**Roadmap / deferred:**

- Tasks are in-memory for now. Persistence (plus the `LLMInteraction` log via
  the schema above) will let task status and history survive process death and
  feed the System/Debug workspace (PRD 5.5: LLM Interaction Log + Task Manager).
- See IMPLEMENTATION-PLAN.md Phase 3 for the build order.

### Research: Koog for Lookup & Web Search Tools

[Koog](https://github.com/jetbrains/koog) is a JetBrains Kotlin Multiplatform
AI-agent framework that could give the LLM optional lookup and web-search
capabilities on an as-needed (agentic) basis rather than always-on.

- [ ] **Evaluate Koog as the LLM tool layer.** Koog exposes custom tools via
      `@Tool` / `@LLMDescription` annotations plus a `ToolRegistry`, letting the
      LLM decide when to call them — matching the "lookup / web search as
      needed" requirement. Verify it composes with the chosen edge inference
      engine (ondevice-ai vs. litertlm-kmp) or the cloud/Ollama fallback.
- [ ] **Confirm platform support.** Koog targets JVM, JS, WasmJS, Android, and
      iOS (KMP). Android (our active target) supports core agents, tool
      execution, and Ktor/OkHttp clients; it requires JDK 17+ and Kotlin 2.3.10+.
- [ ] **Choose lookup/search backends.** Options: provider-native web search
      (`webSearchOptions` / `enableSearch` on OpenAI-style clients), a custom
      web-search tool, or the built-in `rag` module for local lookup/memory.
      Decide per backend given the offline-first constraint.
- [ ] **Map edge vs. cloud inference.** Koog ships cloud LLM clients (OpenAI,
      Anthropic, Google, DeepSeek, OpenRouter, Ollama, Bedrock); native edge
      executors aren't core yet (see
      [KG-654](https://youtrack.jetbrains.com/issue/KG-654/Support-mainstream-Mobile-Edge-AI-Executors-via-KMP)).
      Research wrapping ondevice-ai / litertlm-kmp as a Koog `PromptExecutor`,
      or driving edge models through Ollama.
- [ ] **Privacy & network trade-offs.** Web search sends queries to external
      services; short-circuit the offline-first guarantee. Gate it behind an
      opt-in setting surfaced in the settings UI alongside the LLM on/off toggle.
- [ ] **Failure behavior.** Define graceful degradation (no results, offline,
      provider unreachable) and whether tool/search calls are captured in the
      LLM interaction log.

### TODO: LLM Inference Strategy
- [ ] Define fallback behavior for low-resource devices.
- [ ] Benchmark initial prompt latency vs. iterative follow-up performance.

### TODO: UI/UX Architecture
- [ ] Prototype the "Iterative Question Card" interaction.
- [ ] Design a navigation strategy that scales from mobile to desktop.
- [ ] Plan the export pipeline (Markdown synthesis -> File System).
