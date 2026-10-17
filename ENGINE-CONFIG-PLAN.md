# Engine Catalog & Configuration Plan

This plan reworks how Alpha Thinker models, stores, validates, and presents
its LLM engines (planning backends). It is a dedicated tracker for work that
was previously sketched as a cluster of loose items in
[IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) Phase 3.7:

- "RemoteProvider may be an extra layer we don't need with engine and backend"
- "Values for model and context carry over as you select different remote types"
- "Cross-provider hygiene"
- "Come up with a good way to present LLM backend ever-growing list"
- "Allow multiple remote of different kinds of endpoints to be defined"
- "Reconsider `EngineMode` as a class once remote backends become instances"

It is presented alongside [MODEL-CATALOG-PLAN.md](MODEL-CATALOG-PLAN.md), which
covers the models *inside* an engine — their discovery, curation
(Verified / Recommended / Others), and acquisition (Ollama pull, downloaded
local models). This plan's stage 3 leaves room in the picker for that plan's
Local tier, which becomes real when its downloads land.

**Status: in progress.** Stage 1's foundation has landed and is wired in:
`EngineMode` renamed to `EngineKind`, the `EngineRef` / `EngineInstance` /
`EngineTier` / `EngineState` identity types added, the `engine_instances` +
`engine_state` storage plus a tested `EngineRepository`, `EngineStatus` derived
from the selected ref, and `AppComponent` selecting through `EngineRegistry`
(the two parallel `EngineMode`-keyed maps and `DynamicKoogPlanningBackend` are
gone). Stage 2a has landed too: the selected instance's fields drive
`SettingsViewModel` (one `remoteConnection` `StateFlow`) and the remote backend,
and the flat `RemoteLlm*` keys are retired. Open in Stage 2: 2b (connections CRUD
UI), 2c (per-ref self-validation), 2d (test checklist). Each stage is intended to
land independently, green on `./gradlew :shared:desktopTest`.

---

## Problem

The engine layer carries three axes that do not compose:

- `EngineMode` (`shared/.../engine/EngineMode.kt`) is a fixed four-entry enum
  (Offline / OnDevice / Remote / Downloaded) whose `Remote` entry is stretched
  to cover every provider — it cannot express "which connection".
- `RemoteProvider` (`shared/.../engine/RemoteProvider.kt`) is the vendor
  catalogue (defaults, key requirements, base-URL configurability, model
  listing) but it is applied globally, not per connection.
- `KoogPlanningBackend` / `RemoteKoogPlanningBackend` /
  `DynamicKoogPlanningBackend` build the Koog client by switching on that global
  provider.

Consequences:

- Engine settings are **flat and single-instance** (`SettingsKey.RemoteLlmBaseUrl`
  etc., `shared/.../database/Storage.kt`), so values carry across providers and
  there is no per-connection identity — which downloads and pulls both need.
- `AppComponent` holds **two parallel `EngineMode`-keyed maps**
  (`shared/.../di/AppComponent.kt`), only `Remote` populated, and
  `DynamicKoogPlanningBackend` is an indirection whose unbound-mode throw is
  unreachable.
- `IntelligenceSheet` renders **four flat archetype cards** with no tiers and no
  advanced area, and there is **no first-run handling** anywhere.

## Decisions (locked)

1. **Instance refactor.** An `EngineInstance` / `EngineRef` becomes the unit of
   identity; `EngineKind` replaces `EngineMode` as the archetype.
2. **`engine_instances` table** stores user-created engines.
3. **First run is the same picker** as every later engine change — no separate
   onboarding gate or tutorial flow.
4. **`RemoteProvider` survives**, scoped to a field *inside* a Remote instance.
   It is not an extra layer; it was only the wrong scope while it was global.
5. **Split storage.** Catalog definitions live in code; `engine_instances` holds
   user instances; `engine_state` (keyed by `EngineRef`) holds `selected` and
   `lastUsedAt`, which gives LRU ordering without promoting built-ins to rows.

