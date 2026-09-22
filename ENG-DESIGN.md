# ENG-DESIGN.md

## Engineering Strategy & Investigations

This document outlines the technical investigations and design decisions required to implement Alpha Thinker.

### Build System
**Gradle** is the primary build system for the **Kotlin Multiplatform (KMP) / Compose Multiplatform** application. Builds run standard Gradle tasks and cover the platform targets enabled in `shared/build.gradle.kts` (Android today; iOS, web, and desktop as they are enabled). Platform app entry points live in thin per-platform modules that depend on the shared module (`androidApp/` today).

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
    *   `ignoredAt` (Timestamp, Optional: When the question was ignored/skipped.)
    *   `answerId` (Foreign Key, Optional: Points at the current committed
        `Answer` version for this question; `null` means unanswered.)
    *   `draftText` (String, Optional: In-progress answer text; `null` means no
        draft. Mutually exclusive with `answerId` — a question is either
        committed or a draft, never both, and the domain model `init` guards
        enforce this.)
    *   `draftUpdatedAt` (Timestamp, Optional: When the draft was last edited,
        for "latest edits first" draft sorting.)

3. **Answer:**
    *   `answerId` (Unique ID)
    *   `questionId` (Foreign Key: Links to the parent Question.)
    *   `responseText` (String: The user's written answer.)
    *   `createdAt` (Timestamp: When this version was committed. Answer rows are
        **immutable history** — every commit appends a new version rather than
        mutating a previous one, and the current version is chosen by the
        question's `answerId`. Deletion unpoints `answerId` (the version stays
        in history) so old versions can be viewed/restored by making a new
        version.)

