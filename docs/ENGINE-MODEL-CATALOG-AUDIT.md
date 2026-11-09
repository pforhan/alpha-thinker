# Engine & model catalog audit (26.1)

Disposition matrix for the classes created/touched by
[ENGINE-CONFIG-PLAN.md](ENGINE-CONFIG-PLAN.md) and
[MODEL-CATALOG-PLAN.md](MODEL-CATALOG-PLAN.md), produced for
[IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) task 26.1. "Key call sites" are
representative, confirmed by `grep`/reading (not exhaustive). Verdicts:
**keep** (justified), **fold** (remove/merge), **rename** (clarify a conflict),
**relocate/split** (right concept, wrong file/package/layer). No code moves in
this audit except 26.2, which landed first.

## Engine config / identity

| Type | File | Responsibility | Key call sites | Verdict | Rationale |
|---|---|---|---|---|---|
| `EngineKind` | `engine/EngineKind.kt` | Backend archetype (`Offline`/`Remote`) with durable `key`, label/description, static `capabilities`. | `EngineRef`, `EngineInstance`, `EngineCatalog`, `EngineRegistry`, `EngineRepository`, `RemoteKoogPlanningBackend`, `ui/chrome/EngineStatus`, persistence mappers. | **Keep** (fold `available()`). | Central identity. `available()` returns `true` for every kind and is only referenced by `EngineKindTest` (`grep '\.available()'` finds no production call sites) — readiness is owned by `EngineCatalog`/`EngineAvailability` (26.3). |
| `EngineRef` (`Builtin`/`Instance`) | `engine/EngineRef.kt` | Selectable engine identity; durable `storageKey` for selection/last-use. | `EngineSelectionState`, `EngineCatalog`, `EngineRegistry`, `EngineRepository`, `Storage`/DAOs, `ui/chrome/EngineStatus`, viewmodels. | **Keep**. | The per-selection unit the plan needed; small, well-scoped. |
| `EngineInstance` | `engine/EngineInstance.kt` | User-created connection row (provider/endpoint/key/model/window). | `EngineCatalog`, `EngineRegistry`, `EngineRepository`, `RemoteConnection` construction, storage/mappers, connection UI/VM. | **Keep**. | Carries the fields the registry/backend consume; `remote()`/`defaultRemote()` centralize construction. |
| `EnginePickerTier` *(renamed from `EngineTier`, 26.5)* | `engine/EnginePickerTier.kt` | Picker grouping (`Offline`/`Free`/`Advanced`) + display order. | `EngineCatalog.groupedByTier`, picker UI. | **Keep** (done). | Engine-picker grouping, disambiguated from `ModelPresentationTier` (model curation). |
| `EngineCatalogEntry` | `engine/EngineCatalogEntry.kt` | Code-defined built-in/user instance entry with per-ref `availability(repo)`. | `EngineCatalog`, picker, status derivation. | **Keep** (27.2). | Split from `EngineCatalog.kt`; availability lambda reads the repo (coupling to preserve deliberately). |
| `EngineCatalog` | `engine/EngineCatalog.kt` | Pure merge/grouping of built-ins + instances by picker tier/recency. | `EngineRepository` (live keys/pruning), `EngineRegistry` consumers, picker, `EngineStatus` derivation. | **Keep** (27.2). | Split from `EngineCatalogEntry`. |
| `EngineRegistry` | `engine/EngineRegistry.kt` | Builds/caches `PlanningEngine` per `EngineRef`; `invalidate` closes evicted engines. | `AppComponent`/DI, task execution path (`PlanningEngineSelector` seam), settings/engine viewmodels. | **Keep**. | Replaced the parallel mode maps and `DynamicKoogPlanningBackend`. |
| `EngineSelectionState` *(renamed from `EngineState`, 26.2)* | `engine/EngineSelectionState.kt` | Domain record: `selected` + `lastUsedAt` per `EngineRef`; persisted via `engine_selection_state`. | `EngineRepository` (`load`/`markUsed`/`shouldOfferFirstRun`), `Storage`/`Mappers`/`EngineSelectionStateEntity`/DAO. | **Keep** (done). | 26.2 resolved the collision with UI `EngineStatus`; table/entity/DAO/storage names kept coherent and DB bumped to v14 (destructive migration). |
| `EngineAvailability` | `engine/EngineAvailability.kt` | `Available` / `Unavailable(reason)` for whether an engine can run now. | `EngineCatalog` entries, `ui/chrome/EngineStatus`. | **Keep**. | Distinct from archetype; the one readiness concept (pairs with 26.3). |
| `EngineCapability` | `engine/EngineCapabilities.kt` | Capability vocabulary (`Network`/`Llm`/`Tools`) implied by a kind/mode. | `EngineKind.capabilities`, `ui/chrome` capability slots/copy/status. | **Keep**. | Stable shared vocabulary across engine and chrome. |
| `PlanningEngineSelector` | `engine/PlanningEngineSelector.kt` | Fun-interface seam freezing the resolved engine per task (lock-in). | `ProjectRepository`/task execution, tests. | **Keep**. | Tiny, justified indirection. |
| `RemoteConnection` | `engine/RemoteConnection.kt` | Config DTO (provider/baseUrl/apiKey/model/contextLength) for the remote backend. | `EngineRegistry` construction, `RemoteKoogPlanningBackend`. | **Keep** (26.9). | Extracted from `RemoteKoogPlanningBackend.kt`. |
| `RemoteProvider` | `engine/RemoteProvider.kt` | Vendor catalogue (connection/protocol + defaults): endpoint defaults, key requirements, base-URL configurability, wire/client mapping metadata. | `EngineInstance`, `RemoteConnection`/backends, `RemoteModelCatalog`, `ModelBrowserMerge`, connection UI. | **Keep** (26.4 done). | Boundary clarified: provider = how to connect; provenance = `ModelMetadataSource`. Exposes the single explicit mapping `RemoteProvider.metadataSource` so the enums are not assumed equivalent. |

