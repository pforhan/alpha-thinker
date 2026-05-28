# Alpha Thinker Implementation Roadmap

## 🏗️ Core Infrastructure
- [ ] **Project Scaffolding:** Initialize Gradle-based multi-module project (KMP + Flutter).
- [ ] **Pigeon Setup:** Define the initial communication contract between KMP and Flutter.
- [ ] **Persistence Layer:** Implement Room KMP with the proposed schema (Project, Question, Answer, LLMInteraction).

## 🧠 KMP Engine Logic
- [ ] **Domain Logic Implementation:** Implementing core project management and question-answering flows.
- [ ] **Lite Implementation:** Implement the hardcoded 20 Seed Questions logic for fallback/Lite mode.
- [ ] **LLM Interface:** Create the abstraction for the inference engine.
- [ ] **Inference Implementation:** Integrate LiteRT-LM and MediaPipe for the Edge mode.
- [ ] **Fallback Mechanism:** Implement the automatic switch from Edge to Lite upon inference failure.

## 📱 Flutter UI
- [ ] **Project List View:** Implement project overview and creation.
- [ ] **Planning Workspace:** Implement the interactive "Question Card" UI.
- [ ] **State Management:** Implement the BLoC/Riverpod architecture to observe KMP updates.
- [ ] **Export Pipeline:** Implement the Markdown synthesis and file system export.

## 🧪 Testing & Verification
- [ ] **KMP Unit Tests:** Verify business logic and fallback transitions.
- [ ] **Integration Tests:** Verify the Pigeon bridge and end-to-end data flow.
- [ ] **Performance Benchmarking:** Measure LLM inference latency and resource impact.