4. **EngineActivity** (renamed and broadened from `LLMInteraction`) — an
    **append-only event log**, one table in its own `ActivityDatabase` (a
    separate Room database file: independent growth, pruning, and migration,
    and a wholesale "Clear log" wipe can never touch projects/questions/
    settings). A logical interaction/task is the group of event rows sharing
    `activityId`; nothing is ever updated — every engine action appends.
    Global order is the autoincrement `eventId`.

    Columns:
    *   `eventId` (PK, autoincrement — global event order)
    *   `activityId` (indexed — one logical activity; the group of rows whose
        *latest* event is that activity's current state)
    *   `parentActivityId` (indexed, Optional — **nesting**: a lookup / web
        search requested by the LLM is a *child activity* of the inference
        that requested it, so the log is a tree, not a flat list)
    *   `projectId` (Foreign Key, Optional)
    *   `roundId` (Foreign Key, Optional)
    *   `kind` (Enum: `TitleRecommendation`, `InitialQuestions`,
        `FollowUpQuestions`, `AvailabilityCheck`, `SynopsisRewrite`,
        `AutoArchive`, `Lookup`, ... — same vocabulary as the `TaskKind` enum)
    *   `engine` (Enum: `LocalInference` (edge LLM), `RemoteInference`
        (HTTP / cloud / OpenAI-compatible), `Hardcoded` (Lite fallback —
        `HardcodedPlanningEngine`), `Lookup` (web/lookup tool call)) — which
        backend actually executed. `LLMInteraction` only ever assumed the
        local LLM; the log records whichever engine ran.)
    *   `eventType` (Enum: `Created | Progress | Succeeded | Failed |
        Cancelled` — each row is one immutable transition; the payload fields
        fill per type)
    *   `progress` (Float, Optional — `Progress` events, 0..1)
    *   `error` (String, Optional — `Failed` / `Cancelled` events)
    *   `result` (Boolean, Optional — boolean-answering task outcomes, e.g.
        `AvailabilityCheck`)
    *   `promptUsed` / `parameters` (String / JSON, Optional — the input
        prompt or remote request body and its config, on the `Created` event)
    *   `generationPayload` / `suggestedQuestions` (JSON/Text, Optional — the
        engine's raw output and any structured question list, on terminal
        events)
    *   `durationMs` (Long, Optional — on the terminal event: the call's
        timing, or per-call timing for a `Lookup` child)
    *   `timestamp` (Timestamp)

    **Tool calls are child events, not a blob:** there is no `toolCalls`
    column. Each tool invocation (e.g. an LLM-requested web lookup) is its own
    child row (`engine = Lookup`, `parentActivityId` = the requesting
    inference) carrying name, arguments, results, and per-call timing in its
    event payload — one normalized source of truth for both tree display and
    per-call latency.

    **Read models are derived, never stored.** Live task state (Task Manager,
    project screens) = the *latest event per `activityId`* — a Room
    `@DatabaseView` window-function query (implemented as
    `ActivityDao.latestPerActivity()` via `ROW_NUMBER() OVER (PARTITION BY
    activityId ORDER BY eventId DESC)`), or a repository fold over the
    `TaskRunner`'s existing transition flow. The LLM Interaction Log reads the
    full history as a tree via `parentActivityId`. On startup the fold is
    replayed and any activity whose latest event is non-terminal gets a
    terminal `Failed("interrupted")` event (or is re-enqueued), so in-flight
    work recovers after process death.

    **Write path — two writers, one channel.** `TaskRunner` appends the
    lifecycle events (`Created` / `Progress` / `Succeeded` / `Failed` /
    `Cancelled`, with `activityId = taskId`). A **`LoggingPlanningEngine`
    decorator** — wrapping whichever engine is active, exactly as
    `SlowDownPlanningEngine` wraps `HardcodedPlanningEngine` — appends the
    interaction detail (`promptUsed` / `parameters`,
    `generationPayload` / `suggestedQuestions`, `durationMs`, and child
    `Lookup` tool-call events). The task body passes its `taskId` into the
    engine call as `activityId` (the `PlanningEngine` methods carry a required
    `activityId: String` with no defaults, so every call is attributed to its
    originating generation task by construction), so the decorator's detail
    groups under the same activity as the lifecycle rows. `ProjectRepository`
    and the engines are pure producers — neither writes the log.

    **Retention:** a settable TTL (new app setting, default 7 days) prunes
    *whole activities* whose terminal event is older than the window — live
    (non-terminal) activities are never pruned; a manual **"Clear log"**
    action wipes the separate database wholesale. Deleting a project does not
    cascade into the log; per-project visibility is a `projectId` filter.

    The rename from `LLMInteraction` reflects that the app tracks more than
    LLM traffic: **remote HTTP calls** (cloud / OpenAI-compatible backends,
    PRD 6), the **hardcoded Lite fallback**, and **nested tool calls** (e.g.
    an LLM-requested web lookup) all land here. The log is the *persisted*
    form of the current in-memory `GenerationTask`, so the System/Debug
    workspace (PRD 5.5: LLM Interaction Log + Task Manager) reads one
    append-only table. `TaskRunner` still reads/writes its in-memory
    StateFlow today; the DB read model (`latestPerActivity`) is implemented at
    the DAO and service level and powers the System/Debug reader (Phase 3).

5. **GlobalQuestion:**
   *   `globalQuestionId` (Unique ID)
   *   `text` (String: The question text)
   *   `category` (String, Optional: To help organize global questions)
   *   `createdAt` (Timestamp)

