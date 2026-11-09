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
| `EngineTier` | `engine/EngineTier.kt` | Picker grouping (`Offline`/`Free`/`Advanced`) + display order. | `EngineCatalog.groupedByTier`, picker UI. | **Keep, rename candidate**. | Shares the bare noun "tier" with `ModelPresentationTier` (unrelated axis: picker grouping vs. model curation). Disambiguate in 26.5. |
| `EngineCatalogEntry` + `EngineCatalog` | `engine/EngineCatalog.kt` | Code-defined built-ins + pure merge/grouping of instances by tier/recency; per-ref `availability(repo)`. | `EngineRepository` (live keys/pruning), `EngineRegistry` consumers, picker, `EngineStatus` derivation. | **Keep, split file**. | Right responsibility. Two public types in one file (AGENTS: one type per file). Availability lambda reads the repo — acceptable, but note the behavioral coupling. |
| `EngineRegistry` | `engine/EngineRegistry.kt` | Builds/caches `PlanningEngine` per `EngineRef`; `invalidate` closes evicted engines. | `AppComponent`/DI, task execution path (`PlanningEngineSelector` seam), settings/engine viewmodels. | **Keep**. | Replaced the parallel mode maps and `DynamicKoogPlanningBackend`. |
| `EngineSelectionState` *(renamed from `EngineState`, 26.2)* | `engine/EngineSelectionState.kt` | Domain record: `selected` + `lastUsedAt` per `EngineRef`; persisted via `engine_selection_state`. | `EngineRepository` (`load`/`markUsed`/`shouldOfferFirstRun`), `Storage`/`Mappers`/`EngineSelectionStateEntity`/DAO. | **Keep** (done). | 26.2 resolved the collision with UI `EngineStatus`; table/entity/DAO/storage names kept coherent and DB bumped to v14 (destructive migration). |
| `EngineAvailability` | `engine/EngineAvailability.kt` | `Available` / `Unavailable(reason)` for whether an engine can run now. | `EngineCatalog` entries, `ui/chrome/EngineStatus`. | **Keep**. | Distinct from archetype; the one readiness concept (pairs with 26.3). |
| `EngineCapability` | `engine/EngineCapabilities.kt` | Capability vocabulary (`Network`/`Llm`/`Tools`) implied by a kind/mode. | `EngineKind.capabilities`, `ui/chrome` capability slots/copy/status. | **Keep**. | Stable shared vocabulary across engine and chrome. |
| `PlanningEngineSelector` | `engine/PlanningEngineSelector.kt` | Fun-interface seam freezing the resolved engine per task (lock-in). | `ProjectRepository`/task execution, tests. | **Keep**. | Tiny, justified indirection. |
| `RemoteConnection` | `engine/RemoteKoogPlanningBackend.kt` | Config DTO (provider/baseUrl/apiKey/model/contextLength) for the remote backend. | `EngineRegistry` construction, `RemoteKoogPlanningBackend`. | **Keep, relocate/split**. | Config DTO buried in the backend impl file (2 public types). Extract to its own file/subpackage (26.9). |
| `RemoteProvider` | `engine/RemoteProvider.kt` | Vendor catalogue (connection/protocol + defaults): endpoint defaults, key requirements, base-URL configurability, wire/client mapping metadata. | `EngineInstance`, `RemoteConnection`/backends, `RemoteModelCatalog`, `ModelBrowserMerge`, connection UI. | **Keep** (26.4 done). | Boundary clarified: provider = how to connect; provenance = `ModelMetadataSource`. Exposes the single explicit mapping `RemoteProvider.metadataSource` so the enums are not assumed equivalent. |

## Engine runtime / plumbing (in package; repackaging targets, not config/catalog domain)

