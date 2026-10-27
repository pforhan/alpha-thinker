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
7. **Verification is part of the download by default** — for `ModelFileDownload`,
   treat download → checksum → extraction as phases of the same task (log phases,
   allocate progress across them, only mark succeeded after verification passes).
   `ModelVerification` remains a separate `TaskKind` only for explicit
   re-verification of already-installed models (not part of normal download
   completion). For `OllamaModelPull`, verification continues as phases reported
   by the server stream.
8. **Hostability is a note, not a filter.**

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
- **Measured rows** — `model-evals/*.json`, produced by
  `../tools/eval_ollama_models.py` (the reference for both the `latest` resolution
  and the footprint estimate).

---

## Stages

### Stage 1 — Model metadata & identity

- [x] **Gather richer metadata (Ollama):** extend `OllamaShow` /
      `OllamaTagDetails` parsing in `RemoteModelCatalog` to capture
      `details.parameter_size`, `details.quantization_level`, `details.format`,
      and the `capabilities` array. (Anthropic: Koog
      `LLModel.capabilities` / `maxOutputTokens`; OpenAI-compatible: null.)
- [x] **Resolve the `latest` alias for display:** `latest` is only a tag alias
      (`llama3.2:latest` is really `llama3.2:3b`), so distill the real identity
      for the UI while keeping the canonical id for selection — when the tag is
      `latest`, show the resolved size (e.g. `llama3.2:3b`, or `llama3.2 · 3B`)
      instead of the bare alias.
- [x] **Extend `RemoteModelInfo`** with `parameterSizeB`, `quantization`,
      `format`, `capabilities`, and `source: ModelSource`; update
      mappers/tests.

**Landed:** `RemoteModelInfo` also carries `displayName` (the resolved alias),
`maxOutputTokens`, `sizeLabel`, `parameterCount`, and `sizeBytes` — the inputs
stage 2's footprint estimate needs — and `ModelSource` lives in a file of its
own. The alias resolution and the size parsing are `ModelIdentity.kt`; the
browser chip shows the resolved name while the id stays canonical.

**Proof:** `./gradlew :shared:allTests`.

### Stage 2 — Curation

- [x] **Curated families + capability gate:** add `ModelProfiles.kt` with
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
- [x] **Ranking function:** `ModelRanker.rank(...)` returns ordered models with
      `ModelRank` (tier/group/reason). Prefer declared JSON/structured-output/
      completion, favor small–medium params + Q4–Q6, penalize very large/unknown.
      Unit tests with representative samples.
- Tiers are seeded from the eval harness's `report` output: the "Emit
  source-ready data from eval rows" task under the harness in
  IMPLEMENTATION-PLAN.md Phase 3.7 adds the snippet emitter that `ModelProfiles`
  consumes, so the curated list is generated from measured rows instead of
  hand-transcribed.
- [x] **Best-available selection:** in `SettingsViewModel.refreshRemoteModels()`,
      if the current selection is missing or unset, auto-select the top of
      `Recommended` (else the top of `Others`) and apply the known context
      window.
- [x] **Grouped browser UI:** update `ModelBrowser` to show
      `Recommended`, `Slower`, `Adequate Quality` then
      `Others` then `Discouraged`, each with a header and chips showing `id · ` +
      short reason. Preserve tap-to-select.
      (Lands in the connection editor moved by ENGINE-CONFIG-PLAN.md stage 3b;
      that plan owns where the browser lives, this one owns what it shows.)

**Proof:** `./gradlew :shared:allTests`.

### Stage 3 — Downloads & local models

- [x] **PRD updates:** PRD §4.3 already states the Verified / Recommended /
      Others groupings — update these to what we have in code. Also include
      best-available auto-selection, and Ollama pull plans (Stage 3)
      (`PRD.md:63`); keep it in sync as the `latest` alias resolution and the
      hostability flag land.
- [x] **Download infrastructure (task system):** Extend the existing task
      infrastructure (`TaskRunner`, `ActivityLogger`, `TaskManager`) to be
      download-aware (no parallel systems).
  - [x] Add `Download` to `ConcurrencyGroup` (downloads are I/O-heavy; do not
        block engine work; tune concurrency per platform).
  - [x] Add download task kinds to `TaskKind` (`OllamaModelPull`,
        `ModelFileDownload`, `ModelVerification`) with `group = ConcurrencyGroup.Download`
        and update `TaskLabels` for titles/progress.
  - [x] Extend `GenerationTask` with optional download metadata (backwards
        compatible): `modelId`, `source` (e.g. `"ollama"`, `"huggingface"`,
        `"litert"`), `bytesTotal`, `bytesDone`, `throughputBps`, `downloadId`.
        Add `TaskRunner.setDownloadProgress(taskId, bytesDone, bytesTotal, throughputBps?)`
        to update fraction `progress` and metadata.
- [x] **Download abstractions:** Create a `downloads/` package with interfaces
      to enable testing and swappable implementations.
  - [x] Define `ModelDownloader` (`downloads()`, `pullOllama(modelId, baseUrl)`,
        `downloadModel(spec)`, `cancel(handleId)`), `DownloadHandle(id, taskId)`,
        `DownloadState` (handle/task/kind/status/modelId/bytes/progress/message/source),
        `DownloadKind` (`OllamaPull`, `LocalFile`), `DownloadStatus` (`Queued`,
        `Running`, `Paused`, `Succeeded`, `Failed`, `Cancelled`).
