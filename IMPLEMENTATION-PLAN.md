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
- [ ] Convert to KMP / Compose, away from flutter
  - [ ] **ProjectList: wire up the add-project button.** `ProjectListScreen` sets `showCreateDialog = true` but never renders a dialog, so the Add FAB and "Create your first project" button do nothing. Add a new-project dialog (Flutter `EditProjectDialog` with `project == null`): optional "Add title" reveal, synopsis autofocus, Create button, default title generation from synopsis.
  - [ ] **ProjectList: navigate into the new project after creation.** Flutter pushes `ProjectDetailScreen` for the freshly created project; Compose's `onProjectCreated` callback is a no-op.
  - [ ] **ProjectList: long-press to delete a project** with a confirm dialog ("This action cannot be undone."). Not present in Compose at all.
  - [ ] **ProjectList/Detail: surface load/create errors** via snackbar instead of silently clearing the list (`viewModel` currently swallows failures to empty).
  - [ ] **ProjectDetail: actually apply the selected filter.** The LazyColumn iterates `project.questions` unfiltered, so Answered/Ignored/Unanswered chips don't filter. Apply the equivalent of `QuestionFilter.apply` (Flutter `question_filter.dart`): unanswered = `isUnanswered`, answered = `isAnswered && !isIgnored`, ignored = `isIgnored`.
  - [ ] **ProjectDetail: unanswered ordering + 3-card rotation.** Flutter persists a shuffled per-project question order (PreferenceService), shows only 3 unanswered at a time, and rotates cards in as they're answered/shuffled. Compose has a transient, buggy `questionOrderState` that's never used to order/filter the list.
  - [ ] **ProjectDetail: persist question order** across launches (Flutter `PreferenceService.saveQuestionOrder` / `getQuestionOrder`). No equivalent storage/DI plumbing exists in Compose.
  - [ ] **ProjectDetail: Shuffle button** for unanswered (rotate current visible to end, pull next from front), disabled when only <=3 unanswered. Missing.
  - [ ] **ProjectDetail / repository: "Ask later" support.** `ProjectRepository` has no reorder concept; Compose `QuestionItem` has no ask-later callback. Add reorder-to-end of the unanswered order (Flutter `_askLater`).
  - [ ] **ProjectDetail: open AnswerDialog on question tap.** `onAnswerClick = { /* TODO */ }` is unimplemented — the core Q&A workspace (view full answer, update answer, save draft) is dead. ViewModel already has `updateAnswer`/`deleteAnswer` but nothing calls them.
  - [ ] **AnswerDialog parity:** complete the Compose dialog — autofocus the field, "Ask Later" (saves draft, returns `ask_later`), "Delete Answer" when a complete answer exists (returns `deleted`), and have the detail screen reload on submit/deleted/ask_later (Flutter `_answerQuestion` result handling).
  - [ ] **QuestionItem parity:** add swipe-to-ignore (left) and swipe-to-ask-later (right) for unanswered (Flutter `SwipeableItem`/`Dismissible`), and "Ask Later" action button (rotate_left icon) on unanswered items. Currently Compose only shows a single Ignore/Unignore icon, and its icon-selection logic is wrong (uses Edit icon for unignore; calls `onIgnore` on unanswered regardless of state).
  - [ ] **Data: persist `contextId`.** `RoomStorage` hardcodes `contextId = ""` on load (`QuestionEntity` has no column); Flutter model carries it.