## Engine runtime / plumbing (in package; repackaging targets, not config/catalog domain)

| Type | File | Responsibility | Key call sites | Verdict | Rationale |
|---|---|---|---|---|---|
| `QuestionBatch` | `engine/QuestionBatch.kt` | Question-generation batch result + `summary()` contract. | `PlanningEngine`, engines, logging, tasks/repository. | **Keep** (27.2). | Split from `PlanningEngine.kt`. |
| `PlanningEngine` | `engine/PlanningEngine.kt` | Core engine contract. | All engines, registry, selector, repository/tasks, logging/decorators. | **Keep** (27.2). | Core seam. |
| `KoogPlanningEngine` | `engine/KoogPlanningEngine.kt` | `PlanningEngine` over a `KoogPlanningBackend`. | `EngineRegistry` (Remote). | **Keep**. | Runtime impl. |
| `KoogPlanningBackend` | `engine/KoogPlanningBackend.kt` | Backend seam (model/JSON/streaming) for Koog engine. | `KoogPlanningEngine`, `RemoteKoogPlanningBackend`. | **Keep**. | Abstraction enabling the remote backend. |
| `RemoteKoogPlanningBackend` | `engine/RemoteKoogPlanningBackend.kt` | Koog client/backend over a `RemoteConnection`. | `EngineRegistry`. | **Keep, split file**. | Carries `RemoteConnection` (relocate, 26.9). |
| `OfflinePlanningEngine` | `engine/OfflinePlanningEngine.kt` | Built-in offline question library. | `EngineRegistry` (Builtin Offline). | **Keep**. | Default/fallback engine. |
| `LoggingPlanningEngine` | `engine/LoggingPlanningEngine.kt` | Decorator logging prompts/responses/raw capture. | Engine composition/DI. | **Keep**. | Cross-cutting runtime decorator. |
| `ForwardingPlanningEngine` | `engine/ForwardingPlanningEngine.kt` | Delegating base for decorators. | Decorator implementations (e.g. slowdown/logging patterns). | **Keep**. | Small reuse seam; verify live subclasses during moves. |
| `SlowDownPlanningEngine` | `engine/SlowDownPlanningEngine.kt` | Dev-tool delay decorator. | Dev tooling/DI composition (Phase 8-adjacent). | **Keep, repackage carefully**. | Dev-tool concern — keep out of "product catalog" neighbors (27.5). |
| `EngineInteraction` | `engine/EngineInteraction.kt` | Dev-tool delay interaction enum. | Slowdown/developer settings. | **Keep, repackage carefully** (27.2 split). | Dev-tool concern (27.5). |
| `EngineDelayConfig` | `engine/EngineDelayConfig.kt` | Dev-tool delay config. | Slowdown/developer settings. | **Keep, repackage carefully** (27.2 split). | Dev-tool concern (27.5). |
| `PlanningContext` | `engine/PlanningContext.kt` | Prompt/transcript context rendering. | `KoogPlanningEngine`/prompt building, export (future). | **Keep**. | Prompt plumbing, not catalog. |
| `ModelIdentity` helpers | `engine/ModelIdentity.kt` | Alias/size/count parsing helpers (top-level, no class). | `RemoteModelCatalog` parsing, display/ranking inputs. | **Keep, relocate**. | Model metadata helpers; fits the model subpackage (27.4), not engine config. |

