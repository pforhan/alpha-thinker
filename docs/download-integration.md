# Download Integration Framework

This document outlines a concrete, actionable framework for tracking downloads in Alpha Thinker. It aligns with [MODEL-CATALOG-PLAN.md](MODEL-CATALOG-PLAN.md) Stage 3 and reuses existing infrastructure (`TaskRunner`, `ActivityLogger`, `TaskManager`) rather than creating parallel systems.

## 1) Extend the Task System to be Download-Aware

The existing task infrastructure already provides queuing, serialization, progress tracking, cancellation, and logging. We should extend it to support downloads.

### TaskKind Additions

Add download-specific task kinds with appropriate grouping:

```kotlin
enum class TaskKind {
  QuestionGeneration,
  TitleRecommendation,
  SynopsisRewrite,
  AutoArchive,
  // Downloads
  OllamaModelPull,      // triggered server-side download (Ollama)
  ModelFileDownload,    // in-process download of model files
  ModelVerification,    // checksum/extraction/integrity verification (often short)
  ;

  val group: ConcurrencyGroup
    get() = when (this) {
      QuestionGeneration, TitleRecommendation, SynopsisRewrite, AutoArchive -> ConcurrencyGroup.Engine
      OllamaModelPull, ModelFileDownload, ModelVerification -> ConcurrencyGroup.Download
    }
}
```

### ConcurrencyGroup Additions

Downloads are I/O-heavy (network/disk bound) and should not block engine work:

```kotlin
enum class ConcurrencyGroup(val concurrency: Int) {
  Engine(concurrency = 1),
  Remote(concurrency = 4),
  Download(concurrency = 2), // tune per platform; maybe 1 on metered networks
}
```

### GenerationTask Metadata

`GenerationTask` already has `progress: Float?` (0..1). For downloads, we need additional context for UI display (e.g., "2.1GB / 4.7GB (45%)" and transfer speed).

**Recommendation:** Extend `GenerationTask` with optional download-specific fields (backwards compatible):

```kotlin
data class GenerationTask(
  // ... existing fields ...
  val progress: Float? = null,
  // Download-specific (optional)
  val modelId: String? = null,
  val source: String? = null,        // e.g., "ollama", "huggingface", "litert"
  val bytesTotal: Long? = null,
  val bytesDone: Long? = null,
  val throughputBps: Long? = null,
  val downloadId: String? = null,
)
```

Add a helper to `TaskRunner` like `setDownloadProgress(taskId, bytesDone, bytesTotal, throughputBps?)` that updates both the fraction-based `progress` and the metadata fields.

### TaskLabels Updates

Add titles and progress labels for the new task kinds so `TaskManager` displays them correctly.

## 2) Abstractions for the Two Download Types

Create a `downloads/` package with interfaces to enable testing and swappable implementations.

### Core Interfaces

```kotlin
interface ModelDownloadManager {
  fun downloads(): Flow<List<DownloadState>>
  suspend fun pullOllama(modelId: String, baseUrl: String): DownloadHandle
  suspend fun downloadModel(spec: ModelDownloadSpec): DownloadHandle
  suspend fun cancel(handleId: String)
}

data class DownloadHandle(val id: String, val taskId: String)

data class DownloadState(
  val handleId: String,
  val taskId: String,
  val kind: DownloadKind,
  val modelId: String,
  val status: DownloadStatus,
  val bytesDone: Long? = null,
  val bytesTotal: Long? = null,
  val progress: Float? = null, // 0..1
  val message: String? = null,
  val source: String? = null, // "ollama", "litert"
)

enum class DownloadKind { OllamaPull, LocalFile }
enum class DownloadStatus { Queued, Running, Paused, Succeeded, Failed, Cancelled }
```

### Ollama Pull (Process-Driven, Best-Effort)

Ollama pulls are server-side operations streamed via the API:

- **Client:** Add `OllamaPullClient` that calls `POST /api/pull` with `{"model": ..., "stream": true}` using the same `KoogHttpClient` pattern as `RemoteModelCatalog` (normalize base URL, handle `/v1` stripping).
- **Streaming:** Read NDJSON lines via `client.lines()` or streaming APIs. Parse each JSON object: `status`, `digest`, `total`, `completed`, `error`.
- **Status Mapping:**
  - `pulling manifest` → log phase, indeterminate or small progress
  - `pulling <digest>` → update bytes/progress per digest/layer
  - `verifying sha256 digest`, `writing manifest`, `removing any unused layers` → log phases, may be indeterminate
  - `success` → complete task successfully, refresh Ollama model list
  - `error`/cancel → fail task with message
- **Cancellation:** Close the HTTP stream/client. Ollama resumes on next pull, but we mark as cancelled in our task log. Background execution uses app scope (no UI lifecycle binding).
- **Logging:** Log start (`started: ollama pull <model>`), each phase, and end (`succeeded`/`failed`/`cancelled`) via `ActivityLogger.taskContext(taskId, projectId = null)` since downloads may not be project-scoped.
- **On Completion:** Call `RemoteModelCatalog.list(Ollama, ...)` or notify listeners to refresh; consider auto-selecting if it was the requested model.

### In-Process Downloads (Fully Tracked)

For direct model file downloads (e.g., LiteRT-LM models):

