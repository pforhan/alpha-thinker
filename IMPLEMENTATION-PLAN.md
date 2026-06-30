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
- [ ] Implement answer revision history UI (list with timestamps)
- [ ] Implement global question pool management (create/edit questions usable across projects)
- [ ] Integrate global pool with Lite mode (seed questions + global questions)

## Phase 2.5: cleanup
- [x] break up bracket storms with multiple methods to make the code easier to read.
- [x] break up project_detail_screen.dart further, moving the dialog out at least
- [ ] auto archive functionality should be for project updates, not question or answer updates.
- [ ] clean up Answer.isAnswered
- [ ] Make better use of Room annotations, e.g. @Relation on our DB objects to simplify RoomStorage
- [ ] Change filenames with "pigeon" in them to something more appropriate
- [ ] repackage files from com.pforhan.alphathinker to alphainterplanetary.alphathinker
- [ ] reformat files to 2-space indent
- [ ] make a QuestionFilter enum and gather any related strings in it to provide type safety
- [ ] Rename LLMIntegration to QuestionGenerator
- [ ] Make a QuestionDAO since questions and answers can be updated independently of a project.
- [ ] Better text instead of "Submit" for adding / editing answers
- [ ] need to integrate MockLLMIntegration and SeedQuestionsLLMIntegration because they're obviously doing the same thing but differently
- [ ] consider revising how answer drafts work, or, if not, formalizing the behavior.
- [ ] figure out how to dismiss question rows programmatically, will probably require a custom impl.  It should look and behave like dismissable but allow button taps to trigger it.
- [ ] probably should be able to unignore a question from the dialog popup, or force unignore before modifying the answer field.

## Phase 3: Intelligence Integration (Edge Version)
- [ ] Integrate LiteRT-LM for automated question generation
- [ ] Implement "Auto-Archive" logic and app-wide settings
- [ ] Implement background notification for long-running LLM tasks
- [ ] Integrate global questions pool with Edge mode (LLM recommendations + global questions)
- [ ] **LLM Interface:** Create the abstraction for the inference engine.
- [ ] **Inference Implementation:** Integrate LiteRT-LM and MediaPipe for the Edge mode.
- [ ] **Fallback Mechanism:** Implement the automatic switch from Edge to Lite upon inference failure.

## Phase 4: Advanced Features & Export
- [ ] Implement Markdown export functionality
- [ ] Plan and implement the export pipeline (Markdown synthesis -> File System)
- [ ] Implement System/Debug Workspace (LLM Log, Console, and Task Manager)
- [ ] **Export Pipeline:** Implement the Markdown synthesis and file system export.

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

