# ENG-DESIGN.md

## Engineering Strategy & Investigations

This document outlines the technical investigations and design decisions required to implement Alpha Thinker. No final tech stack has been selected yet.

### TODO: Tech Stack Evaluation
- [ ] Evaluate **Kotlin Multiplatform (KMP)** for shared logic with platform-native UIs.
- [ ] Evaluate **Flutter/Dart** for cross-platform UI and shared logic.
- [ ] Assess local LLM inference library support (e.g., Llama.cpp, MediaPipe) for each candidate stack.
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