## Target model

```kotlin
enum class EngineKind { Offline, OnDevice, LocalFile, Remote }

enum class RemoteProvider { Ollama, OpenAiCompatible, Anthropic, AlphaInterplanetary }

enum class EngineTier { Offline, Free, Local, Advanced }   // presentation grouping

sealed interface EngineRef {
  data class Builtin(val kind: EngineKind) : EngineRef
  data class Instance(val id: String) : EngineRef
}

/** A user-created engine. Rows exist only for these. */
data class EngineInstance(
  val id: String,
  val kind: EngineKind,
  val provider: RemoteProvider?,   // Remote only
  val label: String,
  val baseUrl: String,
  val apiKey: String,
  val model: String,
  val contextTokens: Int,
  val localPath: String?,          // LocalFile only
)

/** A code-defined built-in entry (Offline, Free/Alpha Interplanetary, On-device). */
data class EngineCatalogEntry(
  val ref: EngineRef,
  val kind: EngineKind,
  val provider: RemoteProvider?,
  val tier: EngineTier,
  val label: String,
  val description: String,
  val availability: EngineAvailability,
)
```

The picker is a pure merge — `EngineCatalog.entries(instances, state)` — grouped
by `EngineTier` and ordered by `lastUsedAt`.

- **Availability/validation lifts to the ref/instance** and feeds the existing
  `CapabilityState.Active / Incomplete / Unused` (`ui/chrome/EngineStatus.kt`):
  a Remote missing its model or key reads "needs setup", not "active".
- **`EngineRegistry`** builds and caches a `PlanningEngine` per `EngineRef`
  (backend → Koog client → `Logging(SlowDown(...))`), replacing
  `resolveSelectedEngine`, both `EngineMode`-keyed maps, and
  `DynamicKoogPlanningBackend`.

## Storage

- `engine_instances` — one row per user engine (Remote connection or downloaded
  model). Own DAO; one type per file per AGENTS.md.
- `engine_state` — one row per `EngineRef` that has ever been selected/used:
  `selected: Boolean` and `lastUsedAt`. Built-ins appear here only once touched.
- Retire the flat `SettingsKey.RemoteLlm*` keys (destructive migration era —
  no real migration required). `EngineMode` setting is replaced by the
  `selected` flag in `engine_state`.
- Both tables ride the existing `fallbackToDestructiveMigration()` path in
  `AppDatabase`.

Prefer a dedicated **`EngineRepository`** for instance CRUD + selection + usage,
leaving `SettingsRepository` for app settings (phase theme, engine delay,
retention, announced-failure id). This matches the one-concern-per-type style
already applied to the DAOs.

---

## Stages

### Stage 1 — Engine instance foundation

- [x] Add `engine/EngineKind.kt` (rename of `EngineMode`), `engine/EngineInstance.kt`,
      `engine/EngineRef.kt`, `engine/EngineTier.kt`, `engine/EngineCatalog.kt`
      (built-in entries + tiers + availability), `engine/EngineRegistry.kt`.
- [x] Add `engine_instances` + `engine_state` tables, entities, and DAOs.
- [x] Extend `Storage` with instance/state accessors; retire `RemoteLlm*` keys.
- [x] Introduce `EngineRepository` (instance CRUD, selected ref, `lastUsedAt`),
      seeded on first read with one Remote instance from `RemoteProvider.Default`.
- [x] `EngineStatus` / capabilities / `EngineStatusCopy` derive from the selected
      `EngineRef` rather than a mode.
- [x] `AppComponent`: collapse the two parallel `EngineMode`-keyed maps
      (`AppComponent.kt:88-95`, identical keys) into the registry; select through
      `EngineRegistry`.
