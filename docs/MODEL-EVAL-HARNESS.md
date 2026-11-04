# On-device model evaluation harness

The app can measure the LiteRT-LM models it actually runs, on the phone it runs
them on. It is the app-side counterpart to
[`tools/eval_ollama_models.py`](../tools/eval_ollama_models.py): the fixtures,
prompts, scoring, row shape, and quality/speed bands are the same, so a row
measured on a phone is directly comparable with the Ollama rows already in
[`docs/model-evals`](model-evals/).

The point is per-device evidence. A model that is fast on a laptop can be
unusable on a phone, so results are grouped by hardware and OS and are never
pooled across devices.

## Running a sweep

Open **Testing** and choose **Evaluate local models**. The screen lists the
curated models whose `.litertlm` file is already installed.

- **Runs per cell** — `1` or `3`. More runs smooth out latency noise.
- **Start over** — default is to resume an interrupted row for the same model,
  device, and run count. Turn this on to discard it and measure every cell again.
- **Cancel** — stops after the request in flight. The partial row is kept, so
  the next start resumes from the cell it reached.

A sweep always uses a fresh backend for each model and the same
`LocalBackendFactory` a real planning request uses. Local inference only exists
on Android today, so that is the only target that can produce new rows; desktop,
iOS, and wasm can still read and share rows.

## What is measured

Each model pays one unscored **warmup** (the cold load) and then runs every
fixture × interaction once per run. The app's suite has three fixtures
(`simple-product`, `home-project`, `software-plan`) and three interactions
(`title`, `initial_questions`, `summarize`), for **9 scored cells per run**.

Per cell the row records:

| field | meaning |
|---|---|
| `latency_s`, `wall_s` | request time, in seconds |
| `timings.gen_tps` | output tokens divided by wall time, when the backend reports tokens |
| `raw` | the reply verbatim, so scoring can be recomputed later |
| `error`, `timeout` | the failure and whether it was a timeout |

The model-level report derives:

- **ok** — the reply was usable at all (non-empty title/summary, parseable
  question array).
- **clean** — it also followed the formatting rules the app's prompts ask for
  (title under 60 characters with no quotes, period, preamble, or newline;
  summary under 150 words with no bullets, headings, or preamble).
- **json** — the question reply parsed as JSON.
- **quality** — `0.5 × json + 0.3 × clean + 0.2 × ok`, renormalised over the
  components a row actually has, so a partial row is not punished for cells it
  never reached.
- **suite_s** — the average time for one pass over the whole suite. Failed and
  timed-out cells are excluded, so read it beside the timeout/error counts.
- **footprint** — `EvalMemory` estimates resident size from the weight file,
  or from parameters × quantization when the file size is unknown, adding the
  same 15% plus 1 GB of runtime headroom as the Python harness.

Bands match the Python report:

- quality: strong ≥ 98, adequate ≥ 85, otherwise weak.
- speed: fast < 10 s, fine < 35 s, otherwise slow.

The app labels that grid with its own presentation tiers: **Recommended** =
strong × fast, **High-quality but Slower** = strong × (fine or slow),
**Lower-quality but Fast** = adequate × (fast or fine), **Discouraged** = any
other measured combination, and **Unknown** when an axis has no data. The tier is
a label, never a filter.

## Rows, storage, and resume

One self-describing JSON row is written per `(model, run)`:

- Android: `<app filesDir>/evals/`
- desktop: `~/.alphathinker/evals/`

The row is flushed after the warmup and after **every** cell. That is what
makes a sweep safe to interrupt: a process reclaimed for memory leaves a row
with `in_progress: true`, its warmup, and every cell that landed. The next
launch's **Run or resume** skips those cells.

The row schema mirrors the Python harness (`schema: 1`, `fixtures_version: 1`,
`model_id`, `model`, `run`, `warmup`, `cells`, `started_at`, `finished_at`,
`in_progress`). Two additions describe the device:

- `run.hardware` — platform, OS and version, device, CPU, RAM, app version, API
  level where the platform can report them.
- `device_key` — a stable grouping key derived from those fields. Report
  grouping always uses this key (or re-derives it for rows written before the
  field existed), so a phone row is never averaged with a desktop row.

