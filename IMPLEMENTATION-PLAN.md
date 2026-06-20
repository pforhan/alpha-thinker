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
- [ ] Present questions to be answered in a random order.
- [ ] Implement dynamic question rotation (unanswered cards replace answered cards)
- [x] Implement question archiving functionality (manual deactivation)
- [ ] Implement "Answered Questions" and "Archived Questions" views/filters. Just include a short blurb from the answer; allow opening for full details.
- [ ] Implement answer revision history UI (list with timestamps)
- [x] **Domain Logic Implementation:** Implementing core project management and question-answering flows.
- [ ] **Lite Implementation:** Implement the hardcoded 20 Seed Questions logic for fallback/Lite mode.
- [ ] Implement global question pool management (create/edit questions usable across projects)
- [ ] Integrate global pool with Lite mode (seed questions + global questions)

## Phase 2.5: cleanup
- [ ] Make better use of Room annotations, e.g. @Relation on our DB objects to simplify RoomStorage
- [ ] Change filenames with "pigeon" in them to something more appropriate
- [ ] repackage files from com.pforhan.alphathinker to alphainterplanetary.alphathinker
- [ ] reformat files to 2-space indent

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

