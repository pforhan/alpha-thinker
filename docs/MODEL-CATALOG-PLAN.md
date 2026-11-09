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
   model is ever left out of the browser. Discouraged models are collapsed by
   default but can be shown.
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

> **Superseded in part:** local models, local-file downloads, and the in-app
> on-device eval harness are being removed rather than finished. Ollama pull
> stays. See [Removal plan](#removal-plan--local-models-local-downloads-and-ondevice)
> below.

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
        `DownloadState` (handle/task/kind/status/modelId/bytes/throughput/progress/message/source),
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
- [x] Revise how we understand model aliases.  Use manifest SHA to know two models are the same. Note: won't use manifest digests at this time. Curation is keyed by family/model-id patterns and measured seeds, and pull/availability matching stays tag-based. Digests are mainly for runtime alias detection, duplicate suppression, and future "are these the same model?" logic; storing them alongside cured entries would add maintenance burden without improving recommendation matching.
  - [x] Determine scope: this is Ollama-only — other sources use their ids as-is, so alias reconciliation applies only to Ollama tags.
  - [x] the app should recognize that llama3.2:latest is the same as llama3.2:3b — `RemoteModelInfo.digest` is captured from Ollama and a `:latest` alias sharing a digest with an installed concrete tag is shown under that tag.
  - [x] Do we need to rebuild reports to gather manifest sha for everything? No, not necessary since we aren't using sha
  - [x] eval_ollama_models.py rebuilds display names from a tag-shaped size label and refuses descriptive labels such as "mini", so alias resolution no longer fabricates tags like "minib".  Re-running a sweep picks up any alias-rule change.
- [x] **Ollama model pull (curated):** Only present strong quality models from
      `model-evals/all.md` in the UI (no full browser). Implement end-to-end
      pull support for curated Ollama models via the download infrastructure.
  - [x] **Curated source of truth:** Derive the curated Ollama list from
        `ModelProfiles`, filtering to strong-quality candidates. Programmatically 
        filter ModelProfiles.measuredSeeds rather than make a duplicate copy.
        Track installed vs pullable state against `RemoteModelCatalog` results.
  - [x] **Connection UI:** Update `ConnectionDialog` (Advanced) to render any
        curated items not already downloaded alongside other models in the chip list
        as a pull chip once the model list has loaded. These chips should appear in
        their respective tiers.
        Prefixed with "↓" or a similar material icon. Any pull chips should have a
        different background color.
  - [x] **Pull execution:** Upon selection of a pull chip, kick off the download. Add a small
        progress spinner to the chip that indicates the download. Do not block
        saving the dialog configuration as ollama will continue to manage the 
        download. Use `ModelDownloadManager.pullOllama(modelId, baseUrl)`
        to enqueue an `OllamaModelPull` task via `TaskRunner`. Stream NDJSON via
        `OllamaPullClient` (`POST /api/pull` with `stream: true`), treat server
        phases (`pulling manifest`, `pulling <digest>`, `verifying sha256 digest`,
        `writing manifest`, removing unused layers, etc.) as phases of the same
        task (verification included), track `bytesDone`/`bytesTotal`/`progress`,
        and log via `ActivityLogger.taskContext(taskId, projectId = null)`.
        The client consumes the NDJSON line by line as it arrives, and per-layer
        totals are summed so the chip's ring tracks the whole pull.
  - [x] **Lifecycle & refresh:** Support background execution (app scope), and error
        handling. On failure, surface messages and clean up as appropriate.
- [x] After download completes, a pull chip should become a regular chip that takes
      no further action. It should drop its arrow prefix and different color. A
      completed pull re-reads the draft's model list, which turns the entry into a
      normal installed model.
- [x] Model browser should hide discouraged models by default, with a Show button or
      link to toggle. The tier stays open while the current selection is in it, so
      the chosen chip never disappears.
- [x] Models with bad quality should sort after adequate quality models inside
      discouraged. Within a tier, quality comes first and speed breaks ties, both
      best-first.
- [x] See if we can use material icons for llm tier display instead of unicode text.
      `ModelPresentationTier` lost its glyph string; the UI now maps each tier to a
      Material icon (`ModelTierVisuals.kt`), and the pull chip's `↓` is a Download icon.
- [x] **Local file downloads (LiteRT-LM, in-process):** Add `ModelFileTransferClient`
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
      Implemented for Android, the only target with a LiteRT runtime: the curated
      `.litertlm` catalog downloads into app storage and installs LocalFile
      instances. Desktop/iOS/wasm keep the Local tier disabled and compile against
      the shared seams.
- [x] **On-device evaluation harness:** Build an in-app tool similar to
      `tools/eval_ollama_models.py` to capture timing, ok/clean/json rates,
      memory/footprint estimates, and timeouts for validating models
      and extending the curated set.  And write a script for a computer that lets me share 
      this data on a local network back to my computer to add to our data sets.  If we
      aren't already we should be grouping by hardware and os to form per-device recommendations.
      Example: the two llama3.2 models we picked are slow and unable to comply on phone,
      usually filling their output buffers with nonsense.  Phi4-mini seems to work though,
      at least on a Nothing 3.  The tool should expect and ideally handle OOM crashes. If
      it can't do it in-process then record enough data during the test that we can pick it
      up next launch.
      Implemented as a Testing-sheet sweep that persists a resumable row per `(model, run)`
      and groups results by hardware/OS. `tools/eval_receiver.py` brings those rows back
      over the LAN and `tools/eval_report_by_device.py` writes per-device recommendations.
    - [x] Do not use usesCleartextTraffic in android manifest -- either set the script up to make
          an https server (preferred) or use a Network Security Config file
          The receiver now serves HTTPS with a checked-in certificate pinned by the app, and the
          manifest replaced the flag with a network security config that allows only loopback HTTP.
    - [ ] Should be able to view and test a wider array of models from the community than we display
          in local engine. Related: need to manage the downloads like that dialog. Code may be
          sharable?
    - [x] If currently running just say a model is in progress -- not to run again
    - [x] We should narrow the timeout measurements across the board.  Perhaps 30s on each request?
          Take a look at current data to see the impact it could have.  If some models go over, but
          don't have good quality, the shorter time would still benefit.
          Current data shows 30 s changes no recommendation tier but lowers measured quality for
          eleven slow models, so the harness keeps 120 s and aborts repetition loops within seconds.   
    - [ ] this tool doesn't need the engine status and popup menu in the top bar.
    - [ ] is quality a percentage or what?  Display a % or indicate its max value
    - [ ] Need a way to clear results
- [ ] Ability to share models with nearby devices using industry standards (quick share, 
      airdrop, etc) -- and between platforms if these models are platform independent.
- [ ] **Improve logging** the activity log entries aren't very useful; the main entry
      is called Task, from Task Runner, and there are way too many entries that just 
      say phase: Downloading which is itself unuseful.  I'd recommend not logging any
      intermediate download reports, or at most at 25% 50% 75%.
- [ ] **Robust mobile downloads** consider using native download mechanisms (or industry 
      standard ones) on android and ios to better deal with common mobile interruptions
      like sleep, network problems, etc
- [ ] Don't add new rows to "Advanced" for local models -- always use the local model
      dialog. Only delete a model when deleted via that dialog. Use selectable cards
      for models instead of the use button.  If a model is not fully downloaded, start
      a download for it.  Keep the ability to cancel a download in progress. Display 
      partial downloads and allow them to be removed. If we need to keep these as 
      distinct records, that's fine, though probably shouldn't show them or allow them
      to be edited.  Just make sure the records are accurate. Example: currently the 
      edit dialog shows them as half-filled ollama models.   
- [ ] when entering engine status, the title on the main gets a back button.  Sometimes
      this remains even after closing any popups.  Requires a tap anywhere to dismiss,
      but back does nothing.
- [ ] **Track context length** as models are selected and downloaded.
- [x] **Make the Local tier real:** Enable Local placeholder rows per
      ENGINE-CONFIG-PLAN.md stage 3; downloaded models render as `EngineKind.LocalFile`
      instances under Local (platform-gate; web/wasm deferred).
      Done with the item above: the Local card opens the model manager and its
      installed instances render under Local wherever the runtime is supported.
- [ ] Even if we can't display anything while waiting for a full response consider 
      showing some kind of display indicating characters / words received etc
- [ ] **Hostability flag:** Apply memory fit heuristics (see
      [llm-memory-fit.md](llm-memory-fit.md)) as a note (not a filter) next
      to size/context chips for local models and Ollama instances on localhost;
      consider chip color changes for outliers (MLX versions may require recent
      Macs).
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

## Removal plan — local models, local downloads, and `OnDevice`

**Status: queued.** The local-model track is being cut, not finished. This is
the execution checklist for removing it; it supersedes the local-model and
on-device eval items in stage 3.

### Scope decisions (locked)

- **Keep Ollama pull.** `OllamaPullClient`, the curated pull chips,
  `OllamaModelPull`, `ConcurrencyGroup.Download`, and the pull half of the
  `downloads/` package stay. The app can still pull a model onto the Ollama
  server and select it.
- **Remove local-file models and downloads.** Delete LiteRT inference, the
  `Local` tier, `EngineKind.LocalFile`, `ModelFileTransferClient`, the local
  model manager, and the local-file download path. The `downloads/` package is
  narrowed, not deleted wholesale.
- **Remove every `OnDevice` leftover.** Delete the kind, catalog entry,
  capabilities/status branches, and the never-shipped system-model runtime
  references.
- **Remove the in-app on-device eval harness.** Delete `ModelEvalScreen`, the
  `eval/` package, the LAN receiver, and their docs/tests. Keep
  `tools/eval_ollama_models.py` and `docs/model-evals/` for remote Ollama
  evaluation.
- **Preserve the remote-model improvements.** `RemoteModelInfo` metadata,
  alias resolution, curation/ranking, the grouped browser, and pull chips are
  not local-model code and stay.

### 1. Engine domain and catalog

- [x] Remove `EngineKind.OnDevice` from `engine/EngineKind.kt`; update the
      type docs and `available()`, and keep `Default = Offline`. `fromKey` now
      returns null for the retired `"on-device"` key.
- [x] Remove `EngineKind.LocalFile` from `engine/EngineKind.kt`; `available()`
      then covers only `Offline` and `Remote`.
- [x] Remove `EngineTier.Local` from `engine/EngineTier.kt`; update
      `EngineCatalog.groupedByTier` and the picker's preferred-tier list so
      `Offline` and `Free` are the two preferred cards and `Advanced` holds
      remote connections.
- [x] Remove `ModelSource.LiteRt` from `engine/ModelSource.kt` (it has no
      remaining call sites).
- [x] Remove `localPath` from `engine/EngineInstance.kt` and its docs.
- [x] Delete the local engine seams:
      `LocalModel.kt`, `LocalModelCatalog.kt`, `LocalModelStore.kt`,
      `LocalModelRuntime.kt` plus its `androidMain` / `desktopMain` /
      `iosMain` / `wasmJsMain` actuals, `LocalBackendFactory.kt`,
      `LocalKoogPlanningBackend.kt`, and `LocalStreamingBackend.kt`.
- [x] `EngineCatalog`: delete the `OnDevice` built-in entry and its
      `entries()` availability branch; update the `EngineCatalogEntry` doc.
- [x] `EngineCatalog`: delete the `LocalFile` built-in entry, the
      `LocalFile` → `Local` tier mapping, and its availability branches.
- [x] `EngineRegistry`: remove the `LocalBackendFactory` constructor parameter,
      the convenience constructor, and the `LocalFile` instance branch. Keep
      `Offline` and `Remote`.
- [x] `ui/chrome/EngineStatus.kt`: remove the `OnDevice` cases from
      `networkDetail` and `llmDetail`.
- [x] `ui/chrome/EngineStatus.kt`: remove the `LocalFile` cases from
      `networkDetail` and `llmDetail` so the `when (kind)` is exhaustive over
      `Offline` / `Remote`. `EngineStatusCopy.kt` has no kind branches, so it
      only needs its test fixtures updated (section 6).
- [x] Remove `EngineAvailability.NotImplemented` once no entry reports it. It
      exists only for the removed kinds: delete the case from
      `engine/EngineAvailability.kt` and the `is NotImplemented` branches in
      `EngineCatalog`, `EnginePickerSheet` ("Not available on this device
      yet."), and `EngineStatus`; update `EngineStatusTest`.
- [x] Remove `LogSource.LocalLLM` after `LocalKoogPlanningBackend` is gone;
      `parseSource` already degrades unknown stored names to null, but update
      the enum, its label, and `EngineRegistryTest`'s local assertion.
- [x] Update `EngineCapabilities` docs/tests so only `Remote` reports
      `Network + Llm` and `Offline` reports none.

### 2. Narrow downloads to Ollama pull

- [x] `downloads/ModelDownloader.kt`: delete `ModelDownloadSpec` and
      `downloadModel`; keep `downloads()`, `pullOllama`, and `cancel`.
- [x] Delete `ModelFileTransferClient.kt`, `ModelFileTransferClientFactory.kt`,
      `androidMain/.../AndroidModelFileTransferClient.kt`, and the
      `ModelFileTransferClientFactory` actuals for Android, desktop, iOS, and
      wasm.
- [x] Remove `DownloadKind.LocalFile`; keep `OllamaPull`.
- [x] `TaskBackedModelDownloader`: drop the `modelFileTransferClient`
      dependency, `downloadModel`, and local-only imports; keep the pull path
      and its per-layer progress summing.
- [x] Remove `TaskKind.ModelFileDownload` and `TaskKind.ModelVerification`
      (and their `TaskLabels` entries); keep `OllamaModelPull` in
      `ConcurrencyGroup.Download`.
- [x] Keep the download metadata on `GenerationTask` and
      `TaskRunner.setDownloadProgress` / `setDownloadMetadata` for pulls;
      update comments that still mention local files.
- [x] `di/AppComponent.kt`: remove `modelFileTransferClient`,
      `localModelStore`, and `modelEvaluator`; delete
      `providesLocalFileDownloader`, `providesLocalBackendFactory`,
      `providesLocalModelStore`, and `providesModelEvaluator`; simplify
      `providesModelDownloadManager`.
- [x] `EngineViewModel`: drop the `localModelStore` parameter and local-model
      imports; remove `localModels`, `downloadLocalModel`, `useLocalModel`,
      `deleteLocalModel`, and the local-download completion collector. Keep
      the pull collector and `downloads`.
- [x] Collapse the redundant pull aliases (`pullOllama`,
      `pullCuratedOllamaModel`, `startOllamaPull`, `triggerOllamaPull`, `pull`,
      `pullModel`) into `pullOllamaModel`; update `EnginePickerSheet` and tests.
- [x] `ConnectionDialog`: keep pull chips, the download glyph, and the progress
      ring, but replace the `LocalModelRuntime.inferenceSupported` proxy in the
      HTTP warning with an explicit platform capability (or an equivalent
      Android check). That warning is about HTTPS policy, not local models.
- [x] Delete `ui/chrome/LocalModelsDialog.kt`; remove `showLocalModels`,
      `onOpenLocalModels`, `localSelected`, the Local-tier special cases, and
      the related comments from `EnginePickerSheet.kt`.
- [x] Leave `ModelBrowserMerge`, `RemoteModelInfo`, `ModelIdentity`,
      `ModelProfiles`, and `ModelRanker` untouched.

### 3. Remove the on-device evaluation harness

- [x] Delete the `eval/` package: `ModelEvaluator.kt`, `EvalAggregation.kt`,
      `EvalFixtures.kt`, `EvalMemory.kt`, `EvalRemote.kt`,
      `EvalRepetition.kt`, `EvalRows.kt`, `EvalRowStore.kt`, and
      `EvalScoring.kt`; delete `ui/screens/ModelEvalScreen.kt`.
- [x] Remove `AppRoute.ModelEval` from `ui/navigation/NavStack.kt` (the
      `NavEntry.toKey()` branch and the `navEntryFromKey` case) and its
      `NavApp` branch plus the `route != AppRoute.ModelEval` guard in
      `ui/navigation/NavApp.kt`.
- [x] Remove the Testing-sheet launcher from `ui/chrome/TestingSheet.kt` and
      `AppChromeState.modelEval`; update `AppChromeState` construction.
- [x] Delete the `EvalRemote` actuals (`androidMain`, `desktopMain`,
      `iosMain`, `wasmJsMain`) and the desktop TLS tests
      `EvalRemotePinnedTlsTest` / `EvalRemotePinningTest`.
- [x] Remove `PlatformContext.evalDirectory`, `readTextFile`, `writeTextFile`,
      `listFiles`, and `deviceInfo`; delete `di/DeviceInfo.kt` and the
      platform/`FakePlatformContext` overrides.
- [x] Delete `tools/eval_receiver.py`, `tools/eval_tls/`,
      `tools/eval_report_by_device.py`, and `docs/MODEL-EVAL-HARNESS.md`;
      remove the app-side evaluation section from `tools/README.md`. Keep
      `tools/eval_ollama_models.py` and `docs/model-evals/`.
- [x] Confirm no manifest cleartext/network-security exception existed only for
      the receiver. Keep loopback HTTP support only if localhost Ollama still
      needs it. No such exception existed in `AndroidManifest.xml`.

### 4. Persistence and stale rows

- [x] Remove `localPath` from `EngineInstanceEntity`, `EngineInstance.toEntity`,
      and `EngineInstanceEntity.toModel`.
- [x] Bump `AppDatabase.version` (12 → 13). `getRoomDatabase` uses
      `fallbackToDestructiveMigration()`, so the upgrade rebuilds local
      storage; call that out in the change because it also clears projects and
      settings. If preserving data matters more, keep the column and skip the
      bump.
- [x] Make removed kinds harmless. `EngineInstanceEntity.toModel()` currently
      does `requireNotNull(EngineKind.fromKey(kind))`, so old `"on-device"` /
      `"downloaded"` rows throw. Either rely on the destructive rebuild or
      change the mapper to skip unknown kinds and delete those rows.
- [x] Prune stale `engine_state` rows. After the enum change,
      `EngineRef.fromStorageKey("on-device")` and `"downloaded"` resolve to
      `Instance(...)` instead of a builtin. In `EngineRepository.load`, drop
      state rows whose ref is not a known builtin or a live instance and fall
      back to `EngineRef.Builtin(EngineKind.Default)` when the selected ref is
      gone. `EngineStateDao` has no delete yet, so add
      `deleteByKey(refKey)` (plus a `Storage` method) to remove them.
- [x] Also delete orphaned `engine_state` rows for removed local instances, and
      verify `firstRunPending` and last-used ordering still behave.
- [x] Add a mapper/repository regression test proving a stored
      `"on-device"` / `"downloaded"` row or selection cannot crash the app.

### 5. Platform seams and dependencies

- [ ] Remove `PlatformContext.modelDirectory`, `fileExists`, and `deleteFile`
      plus the Android/desktop/iOS/wasm overrides and
      `FakePlatformContext` fields (after `LocalModelStore` and the transfer
      client are gone).
- [ ] Remove `libs.koog.prompt.executor.litert.client` and
      `libs.litertlm.android` from `shared/build.gradle.kts`; remove
      `koogLitert`, `litertlm`, and their aliases from
      `gradle/libs.versions.toml`.
- [ ] Check `androidApp`, `iosApp`, and `webApp` for leftover LiteRT or
      model-storage references (none expected).
- [x] If `ConnectionDialog` still needs the HTTPS-only platform flag, add it
      here rather than keeping `LocalModelRuntime` as a proxy.

### 6. Tests

- [x] Delete local/download/eval tests: `LocalModelCatalogTest`,
      `LocalModelStoreTest`, `ModelEvaluatorTest`, `EvalAggregationTest`,
      `EvalMemoryTest`, `EvalRemoteTest`, `EvalRepetitionDetectorTest`,
      `EvalRowStoreTest`, `EvalScoringTest`, `EvalRemotePinnedTlsTest`, and
      `EvalRemotePinningTest`; trim `TaskBackedModelDownloaderTest` and
      `FakeModelDownloader` (drop `specs` / `downloadModel`) to the pull cases.
- [x] Update `EngineKindTest`, `EngineCatalogTest`, `EngineCapabilitiesTest`,
      `EngineRegistryTest`, `EngineRepositoryTest`, `EngineStatusTest`,
      `EngineStatusCopyTest`, `AppChromeStateTest`, and `EngineViewModelTest`
      to remove `OnDevice` / `LocalFile` expectations and local-download cases.
- [x] Keep `OllamaPullClientTest`, the pull cases in
      `TaskBackedModelDownloaderTest`, `ModelBrowserMergeTest`,
      `ModelIdentityTest`, `ModelProfilesTest`, `ModelRankerTest`, and
      `RemoteModelCatalogTest` green.

### 7. Docs and verification

- [ ] `docs/ENGINE-CONFIG-PLAN.md`: update the engine kinds
      (`Offline` / `Remote`), tiers (`Offline` / `Free` / `Advanced`), storage
      shape without `localPath`, and the local-model stage notes.
- [ ] `docs/IMPLEMENTATION-PLAN.md`: mark the local-model items as
      removed/superseded with at most two sentences each — Phase 3.7's
      "Model catalog … downloads" item, Web (wasm) on-device, desktop
      in-process LiteRT, iOS in-process local models, and the Llamatik
      contingency; plus Phase 4's `OnDeviceLLMClient`. Keep the Ollama eval and
      pull items. (The in-app eval harness is only documented in
      `docs/MODEL-EVAL-HARNESS.md`, with no plan row.)
- [ ] `docs/ENG-DESIGN.md`: remove the On-device / Downloaded rows from the
      engine-mode table in "LLM Inference Layer" and the "Downloaded local
      models (LiteRT-LM)" paragraph; keep the remote and Ollama paths.
- [ ] `docs/PRD.md`: correct the "Alpha Thinker Edge" local-LLM wording and the
      Ollama pull "planned" note (pull exists; local models do not).
- [ ] `docs/MODEL-CATALOG-PLAN.md`: Move the stage 3 local-model and eval items
      to an ARCHIVED-LOCAL-MODELS.md, keep the pull items, and refresh 
      "Blast radius" / "Out of scope".
- [ ] Update `README.md` / `tools/README.md` wherever local models are
      advertised.
- [ ] Verify with `./gradlew :shared:desktopTest`, then search for leftovers:
      `OnDevice`, `LocalFile`, `LocalModel`, `LiteRT`, `litert`,
      `ModelFileTransfer`, `ModelEvaluator`, `EvalRemote`, and
      `MODEL-EVAL-HARNESS`. Only intentional history/docs references should
      remain.
- [ ] Smoke-test `Offline` and `Remote` selection plus one Ollama pull in the
      connection editor.

---

## Blast radius

- `RemoteModelCatalog` / `RemoteModelInfo` / `KoogRemoteModelCatalog`;
  `SettingsViewModel.refreshRemoteModels` / `selectRemoteModel`; `ModelBrowser`
  (`ui/chrome/ConnectionDialog.kt`); `ModelProfiles` / `ModelRanker`; the
  Ollama pull download path; PRD §4.3 wording.
- Tests: the `RemoteModelCatalog` cases and `SettingsViewModelTest`.
- Stage 3 adds the first real "download" UI. After the removal plan it serves
  only the Ollama pull: the progress ring and cancel in the connection editor.

## Out of scope

- Auto-promotion of tiers between evaluation batches (human-in-the-loop).
- Local (on-device) models, local-file downloads, and the in-app eval harness:
  removed by the removal plan above, not deferred.
- Hiding models outright. Discouraged models are collapsed by default but stay
  reachable through the browser's Show toggle.
- Pull support for Anthropic / OpenAI-compatible endpoints (which they don't really need anyway).
- The actual **Alpha Interplanetary** free backend: this plan consumes its model
  catalogue, but the backend itself is a separate roadmap item, tracked in
  ENGINE-CONFIG-PLAN.md as a disabled Free-tier placeholder.