Rows store raw replies and timing, not scores. Tightening `EvalScoring` re-scores
every existing row at read time; only a structural change bumps `schema`, and
only a prompt/fixture change bumps `fixtures_version`.

Changing a prompt or adding a fixture is a **fixtures-version bump**, not a local
edit. `EvalFixtures` is deliberately byte-compatible with the Python harness so
old rows stay comparable.

### Timeouts, errors, and OOM

- Every warmup and cell has a timeout (`1 s` in tests, `120 s` per cell and
  `600 s` for the cold load in production defaults). A timeout is stored as a
  cell with `timeout: true` and an error string, and the sweep continues.
- A non-timeout exception is stored as that cell's error and the sweep continues.
- An `Error` — the usual shape of a JVM out-of-memory — is caught, recorded as
  the cell it happened on, flushed, and then the sweep stops with an
  out-of-memory state. The row stays `in_progress`, so the model can be resumed
  after the process restarts. The app cannot recover a process the OS killed
  outright, but it does not need to: the flushed row is the resume point.

## Sharing to a computer

Rows on Android live in app storage, so the app can POST them to a receiver on
the same Wi-Fi network:

```bash
python tools/eval_receiver.py
# Listening on 0.0.0.0:8765
#   In the app, share to: 192.168.1.20:8765
```

On the **Evaluate local models** screen, enter that address under **Share with a
computer** and tap **Share rows**. The receiver validates each row and files it
as `docs/model-evals/<model-slug>-<run-id>.json`. Resending is idempotent: a row
the receiver already has is reported as up to date rather than duplicated.

Useful flags:

```bash
python tools/eval_receiver.py --port 9000
python tools/eval_receiver.py --rows-dir docs/model-evals/incoming
python tools/eval_receiver.py --once            # exit after the first batch
```

`GET /health` reports the receiver's status and rows directory. The Android
manifest enables cleartext HTTP for these private-network endpoints; a hosted
provider still uses HTTPS.

## Per-device recommendations

Once the rows are in `docs/model-evals`, group them by device:

```bash
# markdown to stdout
python tools/eval_report_by_device.py

# the committed report, with per-interaction detail
python tools/eval_report_by_device.py --details --out docs/model-evals/by-device.md

# just the phone you are tuning (matches the key or label)
python tools/eval_report_by_device.py --device pixel

# machine-readable companion for the catalog work
python tools/eval_report_by_device.py --json-out docs/model-evals/by-device.json
```

The report has one section per hardware/OS pair, with models ranked by tier,
quality, and suite time. `--per-model latest` uses each model's newest row
instead of pooling every run; `--per-model max-cells` picks the most complete
row. As in the Python report, rows older than the current schema are skipped
unless `--min-schema` lowers the bar, and rows from another fixtures version are
skipped unless `--allow-fixtures` is passed.

The app shows the same grouping live on the evaluation screen, so a phone sweep
can be judged without leaving the device.

## Extending the curated set

The harness intentionally evaluates the **curated** catalog, not arbitrary
files. To try a new model:

1. Add it to `LocalModelCatalog` with its `.litertlm` URL, expected byte count,
   SHA-256, context window, and — when known — `parameterCount` and
   `quantization`. The last two drive the footprint estimate.
2. Install it from the Local engine tier.
3. Sweep it on the target device. If it is too large, the row left behind still
   records how far the cold load or the first cells got.
4. Share the rows, run `eval_report_by_device.py`, and use the result to decide
   whether the model joins the catalog or is replaced by a smaller one.

`ModelProfiles.measuredSeeds` in `ModelCuration` holds the hand-curated
quality/speed bands; the report's markdown and curated snippet output
(`EvalReport.curatedSnippet`) are shaped for copying a measured row into it.

## Related commands

```bash
./gradlew :shared:desktopTest              # eval scoring/aggregation/evaluator tests
python tools/eval_ollama_models.py report  # the original desktop/Ollama report
python tools/eval_receiver.py              # LAN receiver
python tools/eval_report_by_device.py      # per-device report
```
