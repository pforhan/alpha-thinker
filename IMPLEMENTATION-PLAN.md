# Implementation Plan: Alpha Thinker

This document tracks the specific engineering tasks required to move from design to a functional product.

## Phase 1: Foundation & Infrastructure
- [x] Set up Flutter project structure
- [x] Implement KMP core logic layer
- [x] Configure Pigeon for Flutter/KMP communication (Aligned to `ThinkerApi`)
- [x] Implement Room KMP persistence layer (Entities/DAOs done, build stabilized)
- [x] Implement a simple script to transform Room/Pigeon models (Manual mappers implemented in `RoomStorage`)
- [x] Implement Project, Question, and Answer entities in the database
- [x] **Project Scaffolding:** Initialize Gradle-based multi-module project (KMP + Flutter).
- [x] **Web Support:** Implement Dart-side `ProjectService` with `InMemory` fallback for Web.
- [x] **Architectural Refinement:** Refactored naming to hide implementation details (Pigeon) and use domain-driven names (`ThinkerApi`, `ThinkerService`).

## Phase 2: Core User Experience and Lite Version
- [x] Implement Project creation and listing views
- [x] Implement the Question/Answer workspace
- [x] Implement question archiving functionality (manual deactivation)
- [x] Implement "Answered Questions" and "Archived Questions" views/filters. Just include a short blurb from the answer; allow opening for full details.
- [x] **Domain Logic Implementation:** Implementing core project management and question-answering flows.
- [x] Merge unanswered / answered / archived questions all into one view that defaults to unanswered questions. Answered questions should show 1 line of text of the answer on the list.  All questions should be clickable for full details.
- [x] change "archive" concept to "ignored"
- [x] Views / dialogs with a single text entry should focus that text field by default.
- [x] viewing an answered question should show the full answer and provide an update button (which makes a new answer record)
- [x] should be able to ignore / unignore / delete answer from question detail pane
- [x] **Lite Implementation:** Implement the hardcoded 20 Seed Questions logic for fallback/Lite mode.
- [x] Present questions to be answered in a random order.
- [x] We should only ever show 3 unanswered questions at a time.  Others should dynamically rotate in, with a new unanswered cards replacing the just-answered cards.  When showing unanswered questions, there should also be a shuffle button to bring in fresh questions (if available) without ignoring those currently visible.  Similarly, each question should also have an option to "ask later" that question by putting it at the end of the list.  Both actions operate similarly -- they place the current questions at the end of the sorted list and pull the next questions from the front of the list.  If there are no more unanswered questions these actions should not be available.
- [x] Sort answered questions by answer date descending, ignored questions by ignored date descending
- [x] add ask later to the unanswered question editor dialog
- [x] allow swipe-to-ignore (left) and swipe-to-ask-later (right)
- [x] add animations for ask later, shuffle, and ignore / unignore actions.
- [x] ability to edit project synopsis. Provide the means to clear prior answers or keep them.
- [x] When ask later saves answers as draft they should show up when that question is opened again. 
- [x] During edit project, clear all answers should also reset question state