6. **Round:**
   *   `roundId` (Unique ID — the value questions carry via `roundId`; one row per
       generation/generation round, whether initial, follow-up, or user-created.)
   *   `projectId` (Foreign Key: Links to the parent Project.)
   *   `phase` (String: The planning phase this round belongs to — a labeled
       planning deliverable from the phase library (see PROJECT-FLOWS.md).
       Set to the library's first phase for round 1, picked from the
       generator's suggestions at wrap-up, or carried over by "Get more
       questions". The same phase can span multiple rounds — e.g. several
       UserRequested rounds refining the execution plan.)
   *   `roundNumber` (Int: 1-based ordering within the project.)
   *   `origin` (Enum: `Initial`, `FollowUp`, `UserRequested` — how the round came
       to be; `Initial` = the opening round of a phase (project start or
       wrap-up advancing to a new phase), `UserRequested` = the user tapped
"Get more questions" in the same phase, `FollowUp` = reserved for
        future use (revisiting a completed phase; deferred). Feeds dedup and
        the engine activity log.)
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
  `completedAt`, and the planning phase is read from the `phase` of the round
  currently in progress — fresh project with round 1 open is the library's
  first phase, and the current approach keeps the phase on the round rather
  than a column on the project (the column option remains open if it reads
  better later). The only write paths are wrap-up and "Get more questions":
  at wrap-up the user picks a "what's next?" phase from the generator's 2–3
  adjacent suggestions (or "Finish the plan") and the first round of that
  phase is created (`origin: Initial`), while "Get more questions" creates a
  `UserRequested` round in the current phase — no flow/category is enforced
  (PROJECT-FLOWS.md). A
  numeric "Phase N of M" for display is computed as an index into the phase
  library's ordering, not a stored field.
- Hardcoded/fallback questions historically carried an empty `contextId`; with
  a real Round on creation and per follow-up round, every question gets a
  valid `roundId`.

### Planning Phase Library

The current planning phase is read from the `phase` of the round in progress
(current approach; a project-level column is not ruled out — see the Rounds
note). The set of phases the app can be in comes from a **code-defined
`Phase` enum** in `commonMain` (the `phases` package):

- Each constant is `(stableKey, displayLabel, orderingIndex, questionPool)`;
  `Round.phase` holds the enum and persists through its stable
  **key** string. A persisted `phases` table is deferred until user-created/
  LLM-proposed labels arrive — a later migration is trivial because the key
  already is the reference.
- **Settled library: six domain-neutral phases** (Scope & Goals, Research,
  Design, Execution Plan, Validation Plan, Definition of Done), with "Finish
  the plan" as a terminal option rather than a seventh phase. We deliberately
  avoid a genre taxonomy (game/document/home-improvement etc.): relevance is
  expressed through each phase's question pool, not through extra phase labels.
  The wrap-up chooser (2-3
  options + "Finish the plan") stays the only phase surface the user meets,
  which bounds cognitive load. See PROJECT-FLOWS.md for the per-phase pool
  partition.
