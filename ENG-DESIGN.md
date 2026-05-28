# ENG-DESIGN.md

## Engineering Strategy & Investigations

This document outlines the technical investigations and design decisions required to implement Alpha Thinker. No final tech stack has been selected yet.

## Target Architecture (v1.0)
This iteration proposes a clear separation of concerns:
1. **Frontend UI:** Flutter/Dart for a single, unified, and cross-platform user experience.
2. **Core Logic/Engine:** Kotlin Multiplatform (KMP) for handling core domain logic, data persistence, and heavy computational lifting.

This model allows the shared KMP layer to be the 'source of truth' for the application's business logic, decoupling it from UI platform specifics.

### Interoperability Layer
To ensure robust communication between Flutter/Dart and the KMP engine, we will use **Pigeon Flutter Bindings**. Pigeon will generate the necessary communication boilerplate code, ensuring type safety and predictable message passing across the language boundaries.

### Data Persistence Layer: Room/Android Architecture Components
Given the KMP logic core, the ideal solution for persistent storage is **Room KMP** (or a similar KMP wrapper for SQLite). This will provide a robust, type-safe abstraction over local persistence for our core entities (Projects, Questions, Answers).

Furthermore, to minimize boilerplate, we should implement a custom KSP (Kotlin Symbol Processing) step. This custom processor will observe our pigeon data model definitions and automatically generate necessary classes annotated with Room annotations, extension functions for conversions, and companion objects, preventing manual duplication and keeping the domain model clean.

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
    *   `text` (String: The full question text, either seed, user-input, or LLM-generated.)
    *   `isArchived` (Boolean: Tracks manual deactivation.)
    *   `createdAt` (Timestamp: When the question was first surfaced.)

3. **Answer:**
    *   `answerId` (Unique ID)
    *   `questionId` (Foreign Key: Links to the parent Question.)
    *   `responseText` (String: The user's written answer.)
    *   `answeredAt` (Timestamp: When the answer was filled. Any older answers with the same quetionId are older revisions)

4. **LLMInteraction:**
    *   `llmInteractionId` (Unique ID)
    *   `projectId` (Foreign Key: Links to the parent Project.)
    *   `promptUsed` (String: The full input prompt sent to the LLM.)
    *   `generationPayload` (JSON/Text: The LLM's raw output or a synthesized structured prompt.)
    *   `suggestedQuestions` (JSON/Text: Any structured list of new questions derived by the LLM.)

### TODO: LLM Inference Strategy
- [ ] Research "Edge LLM" execution models for mobile/desktop.
- [ ] Define fallback behavior for low-resource devices.
- [ ] Benchmark initial prompt latency vs. iterative follow-up performance.

### TODO: UI/UX Architecture
- [ ] Prototype the "Iterative Question Card" interaction.
- [ ] Design a navigation strategy that scales from mobile to desktop.
- [ ] Plan the export pipeline (Markdown synthesis -> File System).