- [x] **Delete `DynamicKoogPlanningBackend`.** It is a mode→backend indirection
      over `backends[mode]` whose sole call site passes a map whose single key is
      already that mode, so its `?: throw` is unreachable — and the construction
      that would make it reachable is also what makes it redundant. Its
      fail-loudly-unbound-mode guarantee only paid for itself by requiring a
      second `EngineMode`-keyed map, and once the map key becomes an `EngineRef`
      (the multi-endpoint case) the wrapper stops earning that cost. (This
      answers the deferred "revisit `DynamicKoogPlanningBackend`" item formerly
      in IMPLEMENTATION-PLAN.md Phase 3.7; its `source`-shadowing half was
      already fixed by the Phase 3.6 C-group cleanup.)
- [x] `IntelligenceSheet` renders from `EngineCatalog` in its current card shape
      (no visual redesign yet) so nothing regresses mid-refactor.
- [x] delete `DynamicKoogPlanningBackendTest`
- [x] Update tests with these changes (EngineStatusTest.kt and RemoteKoogPlanningBackendTest.kt 
      temporarily deleted.)

**Proof:** `./gradlew :shared:desktopTest`.

### Stage 2 — Per-instance config & multi-endpoint

- [x] **2a1. Data access.** Add helpers on `EngineRepository`/`EngineInstance` to
      read & update the selected instance's fields (`baseUrl`, `apiKey`, `model`,
      `contextTokens`, `provider`), persisting via `addOrUpdate`, with no flat
      settings reads.
- [x] **2a2. ViewModel migration.** Rewrite `SettingsViewModel` to expose the
      selected instance's fields as `StateFlow`s and mutators that call 2a1;
      keep `announcedFailureActivityId` from `SettingsRepository`.
- [x] **2a3. Provider-switch defaults.** On provider change of the selected
      instance, recompute `model`/`contextTokens` from provider defaults when
      the instance hasn't been hand-edited. "Provider defaults" means a
      metadata lookup where the provider supports it (e.g., Ollama model
      metadata) and code-defined defaults where it doesn't (e.g.,
      OpenAI-compatible endpoints). A switch re-seeds the previous provider's
      starter endpoint, model, and window, then refines the window from the new
      provider's metadata; a field the user has hand-edited is kept.
- [x] **2a4. Tests.** Rewrite `SettingsViewModelTest` for the above (and
      `EngineRepositoryTest` coverage of field edits).
- [x] 2a5. Retire the flat RemoteLlm* settings. SettingsViewModel no longer reads them, but SettingsRepository still declares/loads/persists remoteLlmProvider/BaseUrl/ApiKey/Model/ContextTokens, Storage.SettingsKey still holds the five RemoteLlm* keys, and SettingsRepositoryTest still exercises them. Delete all of it (and the now-unused defaults/imports). 
- [x] 2a6. Engine-layer cleanup. RemoteKoogPlanningBackend no longer needs its SettingsRepository import/doc (it takes a RemoteConnection); resolveSelectedEngine + PlanningEngineSelectionTest are dead after the EngineRegistry swap, and PlanningEngineSelector's doc and AppComponent's import still name SettingsRepository.
- [ ] **2c. Per-ref self-validation.** On-device and LocalFile availability feeds
      `CapabilityState` (file present, runtime available).
- [ ] **2d. Tests.** EngineStatusTest, EngineRegistryTest, RemoteKoog... etc per checklist.
  **Test checklist (Stage 2 deliverables):**
- [ ] **EngineRepositoryTest** — create / update / delete; `addOrUpdate`
  replaces in place; selection persists across repository load (seeded
  default Remote only when storage is empty); delete-selected falls back to
  `EngineRef.Builtin(Offline)`; `markUsed` upserts an `engine_state` row
  carrying `lastUsedAt`; built-ins never appear in `instances`.
- [ ] **EngineStatusTest (rewritten for ref/instance, pull from git history if original needed)** — `Builtin(Offline)`
  → all slots `Unused`; complete `Instance` Remote → all `Active` with detail
  sourced from instance fields; missing model/key → `Incomplete` +
  `needsAttention`; Anthropic without key → Incomplete even at its fixed
  origin; Anthropic with key → fixed origin as active; OnDevice/LocalFile
  instances → Llm-only Active with mode label; `Tools` remains `Unused` for
  every ref kind.
