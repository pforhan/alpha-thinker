# Engine core

Small, stateless primitives for turning project Q&A into prompt context and summaries.

## When you want to…

### Render or budget the planning transcript
- **`PlanningContext`** — render questions with their answer state (`line`/`render`), estimate/budget/trim transcripts to a model window (`estimateTokens`, `budgetTokens`, `nearLimit`, `trim`), and prepend prior `SummarizedPhase`s into context. Pure formatting/budgeting; it does not select phases or call an engine.

### Represent a phase for summarization or compaction
- **`PhaseQuestions`** (`model`) is the cheap phase-content unit (questions/rounds/stats).  
- **`PhaseTranscript`** is the *rendered, materialized* input for a model: a phase’s summarizable questions plus `transcript` text and `estimatedTokens`. Build with `PhaseTranscript.from(phaseQuestions, render)` or `Project.phaseTranscripts(...)`. Convert a produced summary back into prompt context with `toSummarizedPhase(...)`.
- **`SummarizedPhase`** is the *output*: summary text for a phase and the `coversQuestionIds` it replaces in a rendered transcript.

### Summarize past phases (and not confuse it with the engine call)
- **`PhaseSummarization`** — shared orchestration: selects which phase transcripts to summarize (`compactionTranscripts` / `exportTranscripts`), and runs summarization via the engine (`summarize` / `summarizeOrNull`) with consistent best-effort/cancellation behavior. Use this when you need “summarize phase(s) for compaction/export.”
- **`PlanningEngine.summarizePriorAnswers(...)`** — low-level engine capability: given one phase’s already-rendered `transcript` string, ask *that engine* to write a summary and return the text (gated by `PlanningEngine.canSummarize`). It does **not** select phases, render transcripts, manage budgets, or build a `SummarizedPhase`; `PhaseSummarization` (and callers like the repository) own that workflow around it.

### Generate planning content
- **`PlanningEngine`** — stateless contract for title recommendation and question generation (plus optional `summarizePriorAnswers`), with `source`, `contextWindowTokens`, and lifecycle. Implementations/decorators provide the actual behavior (e.g. offline/LLM, logging, delays).
- **`QuestionBatch`** — result of a question-generation round (`questions`, `done`).
- **`PlanningEngineSelector`** — resolves the currently active engine.
- **`ForwardingPlanningEngine`** — base for decorators that forward engine identity/capabilities.
