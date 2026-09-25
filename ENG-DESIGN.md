# ENG-DESIGN.md

## Engineering Strategy & Investigations

This document outlines the technical investigations and design decisions required to implement Alpha Thinker.

### Build System
**Gradle** is the primary build system for the **Kotlin Multiplatform (KMP) / Compose Multiplatform** application. Builds run standard Gradle tasks and cover the platform targets enabled in `shared/build.gradle.kts` (Android today; iOS, web, and desktop as they are enabled). Platform app entry points live in thin per-platform modules that depend on the shared module (`androidApp/` today).

### Key Decisions
- **Layered Architecture:** The UI layer (Compose Multiplatform) remains "logic-free," acting as a presentation layer that observes the KMP engine. Complex business logic and data management reside within the KMP layer.
- **Inference Engine:** Adopted **Koog** (`ai.koog:koog-agents`) as the LLM abstraction and agent layer, with a **selectable inference backend per engine mode** (see "LLM Inference Layer" below): system on-device models (Gemini Nano via ML Kit GenAI; Apple Foundation Models via a Swift bridge) through a **hand-rolled Koog `LLMClient`**, remote cloud / OpenAI-compatible / Ollama through Koog's shipped clients, and downloaded local models through **Google's LiteRT-LM** (in-process; a JVM adapter on desktop). Llamatik (llama.cpp KMP) was investigated as an alternative and is reserved as a fallback if the LiteRT-LM desktop path stalls.
- **Resilience & Fallback:** If the LLM inference fails (e.g., due to resource constraints or malformed output), the app will transparently fall back to the **Alpha Thinker Lite** implementation using the hardcoded seed questions.
- **State Management:** UI state follows Compose Multiplatform conventions, with ViewModels exposing `StateFlow` state.
- **Unified UX:** The visual styling and user interface will remain consistent across both the Lite and Edge editions.
- **Data Persistence:** For the development phase, complex schema migrations will be ignored.

## Target Architecture (v1.0)
This iteration proposes a clear separation of concerns:
1. **Frontend UI:** Compose Multiplatform for a single, unified, and cross-platform user experience.
2. **Core Logic/Engine:** Kotlin Multiplatform (KMP) for handling core domain logic, data persistence, and heavy computational lifting.
3. **LLM Inference Layer:** Koog-backed edge-LLM execution for offline-first autonomous question generation, synthesis, and (opt-in) agentic lookup. Backends plug into Koog's `LLMClient` / prompt-executor seam: system on-device models (Gemini Nano, Apple Foundation) via a hand-rolled client, remote OpenAI-compatible / Ollama via Koog's shipped clients, and downloaded local models via LiteRT-LM (see "LLM Inference Layer" below).

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