## Model catalog / curation

| Type | File | Responsibility | Key call sites | Verdict | Rationale |
|---|---|---|---|---|---|
| `ModelMetadataSource` *(renamed from `ModelSource`, 26.4)* | `engine/ModelMetadataSource.kt` | Metadata provenance (`Ollama`/`OpenAiCompatible`/`Anthropic`) — explains which fields can be known. | `RemoteModelInfo.source`, `RemoteModelCatalog` impl, `ModelBrowserMerge`, tests. | **Keep** (done). | Renamed for clarity and KDoc'd as provenance (not `RemoteProvider`); mapped explicitly via `RemoteProvider.metadataSource`. |
| `RemoteModelInfo` | `engine/RemoteModelInfo.kt` | Canonical model record (id + `displayName` alias + context/size/capability metadata). | Catalog, `ModelRanker`/`ModelBrowserMerge`, `EngineViewModel` auto-select, connection UI. | **Keep** (27.2). | Split out of `RemoteModelCatalog.kt`. |
| `RemoteModelCatalog` (interface) | `engine/RemoteModelCatalog.kt` | Contract to list models / resolve context length for a provider. | `KoogRemoteModelCatalog`, `EngineViewModel`, fakes/tests. | **Keep** (27.2). | Contract separated from impl; enables fakes. |
| `KoogRemoteModelCatalog` | `engine/KoogRemoteModelCatalog.kt` | HTTP/catalog impl (Anthropic bundled, OpenAI-compatible `/v1`, Ollama tags+show). | DI/provisioning, `EngineViewModel` (via interface). | **Keep** (27.2). | Impl split out; private serialization DTOs/helpers kept local to the impl file. |
| `RemoteModelListState` | `ui/chrome/RemoteModelListState.kt` | Browse UI state (`Idle`/`Loading`/`Loaded`/`Failed`); `Loaded.browserEntriesWithPulls()`. | `EngineViewModel`, `ConnectionDialog`, `EngineViewModelTest`. | **Keep** (26.7). | Re-homed out of `engine/RemoteModelCatalog.kt` (catalog contract + model info only) to chrome view state shared by VM and connection UI. |
| `ModelQuality` | `engine/ModelQuality.kt` | Quality axis for curation/ranking. | `ModelMeasurement`/`ModelProfiles`, `ModelRanker`, UI chips. | **Keep** (26.6). | Split out of `ModelCuration.kt`. |
| `ModelSpeed` | `engine/ModelSpeed.kt` | Speed axis for curation/ranking. | Curation/ranking/UI. | **Keep** (26.6). | Split out of `ModelCuration.kt`. |
| `ModelPresentationTier` | `engine/ModelPresentationTier.kt` | Derived presentation grouping (`Recommended`/slower/adequate/unknown/discouraged). | `ModelRank`, `ModelRanker`, browser merge/UI. | **Keep** (26.6). | Model-curation grouping; split out of `ModelCuration.kt` (bare-noun collision with engine grouping resolved in 26.5). |
| `ModelRank` | `engine/ModelRank.kt` | Ranked result: model + tier/quality/speed/reason/estimated. | `ModelRanker`, `ModelBrowserMerge`/`ModelBrowserEntry`, VM/UI. | **Keep** (26.6). | Data side of the rank/ranker pair. |
| `ModelMeasurement` | `engine/ModelMeasurement.kt` | Measured seed record from eval rows. | `ModelProfiles.measuredSeeds`, `ModelRanker`. | **Keep** (26.6). | Evidence backing curation; eval snippet shape (`ModelMeasurement`, `measuredSeeds`) preserved. |
| `ModelProfiles` (object) | `engine/ModelProfiles.kt` | Measured seeds, family heuristics, lookup/strong-id helpers. | `ModelRanker`, `ModelBrowserMerge` (`strongModelIds`), tests, `tools/eval_ollama_models.py` snippet expectations. | **Keep** (26.6). | Reconciled to `ModelProfiles.kt` (as the plan expected), split from `ModelCuration.kt`. |
| `ModelRanker` (object) | `engine/ModelRanker.kt` | Deterministic ranking over measurements/heuristics into `ModelRank`. | `ModelBrowserMerge`, `EngineViewModel` auto-select. | **Keep**. | Clean data-vs-logic split; already separate. |
| `ModelBrowserEntry` | `engine/ModelBrowserEntry.kt` | One ranked browser entry, marked `isPull` when not yet installed. | `ModelBrowserMerge`, `RemoteModelListState`/connection UI. | **Keep** (27.2). | Split from `ModelBrowserMerge.kt`. |
| `ModelBrowserMerge` | `engine/ModelBrowserMerge.kt` | Pure merge of installed + pullable curated models into ranked, `isPull`-marked entries. | `RemoteModelListState.Loaded.browserEntriesWithPulls()`, connection UI. | **Keep** (27.2). | Small/testable merge logic. |

