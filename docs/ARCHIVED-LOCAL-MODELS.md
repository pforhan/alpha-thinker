# Archived: Local Models, Local Downloads, and the On-Device Eval Harness

This file preserves the design and task list for the local-model track that
Alpha Thinker **removed rather than finished**. It is history, not a roadmap:
nothing here is scheduled, and the code it describes no longer exists.

The live plan is [MODEL-CATALOG-PLAN.md](MODEL-CATALOG-PLAN.md), which now covers
remote models only — discovery, curation, ranking, and the **Ollama pull**. The
removal itself is tracked in that plan's
[Removal plan](MODEL-CATALOG-PLAN.md#removal-plan--local-models-local-downloads-and-ondevice).

## What was cut

- **Local-file models.** `EngineKind.LocalFile`, the `Local` tier, LiteRT-LM
  inference (`LocalModel*`, `LocalBackendFactory`, `LocalKoogPlanningBackend`,
  `LocalStreamingBackend`), the `.litertlm` catalog/store, and the local-file
  transfer client.
- **The local-model manager UI.** `LocalModelsDialog` and the Local tier in
  `EnginePickerSheet`.
- **The in-app on-device eval harness.** The `eval/` package, `ModelEvalScreen`,
  the LAN receiver (`tools/eval_receiver.py`, `tools/eval_tls/`), the per-device
  report script, and `docs/MODEL-EVAL-HARNESS.md`.
- **The never-shipped `OnDevice` engine kind** and its system-model runtime.

Kept: the Ollama pull path, remote-model metadata/curation/ranking, the grouped
model browser, `tools/eval_ollama_models.py`, and `docs/model-evals/`.

## Stage 3 items that were cut

These are the original task descriptions, retained for reference.

### Local file downloads (LiteRT-LM, in-process)

- **Local file downloads (LiteRT-LM, in-process):** Add `ModelFileTransferClient`
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

### On-device evaluation harness

- **On-device evaluation harness:** Build an in-app tool similar to
  `tools/eval_ollama_models.py` to capture timing, ok/clean/json rates,
  memory/footprint estimates, and timeouts for validating models
  and extending the curated set. And write a script for a computer that lets me
  share this data on a local network back to my computer to add to our data sets.
  If we aren't already we should be grouping by hardware and os to form per-device
  recommendations. Example: the two llama3.2 models we picked are slow and unable
  to comply on phone, usually filling their output buffers with nonsense.
  Phi4-mini seems to work though, at least on a Nothing 3. The tool should expect
  and ideally handle OOM crashes. If it can't do it in-process then record enough
  data during the test that we can pick it up next launch.
  Implemented as a Testing-sheet sweep that persists a resumable row per
  `(model, run)` and groups results by hardware/OS. `tools/eval_receiver.py`
  brings those rows back over the LAN and `tools/eval_report_by_device.py`
  writes per-device recommendations.
  - Do not use `usesCleartextTraffic` in the Android manifest — either set the
    script up to make an HTTPS server (preferred) or use a Network Security
    Config file. The receiver now serves HTTPS with a checked-in certificate
    pinned by the app, and the manifest replaced the flag with a network security
    config that allows only loopback HTTP.
  - Should be able to view and test a wider array of models from the community
    than we display in local engine. Related: need to manage the downloads like
    that dialog. Code may be sharable?
  - If currently running just say a model is in progress — not to run again.
  - We should narrow the timeout measurements across the board. Perhaps 30s on
    each request? Take a look at current data to see the impact it could have. If
    some models go over, but don't have good quality, the shorter time would still
    benefit. Current data shows 30 s changes no recommendation tier but lowers
    measured quality for eleven slow models, so the harness keeps 120 s and aborts
    repetition loops within seconds.
  - This tool doesn't need the engine status and popup menu in the top bar.
  - Is quality a percentage or what? Display a % or indicate its max value.
  - Need a way to clear results.

### Local-model UX and platform work

- **Model sharing:** Ability to share models with nearby devices using industry
  standards (Quick Share, AirDrop, etc.) — and between platforms if these models
  are platform independent.
- **Robust mobile downloads:** Consider using native download mechanisms (or
  industry standard ones) on Android and iOS to better deal with common mobile
  interruptions like sleep, network problems, etc.
- **Local-model management UX:** Don't add new rows to "Advanced" for local
  models — always use the local model dialog. Only delete a model when deleted
  via that dialog. Use selectable cards for models instead of the use button. If
  a model is not fully downloaded, start a download for it. Keep the ability to
  cancel a download in progress. Display partial downloads and allow them to be
  removed. If we need to keep these as distinct records, that's fine, though
  probably shouldn't show them or allow them to be edited. Just make sure the
  records are accurate. Example: currently the edit dialog shows them as
  half-filled Ollama models.
- **Make the Local tier real:** Enable Local placeholder rows per
  ENGINE-CONFIG-PLAN.md stage 3; downloaded models render as
  `EngineKind.LocalFile` instances under Local (platform-gate; web/wasm
  deferred). Done with the local-file downloads item above: the Local card opens
  the model manager and its installed instances render under Local wherever the
  runtime is supported.
- **LiteRT beta note:** The LiteRT client may require a beta version of Koog (see
  IMPLEMENTATION-PLAN.md Phase 5, "Revisit a newer Koog release when one lands").