- **Downloader:** `LocalFileDownloader` for direct HTTP(S) downloads to app storage (platform-specific paths via `PlatformContext`). Support resume (Range requests), checksum verification (sha256), extraction if needed, and disk space checks.
- **Progress:** Track bytes read/written, throughput, ETA. Update task in real-time via `TaskRunner` helpers.
- **Cancellation:** Cooperative cancellation (close streams, delete partial files).
- **Verification:** After download, verify checksum → either as a `ModelVerification` task or as a phase in the same task.
- **On Completion:** Create/update `EngineInstance` of kind `LocalFile` with `localPath`, update `EngineCatalog` availability (make Local tier real), register with the engine system.

## 3) Integration Points

| Component | Integration |
|-----------|-------------|
| **DI (`AppComponent`)** | Add `OllamaPullClient`, `LocalFileDownloader`, `ModelDownloadManager`. `TaskRunner` already exists. |
| **EngineRepository/EngineCatalog** | When a local model is downloaded, add `EngineInstance(kind = LocalFile, localPath = ...)`. Replace TODO in `EngineCatalog` (line ~92) with actual file existence/checksum validation. |
| **RemoteModelCatalog** | Rely on `list(Ollama, ...)` after pull to refresh installed models. UI needs to distinguish installed vs pullable (curated) models. |
| **Settings/EngineViewModel** | Add actions to pull/download models. For Ollama, only show curated strong models from `docs/model-evals/all.md` (not a full browser). |
| **ConnectionDialog (Advanced)** | Show "Pull" for uninstalled curated Ollama models with inline progress/cancel. Refresh on completion. |
| **TaskManager** | Downloads appear automatically with proper labels/progress (already supported). |
| **EnginePickerSheet (Local tier)** | Replace placeholder with list of downloadable models + installed models; "Download" starts `ModelFileDownload` task. |

## 4) UI/UX Specifics

- **Ollama curated list:** When connected to Ollama, display only strong quality models from `docs/model-evals/all.md`. Distinguish installed vs not installed. For not installed, show "Pull" button.
- **Pull flow:** Tapping "Pull" calls `ModelDownloadManager.pullOllama(...)` which enqueues a task via `TaskRunner`. Show inline progress or navigate to `TaskManager`.
- **Local models:** In the Local tier, show downloadable models + installed local models. "Download" starts a `ModelFileDownload` task.
- **Progress display:** Use `TaskSpinner` + progress bar. Show bytes/percent/speed. Allow cancel and background execution (background is default via app scope).
- **Hostability:** Check memory/size using heuristics from [llm-memory-fit.md](llm-memory-fit.md). Flag models that won't fit the device budget next to size/context chips (note, not filter). Consider color changes for outliers. Apply to local models and Ollama instances on localhost. MLX versions may only run on recent Macs.
- **Logging:** Log all download events (start, phases, errors, cancellation, success) via `ActivityLogger`.

## 5) Concrete Implementation Steps (Actionable Order)

### Step 1: Extend Task Framework (Foundational)
- [ ] Add `Download` to `ConcurrencyGroup`
- [ ] Add download kinds to `TaskKind` and update `TaskLabels`
- [ ] Extend `GenerationTask` with download metadata (optional, backwards compatible)
- [ ] Add helpers to `TaskRunner` for download progress updates

### Step 2: Ollama Pull Client
- [ ] Add `OllamaPullClient` using `KoogHttpClient` for streaming NDJSON
- [ ] Handle base URL normalization like `RemoteModelCatalog` (strip `/v1`)
- [ ] Parse all status messages and track bytes/progress
- [ ] Implement cancellation via stream closure

### Step 3: ModelDownloadManager + Integration
- [ ] Implement `ModelDownloadManager` that enqueues tasks via `TaskRunner`
- [ ] Create `OllamaPullClient` and `LocalFileDownloader` implementations
- [ ] Wire into `AppComponent`
- [ ] Add to `EngineViewModel` or create `DownloadViewModel`

### Step 4: UI for Ollama Pulls (Curated)
- [ ] Update model browser in `ConnectionDialog` to show curated models from eval data
- [ ] Distinguish installed vs pullable, show "Pull" affordance
- [ ] Handle progress/cancel inline or via navigation to `TaskManager`
- [ ] Refresh model list on completion

### Step 5: Local File Downloads (Scaffold)
- [ ] Add `LocalFileDownloader` interface + platform actuals
- [ ] Add UI in Local tier of `EnginePickerSheet`
- [ ] Integrate with `EngineInstance` creation (kind = `LocalFile`)
- [ ] Update `EngineCatalog` availability for `LocalFile` (replace TODO)
- [ ] Add `ModelVerification` flow

### Step 6: Hostability + Polish
- [ ] Implement memory fit heuristics per `llm-memory-fit.md`
- [ ] Apply hostability flags to chips (local models and localhost Ollama)
- [ ] Add comprehensive logging throughout
- [ ] Add on-device evaluation harness similar to `tools/eval_ollama_models.py` for validating LiteRT-LM models
- [ ] Add tests

## Notes on Requirements

- **Task tracking:** Use `TaskRunner` for all long-running download operations
- **Logging:** Use `ActivityLogger.taskContext(taskId, projectId = null)` for download events (start, phases, end, cancelled, errors)
- **Progress/cancel/background:** Progress via `setProgress`/metadata, cancel via closing streams + task cancellation, background via app scope
- **List updates:** Refresh `RemoteModelCatalog` for Ollama after pull; update `EngineRepository`/`EngineCatalog` for local files
- **Reuse patterns:** Leverage `TaskRunner`, `TaskManager`, `ActivityLogger` just like other long-running operations

This framework aligns perfectly with the existing codebase patterns. Start with Step 1 (task framework) as everything else builds on it.
