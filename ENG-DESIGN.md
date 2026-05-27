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
- [x] Target Stack: Flutter/Dart (UI) + KMP (Logic)
- [ ] Research performance of local storage options (SQLite, NoSQL) across platforms.

### TODO: Core Data Architecture
- [ ] See if there's a way to directly draw a link between questions, so that if an answer is changed we can invalidate downstream questions and answers
- [ ] Design a technology-neutral data schema for Projects and Questions.
- [ ] Define the interface for LLM integration to support swapping providers or local engines.

### TODO: LLM Inference Strategy
- [ ] Research "Edge LLM" execution models for mobile/desktop.
- [ ] Define fallback behavior for low-resource devices.
- [ ] Benchmark initial prompt latency vs. iterative follow-up performance.

### TODO: UI/UX Architecture
- [ ] Prototype the "Iterative Question Card" interaction.
- [ ] Design a navigation strategy that scales from mobile to desktop.
- [ ] Plan the export pipeline (Markdown synthesis -> File System).
