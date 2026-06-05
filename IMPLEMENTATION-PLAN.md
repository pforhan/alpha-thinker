# Implementation Plan: Alpha Thinker

This document tracks the specific engineering tasks required to move from design to a functional product.

## Phase 1: Foundation & Infrastructure
- [ ] Set up Flutter project structure
- [ ] Implement KMP core logic layer
- [ ] Configure Pigeon for Flutter/KMP communication
- [ ] Implement Room KMP persistence layer
- [ ] Implement a simple script to transform Room/Pigeon models (initial alternative to KSP)
- [ ] Implement Project, Question, and Answer entities in the database
- [ ] **Project Scaffolding:** Initialize Gradle-based multi-module project (KMP + Flutter).

## Phase 2: Core User Experience (Lite Version)
- [ ] Implement Project creation and listing views
- [ ] Implement the Question/Answer workspace
- [ ] Implement seed question extension UI (text area)
- [ ] Implement answer revision history UI (list with timestamps)
- [ ] **Domain Logic Implementation:** Implementing core project management and question-answering flows.
- [ ] **Lite Implementation:** Implement the hardcoded 20 Seed Questions logic for fallback/Lite mode.

## Phase 3: Intelligence Integration (Edge Version)
- [ ] Integrate LiteRT-LM for automated question generation
- [ ] Implement "Auto-Archive" logic and app-wide settings
- [ ] Implement background notification for long-running LLM tasks
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