## Downloads / Ollama pull (kept after local-model removal)

| Type | File | Responsibility | Key call sites | Verdict | Rationale |
|---|---|---|---|---|---|
| `DownloadKind` | *(removed)* | Former download kind enum (`OllamaPull` only). | — | **Eliminated**. | Redundant after local-model removal/27.2: only Ollama pulls exist, so `DownloadState.kind` and kind-based filters were removed. |
| `DownloadStatus` | `downloads/DownloadStatus.kt` | Lifecycle phase enum (`Queued`…`Cancelled`, `finished`). | `DownloadState`, `TaskBackedModelDownloader` status mapping, pull UI/tests. | **Keep, naming review** (27.2 split). | Phase enum vs. aggregate `DownloadState`; parallels `TaskStatus`. Decide rename to `DownloadPhase` only if low churn (26.8). |
| `DownloadHandle` | `downloads/DownloadHandle.kt` | Download handle linking `id` to task id. | `ModelDownloader`/`TaskBackedModelDownloader`, VM/UI cancel. | **Keep** (27.2). | Split from `DownloadModels.kt`. |
| `DownloadState` | `downloads/DownloadState.kt` | Observable aggregate snapshot of a download. | `ModelDownloader.downloads()`, pull chips/progress/cancel UI, tests. | **Keep** (27.2). | The observed shape; distinct from `DownloadStatus` phase. |
| `ModelDownloader` (interface) | `downloads/ModelDownloader.kt` | Narrow pull API: observe, `pullOllama`, `cancel`. | `TaskBackedModelDownloader`, `EngineViewModel`/DI. | **Keep**. | Contract for fakes and the task-backed impl. |
| `TaskBackedModelDownloader` | `downloads/TaskBackedModelDownloader.kt` | Bridges Ollama pulls to `TaskRunner`/progress/metadata/cancel. | DI, `ModelDownloader` impl, tests. | **Keep**. | The live pull path. |
| `OllamaPullClient` (interface) | `downloads/OllamaPullClient.kt` | Streaming pull contract. | `KoogOllamaPullClient`, `TaskBackedModelDownloader`, tests. | **Keep** (27.2). | Split from impl/event. |
| `OllamaPullEvent` | `downloads/OllamaPullEvent.kt` | Pull progress/error event. | `OllamaPullClient`/impl, downloader. | **Keep** (27.2). | Split out. |
| `KoogOllamaPullClient` | `downloads/KoogOllamaPullClient.kt` | Koog/HTTP NDJSON pull impl. | DI, `TaskBackedModelDownloader`, tests. | **Keep** (27.2). | Private serialization DTOs/helpers kept local to the impl file. |
| `EmaThroughput` | `downloads/EmaThroughput.kt` | Smoothed transfer-rate/ETA helper. | `TaskBackedModelDownloader` progress. | **Keep**. | Small, focused utility. |

## Confirmed fold candidates (evidence)

- `EngineKind.available()` — `grep '\.available()' shared/src` returns only
  `EngineKindTest` assertions; no production call sites. Availability is derived
  via `EngineCatalog`/`EngineAvailability` (26.3).
- No obviously dead plan-created class found in this pass; the decorator/base
  types (`ForwardingPlanningEngine`, `PlanningEngineSelector`) have live roles
  but should be re-checked when 27.1 defines the runtime subpackage.

## 27.1 Target package map (no moves yet)

Recorded for [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) 27.1. This defines
target subpackages and dependency direction only; 27.2–27.5 perform the splits
and moves, and 27.6 verifies.

### Target subpackages