- [ ] need to integrate/combine MockLLMIntegration and SeedQuestionsLLMIntegration because they're obviously doing the same thing but differently
- [ ] **Android entry polish (theme + edge-to-edge):** add `composeApp/src/androidMain/res/values/themes.xml` defining `Theme.AlphaThinker` with parent `android:Theme.Material.Light.NoActionBar`, then set `android:theme="@style/Theme.AlphaThinker"` on `<application>` in `composeApp/src/androidMain/AndroidManifest.xml` (currently no theme is declared, so the default action-bar theme is used under Compose). Also call `enableEdgeToEdge()` from `androidx.activity` in `MainActivity.onCreate()` before `setContent { App() }` so Compose controls the system bars. Do these together since both touch the Activity/manifest theme setup.
- [ ] **Move DB context init into an Application subclass:** add `composeApp/src/androidMain/kotlin/alphainterplanetary/thinker/AlphaThinkerApplication.kt` (`class AlphaThinkerApplication : Application() { override fun onCreate() { initDatabase(this) } }`), register it via `android:name=".AlphaThinkerApplication"` in the manifest, and strip `initDatabase(applicationContext)` out of `MainActivity` so it only does `setContent { App() }`. This makes the Room context available before any DI access and independent of Activity lifecycle. (Later: consider removing the module-global context entirely by passing the platform context into `AppComponent` as a constructor arg, but that needs a common-typed abstraction since KMP can't reference `android.content.Context`.)
- [ ] **Verify Room + DI on device/emulator:** install the debug APK and confirm (a) first launch no longer hits the old `NotImplementedError` from `AppDatabaseConstructor`, (b) project/answer data persists across a force-stop/relaunch (Room writes to `alphathinker.db`), (c) the app survives rotation / backgrounding without crashes, and (d) only one `AppDatabase` connection is opened (the `providesDatabase()` lazy singleton in `AppComponent`).

## Phase 2.6: multiplatform support
- [ ] Set up Compose Multiplatform Web target
- [ ] Set up Compose Multiplatform iOS target
- [ ] Set up Compose Multiplatform Desktop (macOS / JVM) target

## Phase 2.7: UI cleanup
- [ ] Better text instead of "Submit" for adding / editing answers
- [ ] consider revising how answer drafts work, or, if not, formalizing the behavior.
- [ ] figure out how to dismiss question rows programmatically, will probably require a custom impl.  It should look and behave like dismissable but allow button taps to trigger it.
- [ ] probably should be able to unignore a question from the dialog popup, or force unignore before modifying the answer field.
- [ ] **KMP: fix back navigation + synopsis edit affordance.** `ProjectDetailScreen` top-app-bar `navigationIcon` uses the Edit icon (should be a Back arrow); the synopsis body has no edit affordance (Flutter has an edit IconButton beside "Synopsis:" opening the edit dialog; Compose only reaches it via the top bar).
- [ ] **KMP: EditProjectDialog polish.** Refine the Compose edit dialog to match Flutter — 30-char title cap, multiline synopsis autofocus, and optional title reveal for new projects (the create flow itself is in Phase 2.5).

## Phase 3: Intelligence Integration (Edge Version)
- [ ] Evaluate on-device support; there's ondevice-ai for kmp which can talk to system-installed edge llms (gemini nano, apple foundation) that may be more seamless than litert-lm 
- [ ] Determine if we actually need multiple flavors, or if we can just fall back to basic mode inside a single binary
- [ ] Add setting to disable LLM usage (Settings screen)
- [ ] Add setting to enable/disable LLM lookup & web search
- [ ] Evaluate Koog (JetBrains KMP AI-agent framework) for agentic lookup + web search tools (research in ENG-DESIGN.md)
- [ ] Integrate Koog tool-calling so the LLM performs lookup / web search only as needed
- [ ] Implement graceful degradation when search is unavailable/offline, and route tool/search results through the LLM interaction log
- [ ] Integrate selected LLM solution for automated question generation
- [ ] Implement "Auto-Archive" logic and app-wide settings
- [ ] Implement background notification for long-running LLM tasks
- [ ] Integrate global questions pool with Edge mode (LLM recommendations + global questions)
- [ ] **LLM Interface:** Create the abstraction for the inference engine.
- [ ] **Inference Implementation:** Integrate selected LLM inference solution for the Edge mode (evaluation in progress: ondevice-ai vs LiteRT-LM).
- [ ] **Fallback Mechanism:** Implement the automatic switch from Edge to Lite upon inference failure.

## Phase 4: Advanced Features & Export
- [ ] Implement Markdown export functionality
- [ ] Plan and implement the export pipeline (Markdown synthesis -> File System)
- [ ] Implement System/Debug Workspace (LLM Log, Console, and Task Manager); the LLM log should surface any tool calls made during an interaction (tool name, arguments, results, per-call latency)
- [ ] **Export Pipeline:** Implement the Markdown synthesis and file system export.
- [ ] Implement answer revision history UI (list with timestamps)
- [ ] Implement global question pool management (create/edit questions usable across projects)
- [ ] Integrate global question pool with Lite version seed questions and edge version generated questions

## Phase 5: Refinement & UX
- [ ] Prototype and refine "Iterative Question Card" interaction
- [ ] Design and implement navigation strategy for multi-platform (mobile/desktop)
- [ ] Develop custom KSP processor for Room/Pigeon synchronization (Deferred)
- [ ] **State Management:** Implement the BLoC/Riverpod architecture to observe KMP updates.

## Phase 6: Testing & Verification
- [ ] **KMP Unit Tests:** Verify business logic and fallback transitions.
- [ ] **Integration Tests:** Verify the Pigeon bridge and end-to-end data flow.
- [ ] **Performance Benchmarking:** Measure LLM inference latency and resource impact.

## Phase 7: Backend Integration & Deployment (Low Priority)
- [ ] Implement a real backend service to replace `InMemoryProjectService` for Web
- [ ] Unify backend connection strategy for both native and web apps
- [ ] Add Gradle/scripting support to launch backend and frontend concurrently