| Type | File | Responsibility | Key call sites | Verdict | Rationale |
|---|---|---|---|---|---|
| `PlanningEngine` + `QuestionBatch` | `engine/PlanningEngine.kt` | Core engine contract and batch result. | All engines, registry, selector, repository/tasks, logging/decorators. | **Keep, split file**. | Core seam; 2 public types. |
| `KoogPlanningEngine` | `engine/KoogPlanningEngine.kt` | `PlanningEngine` over a `KoogPlanningBackend`. | `EngineRegistry` (Remote). | **Keep**. | Runtime impl. |
| `KoogPlanningBackend` | `engine/KoogPlanningBackend.kt` | Backend seam (model/JSON/streaming) for Koog engine. | `KoogPlanningEngine`, `RemoteKoogPlanningBackend`. | **Keep**. | Abstraction enabling the remote backend. |
| `RemoteKoogPlanningBackend` | `engine/RemoteKoogPlanningBackend.kt` | Koog client/backend over a `RemoteConnection`. | `EngineRegistry`. | **Keep, split file**. | Carries `RemoteConnection` (relocate, 26.9). |
| `OfflinePlanningEngine` | `engine/OfflinePlanningEngine.kt` | Built-in offline question library. | `EngineRegistry` (Builtin Offline). | **Keep**. | Default/fallback engine. |
| `LoggingPlanningEngine` | `engine/LoggingPlanningEngine.kt` | Decorator logging prompts/responses/raw capture. | Engine composition/DI. | **Keep**. | Cross-cutting runtime decorator. |
| `ForwardingPlanningEngine` | `engine/ForwardingPlanningEngine.kt` | Delegating base for decorators. | Decorator implementations (e.g. slowdown/logging patterns). | **Keep**. | Small reuse seam; verify live subclasses during moves. |
| `SlowDownPlanningEngine` | `engine/SlowDownPlanningEngine.kt` | Dev-tool delay decorator. | Dev tooling/DI composition (Phase 8-adjacent). | **Keep, repackage carefully**. | Dev-tool concern — keep out of "product catalog" neighbors (27.5). |
| `EngineInteraction` + `EngineDelayConfig` | `engine/EngineDelayConfig.kt` | Dev-tool delay config. | Slowdown/developer settings. | **Keep, split/repackage carefully**. | Dev-tool concern (27.5). |
| `PlanningContext` | `engine/PlanningContext.kt` | Prompt/transcript context rendering. | `KoogPlanningEngine`/prompt building, export (future). | **Keep**. | Prompt plumbing, not catalog. |
| `ModelIdentity` helpers | `engine/ModelIdentity.kt` | Alias/size/count parsing helpers (top-level, no class). | `RemoteModelCatalog` parsing, display/ranking inputs. | **Keep, relocate**. | Model metadata helpers; fits the model subpackage (27.4), not engine config. |

## Model catalog / curation

| Type | File | Responsibility | Key call sites | Verdict | Rationale |
|---|---|---|---|---|---|
| `ModelMetadataSource` *(renamed from `ModelSource`, 26.4)* | `engine/ModelMetadataSource.kt` | Metadata provenance (`Ollama`/`OpenAiCompatible`/`Anthropic`) — explains which fields can be known. | `RemoteModelInfo.source`, `RemoteModelCatalog` impl, `ModelBrowserMerge`, tests. | **Keep** (done). | Renamed for clarity and KDoc'd as provenance (not `RemoteProvider`); mapped explicitly via `RemoteProvider.metadataSource`. |
| `RemoteModelInfo` | `engine/RemoteModelCatalog.kt` | Canonical model record (id + `displayName` alias + context/size/capability metadata). | Catalog, `ModelRanker`/`ModelBrowserMerge`, `EngineViewModel` auto-select, connection UI. | **Keep, relocate/split**. | Core data; currently co-located with catalog contract/impl/UI state (4 public types). |
| `RemoteModelCatalog` (interface) | `engine/RemoteModelCatalog.kt` | Contract to list models / resolve context length for a provider. | `KoogRemoteModelCatalog`, `EngineViewModel`, fakes/tests. | **Keep, relocate/split**. | Enables fakes; separate from impl. |
| `KoogRemoteModelCatalog` | `engine/RemoteModelCatalog.kt` | HTTP/catalog impl (Anthropic bundled, OpenAI-compatible `/v1`, Ollama tags+show). | DI/provisioning, `EngineViewModel` (via interface). | **Keep, relocate/split**. | Substantial impl; decide file placement vs. contract in 27.2/27.4. |
| `RemoteModelListState` | `engine/RemoteModelCatalog.kt` | Browse UI state (`Idle`/`Loading`/`Loaded`/`Failed`); `Loaded.browserEntriesWithPulls()`. | `EngineViewModel` (`grep` confirms VM + `ConnectionDialog` + VM tests only; defined here). | **Keep, re-home**. | UI browse state living in the engine catalog file couples data layer to UI — move toward chrome/VM or its own file (26.7). |
| `ModelQuality` | `engine/ModelCuration.kt` | Quality axis for curation/ranking. | `ModelMeasurement`/`ModelProfiles`, `ModelRanker`, UI chips. | **Keep, split file**. | One of 6 public types in `ModelCuration.kt` (AGENTS). |
| `ModelSpeed` | `engine/ModelCuration.kt` | Speed axis for curation/ranking. | Curation/ranking/UI. | **Keep, split file**. | Same. |
| `ModelPresentationTier` | `engine/ModelCuration.kt` | Derived presentation grouping (`Recommended`/slower/adequate/unknown/discouraged). | `ModelRank`, `ModelRanker`, browser merge/UI. | **Keep, split/rename candidate**. | "Tier" noun collides with `EngineTier` (26.5). |
| `ModelRank` | `engine/ModelCuration.kt` | Ranked result: model + tier/quality/speed/reason/estimated. | `ModelRanker`, `ModelBrowserMerge`/`ModelBrowserEntry`, VM/UI. | **Keep, split file**. | Data side of the rank/ranker pair. |
| `ModelMeasurement` | `engine/ModelCuration.kt` | Measured seed record from eval rows. | `ModelProfiles.measuredSeeds`, `ModelRanker`. | **Keep, split file**. | Evidence backing curation. |
| `ModelProfiles` (object) | `engine/ModelCuration.kt` | Measured seeds, family heuristics, lookup/strong-id helpers. | `ModelRanker`, `ModelBrowserMerge` (`strongModelIds`), tests, `tools/eval_ollama_models.py` snippet expectations. | **Keep, split/rename file**. | Plan named `ModelProfiles.kt`; landed inside `ModelCuration.kt` with 5 siblings (26.6). |
| `ModelRanker` (object) | `engine/ModelRanker.kt` | Deterministic ranking over measurements/heuristics into `ModelRank`. | `ModelBrowserMerge`, `EngineViewModel` auto-select. | **Keep**. | Clean data-vs-logic split; already separate. |
| `ModelBrowserMerge` + `ModelBrowserEntry` | `engine/ModelBrowserMerge.kt` | Pure merge of installed + pullable curated models into ranked, `isPull`-marked entries. | `RemoteModelListState.Loaded.browserEntriesWithPulls()`, connection UI. | **Keep, split file**. | Small/testable; 2 public types. |