- [ ] **EngineRegistryTest** — `getEngine(Offline)` returns
  `OfflinePlanningEngine`; a Remote instance returns a `KoogPlanningEngine`
  whose backend reads only from its instance fields; repeat calls for the
  same ref return the identical cached engine; `invalidate(ref)` forces a
  rebuild (second factory creation visible); no flat storage reads.
- [ ] **RemoteKoogPlanningBackendTest (rewritten, pull from git history if original needed)** — constructor takes a
  `RemoteKoogPlanningBackend`-compatible `RemoteConnection` (not a
  `SettingsRepository`); provider dispatch, base-URL normalization, `/v1`
  stripping, window carryover all derived from connection data; rebuild
  triggered only by changed target; no listener-based state rebuilds.
- [ ] **SettingsViewModelTest (rewritten)** — `selectedRef` starts at
  `Builtin(Offline)`; `selectEngine(ref)` updates `selectedRef` and
  persists via repository; `announcedFailureActivityId` still flows out of
  `SettingsRepository`; multiple instances coexist with independent field
  edits and no cross-talk.
- [ ] **AppChromeStateTest (new)** — the `engineStatus` flow emits a
  refreshed derivation whenever `selectedRef` changes or a field of the
  selected instance changes; no stale reuse of previously cached backend.

**Requirement:** The flat `RemoteLlm*` settings keys are fully retired.
Configuration lives exclusively in `EngineInstance` fields, and the
`EngineStatus` / `EngineRegistry` wiring of Stage 1 no longer reads those flat
values. `engineStatus(ref, instance)` and `EngineRegistry.getEngine(ref)` must
both derive their truth from the same `EngineInstance`, with `EngineRepository`
as the sole owner of selection and `lastUsedAt` ordering. Every remote
backend exists for a single instance, receives its endpoint/key/model/window
at construction, and is rebuilt via `invalidate(ref)` whenever its instance
changes.

**Proof:** `./gradlew :shared:desktopTests`.

### Stage 3 — Presentation

- [ ] **Moved from 2: Connections CRUD UI.** Advanced list screen: add / edit / delete /
  select an instance; deletion of the selected falls back to `EngineRef.Builtin(Offline)`.
  implement along with the rest of stage 3 to keep everything consistent.
- [ ] One `EnginePickerSheet`, used for first run and every later engine change:
      visible tiers **Offline / Free (disabled placeholder) / Local (disabled
      until MODEL-CATALOG-PLAN.md stage 3)**; an **Advanced** disclosure holding
      Remote connections and On-device.
- [ ] Recast status pills and the header flyout per selected `EngineRef`.
- [ ] Retire `IntelligenceSheet` (superseded by `EnginePickerSheet`); keep the
      `ChromeSheet` route stable where possible.

**Proof:** `./gradlew :shared:desktopTest`; eyeball via `:desktopApp:run`.

### Related — companion plan

Model-level work (metadata, curation, and downloads) is tracked separately in
[MODEL-CATALOG-PLAN.md](MODEL-CATALOG-PLAN.md). It depends on this plan's
stages 1–2 and enables this plan's stage 3 Local tier.

---

## Blast radius

- ~126 `EngineMode` references and ~113 `RemoteProvider` references across
  `shared` (main + commonTest).
- Affected tests: `SettingsViewModelTest`, `SettingsRepositoryTest`,
  `EngineStatusTest`, `EngineStatusCopyTest`, `PlanningEngineSelectionTest`,
  `DynamicKoogPlanningBackendTest` (delete).
- Two new tables ride the existing destructive migration; retiring the flat
  remote settings keys is safe pre-release.

## Out of scope

- An onboarding gate or guided tutorial (first run reuses the picker).
- The actual Alpha Interplanetary backend (placeholder entry only).
- The system on-device runtime beyond its tier entry; per-capability toggles
  (already parked in IMPLEMENTATION-PLAN.md Phase 4).
