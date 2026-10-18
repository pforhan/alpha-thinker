# Model Catalog Plan

This plan covers how Alpha Thinker discovers, describes, ranks, and obtains the
**models** used by remote and local engines. It is the companion to
[ENGINE-CONFIG-PLAN.md](ENGINE-CONFIG-PLAN.md): that plan reworks the engines
themselves (how they are modeled, stored, and presented), while this one is
about the models inside an engine.

It is a dedicated tracker for work previously itemized in
[IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) Phase 3.7:

- "Model discovery & curation (Verified / Recommended / Others)" and its
  sub-items (richer metadata, the `latest` alias, `RemoteModelInfo`, curated
  families, the ranking function, source-ready eval output, best-available
  selection, the grouped browser, and the hostability flag).
- "Ollama model pull".
- "Downloaded local models (LiteRT-LM, in-process)".

**Status: not started.** Depends on ENGINE-CONFIG-PLAN.md stages 1–2
(per-instance config), because model state is per engine instance. Its stage
2/3 UI work additionally comes after that plan's stage 3b, which moves
`ModelBrowser` into the Advanced connection editor — build the grouped browser
and the pull affordance there, not in `IntelligenceSheet`.

---

## Problem

Today a model is a bare id plus an optional context length —
`RemoteModelInfo(id, contextLength?)` (`shared/.../engine/RemoteModelCatalog.kt`).
The catalogue (`KoogRemoteModelCatalog`) parses only what it needs to fill that
shape, the browser lists whatever the server returns in one flat `FlowRow`, and
the selected model carries across provider switches. There is no notion of model
quality, no capability signal, no way to pull a missing model, and no place to
manage downloaded local files.

## Decisions (locked)

1. **Verified / Recommended / Others are presentation groupings only** — no
   model is ever hidden.
2. **Two independent axes** — quality and speed — with the
   `good` / `ok` / `discouraged` label *derived on that grid* rather than being
   an independent tier list. A model with no data is `unknown` on either axis
   and is still ranked and shown.
3. **Tiers are seeded from measured evidence** (the eval rows), human-in-the-loop;
   nothing is auto-promoted.
4. **Gather metadata from the source where it exists** — Ollama `POST /api/show`,
   the Anthropic catalogue via Koog; leave null for the OpenAI-compatible path.
5. **Resolve the `latest` alias for display** while keeping the canonical id for
   selection.
6. **Downloads differ by kind** — an Ollama pull is a server-side stream on an
   instance; a downloaded LiteRT model is itself an `engine_instances` row of
   `EngineKind.LocalFile` (see ENGINE-CONFIG-PLAN.md, "Storage").
7. **Hostability is a note, not a filter.**

## Target model

```kotlin
enum class ModelSource { Ollama, OpenAiCompatible, Anthropic, LiteRt }

enum class ModelQuality { Strong, Adequate, Weak, Unknown }
enum class ModelSpeed { Fast, Fine, Slow, Unknown }
enum class ModelTier { Verified, Recommended, Others }

data class RemoteModelInfo(
  val id: String,
  val contextLength: Int? = null,
  val parameterSizeB: Int? = null,
  val quantization: String? = null,
  val format: String? = null,
  val capabilities: Set<String> = emptySet(),
  val source: ModelSource,
)

data class ModelRank(
  val model: RemoteModelInfo,
  val tier: ModelTier,
  val quality: ModelQuality,
  val speed: ModelSpeed,
  val reason: String,
)
```

Plus `ModelProfiles` (family/pattern rules + measured seeds) and `ModelRanker`.

## Data sources

- **Ollama** — `POST /api/tags` then `POST /api/show` for
  `details.parameter_size`, `details.quantization_level`, `details.format`, the
  `capabilities` array, and the window
  (`model_info[<arch>.context_length]`, falling back to
  `details.context_length`). For the `latest` alias, also read
  `model_info["general.size_label"]`, `parameter_count`, `basename`, `finetune`.
- **Anthropic** — Koog `LLModel.capabilities` / `maxOutputTokens` from the
  bundled catalogue.
- **OpenAI-compatible** — no standard; leave the new fields null.
- **Measured rows** — `docs/model-evals/*.json`, produced by
  `tools/eval_ollama_models.py` (the reference for both the `latest` resolution
  and the footprint estimate).

---

## Stages

### Stage 1 — Model metadata & identity

- [ ] **Gather richer metadata (Ollama):** extend `OllamaShow` /
      `OllamaTagDetails` parsing in `RemoteModelCatalog` to capture
      `details.parameter_size`, `details.quantization_level`, `details.format`,
      and the `capabilities` array. (Anthropic: Koog
      `LLModel.capabilities` / `maxOutputTokens`; OpenAI-compatible: null.)
- [ ] **Resolve the `latest` alias for display:** `latest` is only a tag alias
      (`llama3.2:latest` is really `llama3.2:3b`), so distill the real identity
      for the UI while keeping the canonical id for selection — when the tag is
      `latest`, show the resolved size (e.g. `llama3.2:3b`, or `llama3.2 · 3B`)
      instead of the bare alias.
- [ ] **Extend `RemoteModelInfo`** with `parameterSizeB`, `quantization`,
      `format`, `capabilities`, and `source: ModelSource`; update
      mappers/tests.