4. **ActivityLogger** (the store; renamed and simplified from `EngineActivity`) — a **flat,
    append-only journal**, one table (`log_events`) in its own
    `ActivityDatabase` (a separate Room database file: independent growth,
    pruning, and migration, and a wholesale "Clear log" wipe can never touch
    projects/questions/settings). A logical interaction/task is simply the
    group of rows sharing an `activityId`; nothing is ever updated — every
    engine action appends. There is no parent/child nesting and no per-event
    duration: aggregation by shared `activityId` and duration derived from the
    clumped rows' timestamps are read model concerns, never stored.

    Columns:
    *   `id` (PK, autoincrement — global row order)
    *   `activityId` (indexed, Optional — one logical activity; rows without an
        activity id are standalone lines of their own)
    *   `projectId` (indexed, Optional — a pure per-project visibility filter;
        deleting a project never cascades into the log)
    *   `category` (Enum: `QuestionGeneration`, `TitleRecommendation`,
        `CapabilityCheck`, `Lookup`, `TaskRun`, `Info` — the row's *topic*,
        stable and filterable, chosen per interaction by the writing layer)
    *   `source` (Enum: `Lite`, `LocalLLM`, `RemoteLLM`, `Tool`, `TaskRunner`,
        `App`, Optional — the *producer* of the row: which engine family, the
        task framework, or the app itself). `logCategory`-style topic confusion
        is avoided by splitting the old composite into these two orthogonal
        fields: e.g. a Koog-backed recommendation is `TitleRecommendation` from
        `RemoteLLM`.
    *   `log` (String — the durable text: lifecycle rows read
        `started:`/`succeeded`/`failed: <msg>`/`cancelled`; interaction rows
        read `prompt:` (full rendered prompt) or `input:` plus a terminal
        `response:`/`failed:` row. The bylines namespace its free-form text the
        way the old typed per-event payload columns did.)
    *   `timestamp` (Timestamp)

    **Read models are derived, never stored.** `ActivityRecord.groupByActivity()`
    groups rows by `activityId` (newest activity first) and synthesizes, per
    activity, a one-line `summary` (the last `response:`/terminal row, else the
    newest row), a `source`, a failure flag (`hasError`), and an elapsed
    `duration` from the clumped rows' timestamps — the LLM Interaction Log
    viewer is a thin projection over these. There is no startup recovery: the
    journal is flat and TTL-pruned by **row age**, so in-flight work simply
    ages out after process death.

    **Write path — two writers, one channel.** `TaskRunner` appends the
    lifecycle rows (`started:` on start, then `succeeded` / `failed: <msg>` /
    `cancelled` on resolution, with `activityId = taskId`). A
    **`LoggingPlanningEngine` decorator** — wrapping whichever engine is
    active, exactly as `SlowDownPlanningEngine` wraps
    `HardcodedPlanningEngine` — appends the interaction detail (an `input:` or
    full `prompt:` row, then a terminal `response:` or `failed:` row). The task
    body passes its `taskId` into the engine call as `activityId` (the
    `PlanningEngine` methods carry a required `activityId: String` with no
    defaults, so every call is attributed to its originating generation task by
    construction), so the decorator's detail groups under the same activity as
    the lifecycle rows. `ProjectRepository` and the engines are pure producers
    — neither writes the log.

    **Retention:** a settable TTL (new app setting, default 7 days) prunes
    rows whose timestamp is older than the window; a manual **"Clear log"**
    action wipes the separate database wholesale. Deleting a project does not
    cascade into the log; per-project visibility is a `projectId` filter.

    The rename from `LLMInteraction` (and simplification from `EngineActivity`)
    reflects that the app tracks more than LLM traffic: **remote HTTP calls**
    (cloud / OpenAI-compatible backends, PRD 6), the **hardcoded Lite
    fallback**, and **tool calls** (e.g. an LLM-requested web lookup) all land
    here. The log is the *persisted* form of the current in-memory
    `GenerationTask`, so the System/Debug workspace (PRD 5.5: LLM Interaction
    Log + Task Manager) reads one append-only table. `TaskRunner` still
    reads/writes its in-memory StateFlow today; the derived
    `ActivityRecord.groupByActivity()` read model powers the Activity Log viewer
    (Phase 3). The engine-family naming collision this removes (item 4's old
    `engine` enum vs the `LogCategory` type) is why the engines report
    `LogSource` on the log.

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
  today; a `KoogPlanningEngine` over the Koog `LLMClient`/executor seam in Phase
  3, which hosts the on-device, remote, and downloaded-model backends.)

**Roadmap / deferred:**

- Tasks are in-memory for now. Persistence implements the append-only
  `EngineActivity` event log (separate `ActivityDatabase`; schema item 4
  above), whose *latest-event-per-activity* read model lets task status and
  history survive process death and feed the System/Debug workspace
  (PRD 5.5: LLM Interaction Log + Task Manager) — the log is the durable
  `GenerationTask` record. See IMPLEMENTATION-PLAN.md Phase 3 (persistence +
  retention items) for the build order.

### LLM Inference Layer: Koog (Decision — supersedes "Research: Koog for Lookup & Web Search Tools")

**Adopted.** Koog (`ai.koog:koog-agents`, 1.2.0, Apache-2.0, JetBrains) is the
LLM abstraction + agent layer for the Edge engine. It targets the JVM, JS,
WasmJS, and iOS targets this project compiles for, requires JDK 17+ and Kotlin
2.3.10+ (this project is on Kotlin 2.3.21 / coroutines 1.10.2), and ships:
cloud LLM clients (OpenAI, Anthropic, Google, DeepSeek, OpenRouter, Ollama,
Bedrock), an official LiteRT client module (`prompt-executor-litert-client`),
prompt executors with multi-provider routing + fallback
(`MultiLLMPromptExecutor`), tool-calling (`@Tool`, `ToolRegistry`, class-based
tools), MCP server integration (`agents-mcp`), structured output,
RAG/embeddings, and tracing. The `PlanningEngine` interface is unchanged; a
`KoogPlanningEngine` implements it over Koog's client/executor seam.