- [x] **Ollama pull client:** Add `OllamaPullClient` using `KoogHttpClient` for
      streaming NDJSON from `POST /api/pull` with `{"model": ..., "stream": true}`.
      Normalize base URL like `RemoteModelCatalog` (strip `/v1`). Parse status
      messages (`status`, `digest`, `total`, `completed`, `error`), map phases
      (manifest/pulling/verifying/writing/removing/success/error), track bytes/
      progress, support cancellation via stream closure, log via
      `ActivityLogger.taskContext(taskId, projectId = null)`, and refresh
      `RemoteModelCatalog` on completion (optionally auto-select).
- [x] **ModelDownloadManager + DI wiring:** Implement `ModelDownloader`
      that enqueues tasks via `TaskRunner`; wire `OllamaPullClient`,
      `ModelFileTransferClient`, and `ModelDownloader` into `AppComponent`.
      Expose actions via `EngineViewModel` or a `DownloadViewModel`.
- [ ] **Ollama model pull (curated):** Only present strong quality models from
      `model-evals/all.md` in the UI (no full browser). Implement end-to-end
      pull support for curated Ollama models via the download infrastructure.
  - [ ] **Curated source of truth:** Derive the curated Ollama list from
        `model-evals/all.md` (via `ModelProfiles`/eval artifacts), filtering to
        strong-quality candidates. Track installed vs pullable state against
        `RemoteModelCatalog` results.
  - [ ] **Connection UI:** Update `ConnectionDialog` (Advanced) to render the
        curated list with quality/speed/size and hostability notes. Show a
        "Pull" affordance for uninstalled models, with inline progress/cancel
        and automatic refresh on completion.
  - [ ] **Pull execution:** Use `ModelDownloadManager.pullOllama(modelId, baseUrl)`
        to enqueue an `OllamaModelPull` task via `TaskRunner`. Stream NDJSON via
        `OllamaPullClient` (`POST /api/pull` with `stream: true`), treat server
        phases (`pulling manifest`, `pulling <digest>`, `verifying sha256 digest`,
        `writing manifest`, removing unused layers, etc.) as phases of the same
        task (verification included), track `bytesDone`/`bytesTotal`/`progress`,
        and log via `ActivityLogger.taskContext(taskId, projectId = null)`.
  - [ ] **Lifecycle & refresh:** Support cancellation (stream closure),
        background execution (app scope), and error handling. On success,
        refresh `RemoteModelCatalog.list(Ollama, ...)`, update installed state,
        apply context length if known, and consider best-available selection if
        the pulled model was the requested target. On failure/cancel, surface
        messages and clean up as appropriate.
- [ ] **Local file downloads (LiteRT-LM, in-process):** Add `ModelFileTransferClient`
      implementation + platform actuals for direct HTTP(S) downloads to app storage
      (via `PlatformContext`) with resume (Range), checksum verification (sha256),
      extraction if needed, disk space checks, cooperative cancellation, and
      throughput/ETA tracking. Add full LiteRT-LM support (Koog's
      `LiteRTLLMClient` on Android, JVM adapter over `litertlm-jvm` on desktop).
      Use the download infrastructure for download + management UI of
      `.litertlm` models; ensure downloaded models become `EngineKind.LocalFile`
      instances. Run download → checksum → extraction as phases of the same
      `ModelFileDownload` task (allocate progress across phases, only succeed after
      verification passes); use `ModelVerification` only for explicit re-verification
      of already-installed models. Create/update `EngineInstance(kind = LocalFile,
      localPath = ...)`, update `EngineCatalog` availability (replace TODO with
      actual file existence/checksum validation), and register with the engine
      system. Add UI in the Local tier of `EnginePickerSheet` (list downloadable +
      installed local models; "Download" starts `ModelFileDownload` task).
- [ ] **Track context length** as models are selected and downloaded.
- [ ] **Make the Local tier real:** Enable Local placeholder rows per
      ENGINE-CONFIG-PLAN.md stage 3; downloaded models render as `EngineKind.LocalFile`
      instances under Local (platform-gate; web/wasm deferred).
- [ ] **Hostability flag:** Apply memory fit heuristics (see
      [llm-memory-fit.md](llm-memory-fit.md)) as a note (not a filter) next
      to size/context chips for local models and Ollama instances on localhost;
      consider chip color changes for outliers (MLX versions may require recent
      Macs).
- [ ] **On-device evaluation harness:** Build an on-device tool similar to
      `tools/eval_ollama_models.py` to capture timing, ok/clean/json rates,
      memory/footprint estimates, and timeouts for validating LiteRT-LM models
      and extending the curated set.
- [ ] **Logging, progress/cancel/background, and tests:** Log all download
      events (start/phases/errors/cancel/success) via `ActivityLogger` (use
      `ActivityLogger.taskContext(taskId, projectId = null)` for downloads).
      Ensure progress via metadata/`setProgress`, cancel via stream closure +
      task cancellation, background via app scope. Add tests for the download
      framework.

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
- Pull support for Anthropic / OpenAI-compatible endpoints (which they don't really need anyway).
- The actual **Alpha Interplanetary** free backend: this plan consumes its model
  catalogue, but the backend itself is a separate roadmap item, tracked in
  ENGINE-CONFIG-PLAN.md as a disabled Free-tier placeholder.