| Target package | Contents (current names/types) | Concern |
|---|---|---|
| `engine.core` | `PlanningEngine`, `QuestionBatch`, `PlanningEngineSelector`, `ForwardingPlanningEngine`, `PlanningContext` | Core engine contract, task lock-in seam, prompt/transcript plumbing. No Koog/client, config persistence, or model-catalog impl deps. |
| `engine.runtime` | `KoogPlanningEngine`, `KoogPlanningBackend`, `RemoteKoogPlanningBackend`, `OfflinePlanningEngine`, `LoggingPlanningEngine`; dev-tool `SlowDownPlanningEngine`, `EngineInteraction`, `EngineDelayConfig` kept isolated (see 27.5 — e.g. `engine.runtime.dev` or a clearly separated file group, not beside product catalog config) | Runtime engine implementations, decorators, and backend wiring. Depends on `engine.core` and config/model types it consumes. |
| `engine.config` | `EngineKind`, `EngineRef`, `EngineInstance`, `EnginePickerTier`, `EngineSelectionState`, `EngineAvailability`, `EngineCapability`, `RemoteProvider`, `RemoteConnection`, `EngineCatalogEntry`, `EngineCatalog`, `EngineRegistry` | Engine identity, picker/catalog grouping, persisted selection state, availability/capability vocabulary, provider/connection config, and the catalog/registry that resolve a ref to an engine. |
| `engine.model` | `RemoteModelInfo`, `ModelMetadataSource`, `ModelIdentity` helpers, `RemoteModelCatalog` (contract), `KoogRemoteModelCatalog` (impl) *(split in 27.2)*, `ModelBrowserMerge`/`ModelBrowserEntry`, `ModelQuality`, `ModelSpeed`, `ModelPresentationTier`, `ModelRank`, `ModelMeasurement`, `ModelProfiles`, `ModelRanker` | Model metadata/provenance, remote model discovery/catalog, browser merge, and curation/ranking. |

`downloads/` remains a top-level sibling package (narrowed to the Ollama pull).
`RemoteModelListState` stays in `ui/chrome` (26.7).

### Dependency direction (acyclic target)

```text
ui/*  ->  engine.{core,runtime,config,model}, downloads, repository, ...
downloads  ->  tasks/logging (+ narrow engine helper noted below)
engine.runtime  ->  engine.core, engine.config, engine.model
engine.config   ->  engine.core (engine refs/selection surface) + engine.runtime construction targets used by EngineRegistry
engine.model    ->  engine.config/provider types only where required (e.g. RemoteProvider/connection context), else standalone
engine.core     ->  (no engine subpackage deps)
```

Confirmed/required one-way constraints:
- `ui/chrome` and `downloads` depend inward; neither `engine` nor `downloads` imports `ui` (`grep` over `engine/` found no `alphainterplanetary.thinker.ui` imports).
- `downloads` currently imports only `engine.normalizeRemoteBaseUrl` (`OllamaPullClient`) — a narrow `downloads -> engine` helper dep. 27.x should place/share that helper deliberately (e.g. a small `engine.core`/`engine.model`-adjacent utility) so `downloads` does not reach into runtime internals.

Known coupling to resolve deliberately during moves (not pure-domain):
- `engine.config`'s `EngineCatalog` imports `repository.EngineRepository` and
  `EngineRegistry` imports `repository.EngineRepository` + `di.AppScope` (verified
  by grep). The map keeps them in `engine.config` as the config/assembly layer;
  27.3 must preserve this upward dependency direction (or extract a port) rather
  than hiding it. `EngineSelectionState` and the pure identity types have no
  repository/di/ui/downloads deps.
- `EngineRegistry` constructs `engine.runtime` implementations; placing registry
  in `engine.config` means config -> runtime at the composition edge. Keep that
  edge explicit (registry is the composition root for engines) and avoid cycles
  back into config from runtime beyond shared types.

### Notes carried forward

- One-type-per-file (AGENTS) for the plan's scope: resolved in 26.6/26.9/27.2
  (`ModelCuration` → 6 files; `RemoteConnection` extracted; `RemoteModelCatalog` →
  `RemoteModelInfo`/contract/`KoogRemoteModelCatalog` impl with private DTOs local;
  `DownloadModels` → `DownloadStatus`/`Handle`/`State` (`DownloadKind` later eliminated);
  `OllamaPullClient` → interface/event/impl with private DTOs local;
  `PlanningEngine`/`QuestionBatch`, `EngineDelayConfig`/`EngineInteraction`,
  `ModelBrowserMerge`/`ModelBrowserEntry`, `EngineCatalog`/`EngineCatalogEntry` split).
  Reconfirm during 27.3–27.5 moves.
- `RemoteModelListState` re-homed to `ui/chrome/RemoteModelListState.kt` in 26.7.
- Dev-tool delay types must not land beside product catalog types (27.5).
- Suggested execution order (status): `26.1(done) → 26.2(done)/26.3(done)/26.4(done)/26.5(done) → 27.1(done) → 26.6(done)/26.7(done)/26.9(done)/27.2(done) → 27.3/27.4/27.5 → 26.8 (optional) → 27.6`.