- **Pool serving:** each phase owns a slice of `HardcodedPlanningEngine`
  `questionPool`; the slice front holds the highest-value questions. The
  phase's initial round serves the front (~7), each `UserRequested` round
  continues from where the last stopped (~5). Pools are sized ~10-14 per phase
  so exhaustion (everything in the phase's pool has been asked) lands naturally
  after 1-2 "Get more questions" rounds.
- **Exhaustion signal:** per-phase. On exhaustion, "Get more questions"
  disables itself and "Finish the plan" surfaces first in the wrap-up chooser.
  This is the hardcoded mirror of the Phase 3 explicit generator "done" signal
  and must never be inferred from an empty generation result (`emptyList()` is
  indistinguishable from "nothing surfaced yet").
- **Next-phase suggestions (hardcoded):** the **sequential rule** — the
  immediate successor of the current phase always leads (even if previously
  visited), then the remaining slots fill with the project's *unvisited*
  phases, so a skipped phase stays reachable. (Weighted keyword scoring was
  designed and retired before shipping: ~6 keywords per phase scored against a
  few hundred words of synopsis + answers was mostly ties and phrasing noise,
  and it would only ever run in Lite mode.) The LLM (Phase 3) picks from the
  same library by reading the answers instead.

### Generation Task Framework

LLM work — initial question generation, follow-up rounds, synopsis rewrites,
cohesive document synthesis, auto-archive evaluation — is inherently
long-running (seconds to minutes on edge devices). The core must never block a
calling coroutine or the UI on inference; instead, generation is modeled as an
observable background task.

**GenerationTask model:**

- `id` (Unique ID)
- `projectId` (Foreign Key: Links to the parent Project.)
- `kind` (Enum/type: `InitialQuestions`, `FollowUpQuestions`,
  `SynopsisRewrite`, `AutoArchive`, ...)
- `status` (Enum: `Queued`, `Running`, `Succeeded`, `Failed`)
- `progress` (Float 0..1, Optional: indeterminate `null` for discrete question
  rounds; denser values when an LLM streams a rewrite/synthesis)
- `error` (String?, set when `Failed`)
- `createdAt` / `startedAt` / `finishedAt` (Timestamps)
- Helpers `isActive` / `isFinished` and `asStarted` / `asSucceeded` /
  `asFailed` keep state transitions in one place.

**TaskRunner:**

- One app-scoped instance owning a `CoroutineScope` (injected, app-lifetime).
- `enqueue(projectId, kind, body: suspend () -> Unit): GenerationTask` wraps a
  suspend body, transitions the task through
  `Queued -> Running -> Succeeded | Failed`, and exposes live tasks via an
  observable flow (`StateFlow<List<GenerationTask>>`, filterable by
  `projectId` via `tasksFor`). Progress is reported separately with
  `setProgress(taskId, progress)` and folded into the terminal publish, so a
  body reporting progress mid-run keeps it after completion.
- **Scheduling is per-resource-group, not global-serial.** Each task declares a
  `TaskGroup` (defaulting to `TaskKind.group`): `Engine` (concurrency 1 — the
  local planning engine is a single shared resource, so its tasks stay FIFO
  serial and a title recommendation always lands before the initial batch that
  reads the project) vs `Remote` (bounded parallelism for independent remote
  calls — remote LLM, HTTP lookups). On top of the group limit, tasks for the
  **same project never run concurrently** — bodies re-read and re-persist the
  whole `Project` aggregate, so two writers for one project would clobber each
  other; parallelism is safe across projects and for read-only checks like
  `RemainingInPhase`.
- Cancellation is intentionally coarse for now: `CancellationException` marks
  the task `Failed` with "Task cancelled" (policy refines when background
  notification lands, IMPLEMENTATION-PLAN.md Phase 3). Writes are always
  persisted *before* a task runs, so a cancelled task never loses user data.

**Repository contract (engine-ready):**

- Mutating writes (`createProject`, `saveAnswer`, `updateProject`) persist
  the user-facing state immediately and return immediately.
- Generation is enqueued as a task instead of awaited inline. On success the
  task body re-reads the project, performs the generation, and persists the
  result (e.g., new questions appended). The UI observes task completion and
  reloads the affected project.
- `PlanningEngine` is invoked statelessly with the project context it
  needs: initial generation gets `synopsis` + the generated `editableTitle`;
  follow-up generation gets the project's questions (completed answers via
  `Question.currentAnswer`, skipped ones via `Question.isIgnored`). The same
  call shape works for the hardcoded stand-in and a real LLM alike.
  (Implementors of the `PlanningEngine` interface: `HardcodedPlanningEngine`
  today; a local-inference engine and a remote HTTP engine in Phase 3.)

**Roadmap / deferred:**

- Tasks are in-memory for now. Persistence implements the append-only
  `EngineActivity` event log (separate `ActivityDatabase`; schema item 4
  above), whose *latest-event-per-activity* read model lets task status and
  history survive process death and feed the System/Debug workspace
  (PRD 5.5: LLM Interaction Log + Task Manager) — the log is the durable
  `GenerationTask` record. See IMPLEMENTATION-PLAN.md Phase 3 (persistence +
  retention items) for the build order.

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
      engine activity log.

### TODO: LLM Inference Strategy
- [ ] Define fallback behavior for low-resource devices.
- [ ] Benchmark initial prompt latency vs. iterative follow-up performance.

### TODO: UI/UX Architecture
- [ ] Prototype the "Iterative Question Card" interaction.
- [ ] Design a navigation strategy that scales from mobile to desktop.
- [ ] Plan the export pipeline (Markdown synthesis -> File System).