## Downloads / Ollama pull (kept after local-model removal)

| Type | File | Responsibility | Key call sites | Verdict | Rationale |
|---|---|---|---|---|---|
| `DownloadKind` | `downloads/DownloadModels.kt` | Download kind (`OllamaPull`). | `DownloadState`, downloader/VM/UI. | **Keep, split file**. | Narrowed per removal plan. |
| `DownloadStatus` | `downloads/DownloadModels.kt` | Lifecycle phase enum (`Queued`…`Cancelled`, `finished`). | `DownloadState`, `TaskBackedModelDownloader` status mapping, pull UI/tests. | **Keep, naming review**. | Phase enum vs. aggregate `DownloadState` — readable but easy to misread; also parallels `TaskStatus`. Decide rename to `DownloadPhase` only if low churn (26.8). |
| `DownloadHandle` | `downloads/DownloadModels.kt` | Download handle linking `id` to task id. | `ModelDownloader`/`TaskBackedModelDownloader`, VM/UI cancel. | **Keep, split file**. | Simple handle. |
| `DownloadState` | `downloads/DownloadModels.kt` | Observable aggregate snapshot of a download. | `ModelDownloader.downloads()`, pull chips/progress/cancel UI, tests. | **Keep, split file**. | The observed shape; distinct from `DownloadStatus` phase. |
| `ModelDownloader` (interface) | `downloads/ModelDownloader.kt` | Narrow pull API: observe, `pullOllama`, `cancel`. | `TaskBackedModelDownloader`, `EngineViewModel`/DI. | **Keep**. | Contract for fakes and the task-backed impl. |
| `TaskBackedModelDownloader` | `downloads/TaskBackedModelDownloader.kt` | Bridges Ollama pulls to `TaskRunner`/progress/metadata/cancel. | DI, `ModelDownloader` impl, tests. | **Keep**. | The live pull path. |
| `OllamaPullClient` / `OllamaPullEvent` / `KoogOllamaPullClient` | `downloads/OllamaPullClient.kt` | Streaming NDJSON pull contract + event + Koog/HTTP impl. | `TaskBackedModelDownloader`, tests. | **Keep, split file**. | 3 public types in one file; pull transport isolated from UI. |
| `EmaThroughput` | `downloads/EmaThroughput.kt` | Smoothed transfer-rate/ETA helper. | `TaskBackedModelDownloader` progress. | **Keep**. | Small, focused utility. |

## Confirmed fold candidates (evidence)

- `EngineKind.available()` — `grep '\.available()' shared/src` returns only
  `EngineKindTest` assertions; no production call sites. Availability is derived
  via `EngineCatalog`/`EngineAvailability` (26.3).
- No obviously dead plan-created class found in this pass; the decorator/base
  types (`ForwardingPlanningEngine`, `PlanningEngineSelector`) have live roles
  but should be re-checked when 27.1 defines the runtime subpackage.

## Notes for 26.x / 27.x

- `engine/` mixes config/identity, model catalog/curation, and runtime/plumbing.
  27.1 should lock a package map + dependency direction before moves
  (suggested: `engine.core` / `engine.runtime|koog` / `engine.config` /
  `engine.model`), keeping `ui/chrome` and `downloads` dependencies one-way.
- One-type-per-file (AGENTS) is violated in `ModelCuration` (6), `RemoteModelCatalog`
  (4), `DownloadModels` (4), `OllamaPullClient` (3), and several 2-type files
  (`EngineCatalog`, `ModelBrowserMerge`, `RemoteKoogPlanningBackend`,
  `PlanningEngine`, `EngineDelayConfig`) — normalize in 27.2.
- `RemoteModelListState` is a UI concern defined in the engine catalog
  (confirmed by call sites) — re-home in 26.7.
- Dev-tool delay types (`SlowDownPlanningEngine`, `EngineInteraction`,
  `EngineDelayConfig`) must not land beside product catalog types (27.5).
- Suggested execution order (status): `26.1(done) → 26.2(done)/26.3(done)/26.4(done)/26.5 → 27.1 → 26.6/26.7/26.9/27.2 → 27.3/27.4/27.5 → 26.8 (optional) → 27.6`.