**Proof:** `./gradlew :shared:allTests`.

### Stage 2 — Curation

- [ ] **Curated families + capability gate:** add `ModelProfiles.kt` with
      family/pattern rules for instruction-following + JSON + summarization.
      Define `Verified`, `Recommended`, `Others` (presentation only). Consider a
      small `Verified` subset (starred) within `Recommended`, or a separate
      badge.
      **Note (measured, 24 models):** the evidence favors two independent axes —
      quality (strong ≥98, adequate ≥85, else weak) and speed (average suite
      time: fast <10s, fine <35s, else slow) — with `good`/`ok`/`discouraged`
      derived as a *label on that grid* (good = strong×fast, ok = strong×fine or
      adequate×fast). A model with no data lands in `unknown` on either axis and
      is still ranked and shown.
- [ ] **Ranking function:** `ModelRanker.rank(...)` returns ordered models with
      `ModelRank` (tier/group/reason). Prefer declared JSON/structured-output/
      completion, favor small–medium params + Q4–Q6, penalize very large/unknown.
      Unit tests with representative samples.
- Tiers are seeded from the eval harness's `report` output: the "Emit
  source-ready data from eval rows" task under the harness in
  IMPLEMENTATION-PLAN.md Phase 3.7 adds the snippet emitter that `ModelProfiles`
  consumes, so the curated list is generated from measured rows instead of
  hand-transcribed.
- [ ] **Best-available selection:** in `SettingsViewModel.refreshRemoteModels()`,
      if the current selection is missing or unset, auto-select the top of
      `Recommended` (else the top of `Others`) and apply the known context
      window.
- [ ] **Grouped browser UI:** update `ModelBrowser` to show
      `Verified/Recommended` first (Verified could be starred or outlined), then
      `Others`, each with a header and chips showing `id · size · context` +
      short reason. Preserve tap-to-select.
      (Lands in the connection editor moved by ENGINE-CONFIG-PLAN.md stage 3b;
      that plan owns where the browser lives, this one owns what it shows.)
- [ ] **Hostability flag:** estimate each model's memory footprint (on-disk size,
      else `parameter_count × bytes/param`) and flag those that won't fit the
      device's budget next to the size/context chips — a note, not a filter.
      (`tools/eval_ollama_models.py --device-memory-gb` is the reference for the
      estimate and the `>NG B` flag.)
- [ ] **PRD updates:** PRD §4.3 already states the Verified / Recommended /
      Others groupings, best-available auto-selection, and Ollama pull
      (`PRD.md:63`); keep it in sync as the `latest` alias resolution and the
      hostability flag land.

**Proof:** `./gradlew :shared:allTests`.

### Stage 3 — Downloads & local models

- [ ] **Ollama model pull:** add `OllamaModelPuller` calling `POST /api/pull`
      with streaming NDJSON; expose a pull with progress/cancel/background; add a UI
      affordance to pull an ID (or show "Pull" for known IDs when missing);
      update the installed list on completion. Use a task to track the ongoing activity.
      log interesting info about the download (start, end, cancelled, etc)
- [ ] **Downloaded local models (LiteRT-LM, in-process):** Koog's official
      `LiteRTLLMClient` on Android; a small JVM `LLMClient` adapter over
      `litertlm-jvm` for desktop; a model download + management UI. Use the same
      progress dialogs and tasks and logging functionality as in the above task.
- [ ] **Track context length** as models are selected and downloaded.
- [ ] **Make the Local tier real:** once this lands, ENGINE-CONFIG-PLAN.md
      stage 3's Local placeholder rows become enabled, and the downloaded
      models are `EngineKind.LocalFile` instance rows rendered under Local
      through the same list component (that plan's stage 3b).
      Platform-gate it (web/wasm on-device stays deferred).

Note: the LiteRT client may require a beta version of Koog (see
IMPLEMENTATION-PLAN.md Phase 5, "Revisit a newer Koog release when one lands").

**Proof:** `./gradlew :shared:allTests`; a manual pull against a local Ollama.

---

## Blast radius

- `RemoteModelCatalog` / `RemoteModelInfo` / `KoogRemoteModelCatalog`;
  `SettingsViewModel.refreshRemoteModels` / `selectRemoteModel`; `ModelBrowser`
  (`ui/chrome/EngineOptions.kt`); new `ModelProfiles` / `ModelRanker`; new
  download managers; PRD §4.3 wording.
- Tests: the `RemoteModelCatalog` cases and `SettingsViewModelTest`.
- Stage 3 adds the first real "download" UI, which shares its progress/cancel
  component across the Ollama pull and local downloads.

## Out of scope

- Auto-promotion of tiers between evaluation batches (human-in-the-loop).
- Hiding models.
- Pull support for Anthropic / OpenAI-compatible endpoints (neither exposes a
  pull API here).
- Web (wasm) on-device — WebLLM / LiteRT JS interop remains its own roadmap item
  (IMPLEMENTATION-PLAN.md Phase 3.7).
- The actual **Alpha Interplanetary** free backend: this plan consumes its model
  catalogue, but the backend itself is a separate roadmap item, tracked in
  ENGINE-CONFIG-PLAN.md as a disabled Free-tier placeholder.