## Phase 2.5: cleanup
- [x] break up bracket storms with multiple methods to make the code easier to read.
- [x] break up project_detail_screen.dart further, moving the dialog out at least
- [x] reformat all code with 2-space indents
- [x] combine new project dialog with edit project dialog
- [x] strip whitespace from all user-inputted text in frontend and backend
- [x] auto archive functionality should be for project updates, not question or answer updates.
- [x] clean up Answer.isAnswered
- [x] Smarter default title generation.  Take characters from synposis up until sentence end, newline, or 30 characters, whichever is first.  Limit manual title editing to 30 characters.
- [x] Make better use of Room annotations, e.g. @Relation on our DB objects to simplify RoomStorage
- [x] repackage files from com.pforhan.alphathinker to alphainterplanetary.alphathinker
- [x] reformat files to 2-space indent
- [x] make a QuestionFilter enum and gather any related strings in it to provide type safety
- [x] Rename LLMIntegration to QuestionGenerator
- [x] Make a QuestionDAO since questions and answers can be updated independently of a project.
- [x] Remove pigeon codegen
- [x] Convert to KMP / Compose, away from flutter (marked done although there's more in Phase 2.7 to do)
  - [x] **ProjectList: wire up the add-project button.** `ProjectListScreen` sets `showCreateDialog = true` but never renders a dialog, so the Add FAB and "Create your first project" button do nothing. Add a new-project dialog (Flutter `EditProjectDialog` with `project == null`): optional "Add title" reveal, synopsis autofocus, Create button, default title generation from synopsis.
  - [x] **ProjectList: navigate into the new project after creation.** Flutter pushes `ProjectDetailScreen` for the freshly created project; Compose's `onProjectCreated` callback is a no-op.
  - [x] **Android entry polish (theme + edge-to-edge):** add `androidApp/src/main/res/values/themes.xml` defining `Theme.AlphaThinker` with parent `android:Theme.Material.Light.NoActionBar`, then set `android:theme="@style/Theme.AlphaThinker"` on `<application>` in `androidApp/src/main/AndroidManifest.xml` (currently no theme is declared, so the default action-bar theme is used under Compose). Also call `enableEdgeToEdge()` from `androidx.activity` in `MainActivity.onCreate()` before `setContent { App() }` so Compose controls the system bars. Do these together since both touch the Activity/manifest theme setup.
  - [x] **ProjectList/Detail: surface load/create errors** via snackbar instead of silently clearing the list (`viewModel` currently swallows failures to empty).
  - [x] ProjectDetail - Edit project dialog returns to the project list.  Should remain on project detail. If "clear all" selected on save, reset the filter to default.
  - [x] **ProjectDetail: actually apply the selected filter.** The LazyColumn iterates `project.questions` unfiltered, so Answered/Ignored/Unanswered chips don't filter. Apply the equivalent of `QuestionFilter.apply` (Flutter `question_filter.dart`): unanswered = `isUnanswered`, answered = `isAnswered && !isIgnored`, ignored = `isIgnored`.
  - [x] ProjectDetail and ProjectDetailViewModel should never have to deal with a null project
  - [x] **ProjectDetail: persist question order** across launches (Flutter `PreferenceService.saveQuestionOrder` / `getQuestionOrder`). No equivalent storage/DI plumbing exists in Compose.
  - [x] **ProjectDetail: unanswered ordering + 3-card rotation.** Flutter persists a shuffled per-project question order (PreferenceService), shows only 3 unanswered at a time, and rotates cards in as they're answered/shuffled. Compose has a transient, buggy `questionOrderState` that's never used to order/filter the list.
  - [x] **ProjectDetail: Shuffle button** for unanswered (rotate current visible to end, pull next from front), disabled when only <=3 unanswered. Missing.
  - [x] **ProjectDetail / repository: "Ask later" support.** `ProjectRepository` has no reorder concept; Compose `QuestionItem` has no ask-later callback. Add reorder-to-end of the unanswered order (Flutter `_askLater`).
  - [x] **ProjectDetail: open AnswerDialog on question tap.** `onAnswerClick = { /* TODO */ }` is unimplemented — the core Q&A workspace (view full answer, update answer, save draft) is dead. ViewModel already has `updateAnswer`/`deleteAnswer` but nothing calls them.
  - [x] **AnswerDialog parity:** complete the Compose dialog — autofocus the field, "Ask Later" (saves draft, returns `ask_later`), "Delete Answer" when a complete answer exists (returns `deleted`), and have the detail screen reload on submit/deleted/ask_later (Flutter `_answerQuestion` result handling).
  - [x] **Data: persist `contextId`.** `RoomStorage` hardcodes `contextId = ""` on load (`QuestionEntity` has no column); Flutter model carries it.
- [x] Place Storage interface in the database package
- [x] merge filter and sort functionality into QuestionViewMode (renamed from QuestionFilter), which now applies both filtering and sort (Answered by answer modified/answered date, Ignored by ignored date)
- [x] **Evolve `QuestionGenerator` data contract (LLM-ready, stateless):** new method: recommendTitle(synopsis); `generateInitialQuestions` gains the generated `editableTitle` alongside `synopsis`; `generateFollowUpQuestions` takes the project's `previousQuestions` — answers arrive via `Question.currentAnswer`, skipped questions via `Question.isIgnored` — so a review reads completed answers, drafts, and ignored questions from one list, and generation can dedupe/round off the already-asked count. `ProjectRepository` passes `project.questions` and resolves the initial title.
- [x] **Merge question generators:** combine `MockQuestionGenerator` and `SeedQuestionsGenerator` into one configurable, stateless `HardcodedQuestionGenerator` that mimics the eventual Edge LLM — an initial batch from a merged question pool, then automatic follow-up rounds (configurable per-round counts) until the pool is exhausted. Drops the mock's shared `roundCounter` (cross-project interference) and keeps Lite's seed questions as the base of the pool. Note: Lite's "no automated follow-ups" behavior goes away with this change.
- [x] **Wire `QuestionGenerator` signatures through `ProjectRepository`:** `createProject` resolves the default title via `recommendTitle` and passes `editableTitle` + `synopsis` to `generateInitialQuestions`, shuffling the batch; `updateAnswer` passes `project.questions` as `previousQuestions` to `generateFollowUpQuestions` when the round is resolved. Generation stays inline (awaited) for now — extracting it into dedicated async-task helpers is deferred to the Phase 3 "Generation Task framework", which will restructure `createProject` / `updateAnswer` to persist-then-enqueue anyway.
- [x] **Remove dead generator code:** delete `MockQuestionGenerator.kt` and `FallbackQuestionGenerator.kt` (unreferenced; DI provides `SeedQuestionsGenerator` directly) and point `AppComponent` at the merged generator. The Edge->Lite fallback is reintroduced around the real LLM in Phase 3.
- [x] debug tool: Start a (mostly empty) settings screen, and have a place tools can show up.  create a couple sample projects with a mix of ignored and answered and drafts already popuplated.  Some of the values should be big enough to stretch limits (like a very very long answer) so we can see how the UI behaves with a mix of simple and extreme.
- [x] **Move DB context init into an Application subclass:** add `androidApp/src/main/kotlin/alphainterplanetary/thinker/AlphaThinkerApplication.kt` (`class AlphaThinkerApplication : Application() { override fun onCreate() { initDatabase(this) } }`), register it via `android:name=".AlphaThinkerApplication"` in the manifest, and strip `initDatabase(applicationContext)` out of `MainActivity` so it only does `setContent { App() }`. This makes the Room context available before any DI access and independent of Activity lifecycle. (Later: consider removing the module-global context entirely by passing the platform context into `AppComponent` as a constructor arg, but that needs a common-typed abstraction since KMP can't reference `android.content.Context`.)
- [x] **Verify Room + DI on device/emulator:** install the debug APK and confirm (a) first launch no longer hits the old `NotImplementedError` from `AppDatabaseConstructor`, (b) project/answer data persists across a force-stop/relaunch (Room writes to `alphathinker.db`), (c) the app survives rotation / backgrounding without crashes, and (d) only one `AppDatabase` connection is opened (the `providesDatabase()` lazy singleton in `AppComponent`).
- [x] split viewmodels into their own files
- [x] set up some shared testing objects like InMemoryStorage
- [x] clean up gradle build and kotlin compiler warnings -- resolved as part of the AGP 9 restructuring: `shared/` (KMP library via `com.android.kotlin.multiplatform.library`) + `androidApp/` (thin AGP-built-in-Kotlin entry). Builds warnings-free with `./gradlew build --warning-mode all` (only unfixable JVM `native-access` notices from Gradle internals remain); tests via `./gradlew :shared:allTests`.
- [ ] recreate FallbackQuestionGenerator or equivalent -- we'll need this when an llm is unavailable or the user has turned it off

## Phase 2.6: multiplatform support
- [ ] Set up Compose Multiplatform Web target
- [ ] Set up Compose Multiplatform iOS target
- [ ] Set up Compose Multiplatform Desktop (macOS / JVM) target

## Phase 2.7: UI cleanup
- [ ] Ignored filter disappeared on portrait when we added drafts filter -- perhaps an alternate UI when they don't have enough space?
- [ ] ProjectList: truncate title to one line
- [ ] ProjectDetail: need to truncate title to two lines, synopsis to 5 lines, or make it scrollable.  Need to come up with a way to view the full text of both
- [ ] **AnswerDialog: hide "Ask Later" for completed questions.** Flutter only shows "Ask Later" when the question has no complete answer (`current == null || !current.isComplete`); Compose always shows it, and triggering it on a completed question throws `IllegalStateException` ("Cannot add a draft answer to a question that is already answered") and drops the whole detail screen into the full Error state.
- [ ] Better text instead of "Submit" for adding / editing answers. Let's make the answer dialog have a closed button and a completed toggle or checkbox that moves things from draft to answered.
- [x] new Drafts view mode in the question list (filter `currentAnswer?.isDraft`, default sort latest edits first). 
- [ ] if a dialog close action would cause data to be lost, show a prompt.  Example: type in changes to Edit Project then tap off the dialog area to dismiss (or tap cancel).  Same with answer dialog
- [ ] consider revising how answer drafts work, or, if not, formalizing the behavior.
- [ ] **KMP: swipe-to-ignore / swipe-to-ask-later on question rows**, mimicking the Flutter `Dismissible` behavior in `frontend/lib/widgets/question_item.dart` (via `SwipeableItem`). Per filter: *unanswered* — swipe right = Ask Later (blue background), swipe left = Ignore (red); *answered* — swipe right = Ignore (grey), swipe left = Delete Answer (red); *ignored* — swipe either way = Unignore (green). Also add the background rows with icon+label shown under the card while swiping.
- [ ] probably should be able to unignore a question from the dialog popup, or force unignore before modifying the answer field.
- [ ] ProjectList: is the top-bar Refresh button actually needed? The list already auto-reloads when it re-enters composition (navigation back from detail), and the Error state has its own Retry button — so the Refresh action looks redundant and can likely be removed.
- [ ] ProjectDetail: question filter pills are not aligned with the Questions header.  For some screens we may need them to take less horizontal space as well. 
- [ ] **KMP: synopsis edit affordance.** the synopsis body has no edit affordance (Flutter has an edit IconButton beside "Synopsis:" opening the edit dialog; Compose only reaches it via the top bar).
- [x] **KMP: EditProjectDialog polish.** Refine the Compose edit dialog to match Flutter — 30-char title cap, multiline synopsis autofocus, and optional title reveal for new projects (the create flow itself is in Phase 2.5).
- [ ] **ProjectList: delete a project** with a confirm dialog ("This action cannot be undone."). Add a delete action
- [ ] **ProjectList: edit a project** Add an edit action
- [x] projectdetail: unignore icon — now uses the correct Visibility icon (matches Flutter).
- [ ] figure out how to dismiss question rows programmatically, will probably require a custom impl.  It should look and behave like dismissable but allow button taps to trigger it.
- [ ] Clean up a lot of the hardcoded font size, color, etc options by using a proper theme with named styles
- [ ] Answer dialog: add an affordance to clear the text area
- [ ] **ProjectDetail: per-filter empty message.** Flutter shows "No {filter} questions." when the selected filter has no results; Compose renders an empty list instead.
- [ ] **ProjectDetail: question-list transition.** Flutter wraps the question list in a 300ms `AnimatedSwitcher` keyed on filter + question order; Compose has no cross-fade on filter/reorder changes.

## Phase 2.8: Rounds & Stages UI (pre-LLM)
UI and domain work for the round/stage concept so the experience is ready before LLM integration. No themed stage names — stages are plain numeric values. Depends on the Phase 2.5 generator-merge/interface items for the wrap-up's next-round generation.
- [ ] **Round entity + schema migration:** add a `Round` entity (`roundId`, `projectId` FK, `roundNumber`, `startedAt`, `completedAt?`) and the Room changes as one unit — create the `rounds` table, backfill a round per distinct `(projectId, contextId)` from existing questions, and make `Question.contextId` a `roundId` foreign key to it (one UUID plays both roles). A round is the set of questions surfaced together in a generation batch; creating a project creates Round 1 around the initial questions, and each wrap-up closes the old round and opens the next. Grouping questions by `roundId` is then a real query instead of a reconstruction (see ENG-DESIGN.md "Rounds").
- [ ] **Numeric planning stage:** add `planningStage: Int` to `Project` (persist in Room; default 1). The stage is derived to equal `completedRounds + 1`, advanced by one each time the user wraps up a round. Show it on the project detail header and the project list card.
- [ ] **Stage completion %:** display the current stage's completion as a percentage (resolved / total questions in the current round, where resolved = answered or ignored), alongside the stage number.
- [ ] **Wrap-up step:** add a "Wrap up this round" action on the project detail screen, enabled when all active questions in the current round are resolved (answered or ignored). Tapping it sets `Round.completedAt`, advances the stage, triggers next-round generation via the hardcoded generator (slots into the async `TaskRunner` seam in Phase 3), and plays a small celebratory animation.
- [ ] **Manual wrap trigger:** remove the automatic "all answered -> generate follow-ups" transition in `ProjectRepository.updateAnswer` so next-round generation happens only from the user-initiated wrap-up (auto-advance can return as an optional setting in Phase 3).

## Phase 3: Intelligence Integration (Edge Version)
- [ ] **Generation Task framework:** add `GenerationTask` + in-memory app-scoped `TaskRunner` (`StateFlow`-observable, injected coroutine scope); `createProject` / `updateAnswer` persist immediately and enqueue generation rather than awaiting inline; UI reloads the affected project when its task completes. See ENG-DESIGN.md "Generation Task Framework". Note: this replaces the inline generation calls added to `ProjectRepository` in line 73 — the `TaskRunner` seam lands here (no intermediary helpers needed).
- [ ] **Persist generation tasks + LLM interaction log:** Room tables for task status/progress aligned with the `LLMInteraction` schema (prompt, payload, suggested questions, tool calls, durationMs) so long-running work and its history survive process death and feed the System/Debug workspace.
- [ ] **LLM Interface:** Create the abstraction for the inference engine.
- [ ] **Inference Implementation:** Integrate selected LLM inference solution for the Edge mode (evaluation in progress: ondevice-ai vs LiteRT-LM).
- [ ] **Fallback Mechanism:** Implement the automatic switch from Edge to Lite upon inference failure.
- [ ] Evaluate on-device support; there's ondevice-ai for kmp which can talk to system-installed edge llms (gemini nano, apple foundation) that may be more seamless than litert-lm 
- [ ] Determine if we actually need multiple app flavors, or if we can just fall back to basic mode inside a single binary
- [ ] Add setting to disable LLM usage (Settings screen)
- [ ] Add setting to enable/disable LLM lookup & web search
- [ ] Evaluate Koog (JetBrains KMP AI-agent framework) for agentic lookup + web search tools (research in ENG-DESIGN.md)
- [ ] Integrate Koog tool-calling so the LLM performs lookup / web search only as needed
- [ ] Implement graceful degradation when search is unavailable/offline, and route tool/search results through the LLM interaction log
- [ ] Integrate selected LLM solution for automated question generation
- [ ] Implement "Auto-Archive" logic and app-wide settings
- [ ] Implement background notification for long-running LLM tasks
- [ ] Integrate global questions pool with Edge mode (LLM recommendations + global questions)

## Phase 4: Advanced Features & Export
- [ ] Implement Markdown export functionality
- [ ] Plan and implement the export pipeline (Markdown synthesis -> File System)
- [ ] Implement System/Debug Workspace (LLM Log, Console, and Task Manager); the LLM log should surface any tool calls made during an interaction (tool name, arguments, results, per-call latency)
- [ ] **Export Pipeline:** Implement the Markdown synthesis and file system export.
- [ ] Implement answer revision history UI (list with timestamps)
- [ ] Implement global question pool management (create/edit questions usable across projects)
- [ ] Integrate global question pool with Lite version seed questions and edge version generated questions
- [ ] Allow users to change questions in the answer dialog
- [ ] Allow users to set up a connection to a cloud LLM (OpenAI-compatible API)

## Phase 5: Refinement & UX
- [ ] Prototype and refine "Iterative Question Card" interaction
- [ ] Design and implement navigation strategy for multi-platform (mobile/desktop)
- [ ] Move all strings to Compose MP standards for internationalization

## Phase 6: Testing & Verification
- [ ] **KMP Unit Tests:** Verify business logic and fallback transitions.
- [ ] **Performance Benchmarking:** Measure LLM inference latency and resource impact.