**Engine modes** (a persisted setting; the active engine reports its producer
as `LogSource` on each activity-log row):

| Mode | Backend | `LogSource` |
|---|---|---|
| Lite (default) | `HardcodedPlanningEngine` (unchanged) | `Lite` |
| On-device | `OnDeviceLLMClient` — hand-rolled Koog `LLMClient` | `LocalLLM` |
| Remote | Koog `OpenAILLMClient` / `OllamaClient` | `RemoteLLM` |
| Downloaded | Koog `LiteRTLLMClient` (Android) + own JVM adapter (desktop) | `LocalLLM` |

**Fallback:** any LLM backend that raises `AnalysisFailure` (or is unavailable /
disabled) delegates transparently to Lite via a `FallbackPlanningEngine`-style
wrapper around the Koog layer — the original resilience decision, tracked
as IMPLEMENTATION-PLAN.md Phase 3 "Fallback Mechanism".

**System on-device client (hand-rolled).** The system-model path is a thin,
in-repo Koog `LLMClient`: an Android actual over ML Kit GenAI
(`Generation.getClient()` / AICore / Gemini Nano) and an iOS actual over a
Swift `SystemPromptApi` bridge into Apple Foundation Models
(`LanguageModelSession`), registered from the iOS app at startup. **No
third-party beta library was adopted** — the commonly cited on-device KMP
wrappers (`adrianczuczka/ondevice-ai`, `uny/koog-ondevice`) are pre-1.0
community projects, and `nicklama/ondevice-ai` does not exist. The
`joreilly/OnDeviceAI` sample is the reference shape for the bridge. The client
reports availability (`Available / Downloadable / Downloading / Unavailable`)
to drive engine-picker UI.

**Downloaded local models (LiteRT-LM).** In-process `.litertlm` inference via
Koog's official `LiteRTLLMClient` on Android plus a small JVM `LLMClient`
adapter over `litertlm-jvm` for desktop. Roadmap: web (wasm) on-device via
WebLLM / LiteRT JS. **Llamatik** (llama.cpp KMP covering Android, iOS, Desktop,
WASM) was investigated as a single-dependency alternative; it has no Koog
integration and bundles STT/image-generation this app never uses, so it is a
**contingency** if the LiteRT-LM desktop path stalls.

**Web search / research augmentation — supported via Koog tools.** Search
capable (tool-calling) backends can invoke custom tools we register (`webSearch`,
`fetchPage`) on a `ToolRegistry`, or Koog can import an external web-search MCP
server's tools with zero tool code (`McpToolRegistryProvider`: HTTP/SSE on
mobile, stdio/`fromProcess` on desktop), or use the `rag`/embeddings module for
local retrieval. Tool-call rows land in the activity log as flat `Lookup`
category rows from the `Tool` source (schema item 4), their own activities until
grouped with a shared `activityId`.

- [ ] **Tool-calling with system on-device models (design task — tracking only, not blocking):** system models (Gemini Nano / Apple Foundation) reject tool prompts today, so decide how the lookup/research agent composes with the On-device mode — e.g. route the research agent to a tool-capable backend (remote client or LiteRT FunctionGemma), or split lookups into an explicit pre-tool step that feeds results into context.
- [ ] **Confirm Koog support on every `shared` target on the current toolchain** during integration (JVM, JS, WasmJS, iOS; Android consumes the JVM artifact).
- [ ] **Choose lookup/search backends.** Options: provider-native web search (`webSearchOptions` / `enableSearch` on OpenAI-style clients), a custom `webSearch` / `fetchPage` tool, or the `rag` module for local lookup/memory. Decide per backend given the offline-first constraint.
- [ ] **Privacy & network trade-offs.** Web search sends queries to external services; short-circuit the offline-first guarantee. Gate it behind an opt-in setting surfaced in the settings UI alongside the LLM on/off toggle.
- [ ] **Failure behavior.** Define graceful degradation (no results, offline, provider unreachable); tool/search calls are captured in the app-wide activity log.

### TODO: LLM Inference Strategy
- [ ] Define fallback behavior for low-resource devices.
- [ ] Benchmark initial prompt latency vs. iterative follow-up performance.

### TODO: UI/UX Architecture
- [ ] Prototype the "Iterative Question Card" interaction.
- [ ] Design a navigation strategy that scales from mobile to desktop.
- [ ] Plan the export pipeline (Markdown synthesis -> File System).
