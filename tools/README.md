# tools/eval_ollama_models.py

Local-only evaluator that benchmarks downloaded Ollama models against Alpha
Thinker's PlanningEngine prompt shapes. Focuses on formatting, structure, and
speed (not semantic quality).

## Storage: files as rows

Every model evaluation is written as **one JSON file per (run, model)**, all flat
in one folder, named `<model-slug>-<run-id>.json`. Sorting the folder therefore
groups a model's history together, and each file is a single self-contained
"row" you can read, diff, or delete on its own:

```
docs/model-evals/
  llama3.2_3b-20261014-210806.json
  llama3.2_3b-20261015-090000.json
  qwen2.5_7b-20261014-210806.json
```

A row holds the run parameters, hardware, model metadata, the unscored warmup
(`load_s`), and one entry per scored request with its raw reply, monotonic
latency (`latency_s`), realtime span (`wall_s`), any `sleep_s`, and Ollama's own
timing counters. It is flushed after every request, so an interrupted sweep keeps
its completed work (`in_progress: true`).

**Scoring is derived at read time.** Rows store raw replies, not scores; the
report re-scores them. Tightening a scoring rule therefore re-scores every saved
row instead of invalidating it — only structural changes bump `schema`, and only
prompt changes bump `fixtures_version`.

## Running

```bash
# every installed model, 3 runs each
python tools/eval_ollama_models.py

# a specific set, 5 runs each
python tools/eval_ollama_models.py --models llama3.2:latest qwen2.5:7b --runs 5

# keep models loaded instead of unloading between them
python tools/eval_ollama_models.py --no-stop-between-models

# write rows somewhere else
python tools/eval_ollama_models.py --rows-dir docs/model-evals/tmp
```

`run` is an optional prefix (`... run --models x` == `... --models x`).

Progress output draws one line per request that fills in its own duration, so you
can see what a model costs as it goes:

```
  title/simple-product run 1/5 (1/45) ... 0.15s
  initial_questions/simple-product run 1/5 (4/45) ... 0.76s
```

A failure closes the line too (`... 300.00s TIMEOUT`, `... 0.20s ERROR …`), and a
request that spanned a machine sleep is marked `slept 600s`. This is gated on
being attached to a terminal: when output is redirected to a file it keeps one
line per step instead, so logs stay line-oriented.

Resuming: pass a fixed `--run-id` and rows that already exist are skipped, so an
interrupted sweep can be continued by re-issuing the same command. A row left
`in_progress` (killed or slept mid-model) is *not* skipped — it's re-run. To
resume a batch *without* a model you cancelled on purpose, exclude it by name:

```bash
python tools/eval_ollama_models.py --run-id morning-20261015 --exclude qwq
```

Use `--force` to re-evaluate finished rows too. At the end it prints this run's
table (the report path below) so you see results immediately.

### `latest` is an alias

Ollama treats `latest` as a plain tag alias — `llama3.2:latest` *is* `llama3.2:3b`.
The registry lookup that would confirm this isn't available offline, so each row
distills the real identity from the model's own metadata
(`model_info["general.size_label"]`, falling back to `details.parameter_size`) and
stores a `display_name` alongside the canonical `model_id`:

```
model_id      llama3.2:latest
size_label    3B
display_name  llama3.2:3b
```

Explicit tags are left untouched, and a model whose size can't be determined keeps
its alias. The report shows `display_name`; `model_id` stays the reference you'd
pass to Ollama.

## Reporting

`report` globs row files and aggregates them into one comparison view. With no
arguments it reads everything under `docs/model-evals`; it also accepts folders
or individual row files, and de-duplicates. It defaults to the **terminal** view,
a compact tab-separated table; `--full` prints the complete markdown report.

```bash
# default: terminal table, one line per model, every saved row pooled into it
python tools/eval_ollama_models.py report

# just one batch, by run id — older rows are filtered out, not deleted
python tools/eval_ollama_models.py report --run-id morning-20261015

# one batch minus a model you bailed on (matched by id, display name, or family)
python tools/eval_ollama_models.py report --run-id morning-20261015 --exclude qwq

# two batches, each model's runs side by side in one table (`run` column)
python tools/eval_ollama_models.py report --run-id overnight-20261014 --run-id morning-20261015

# only the newest batch's row per model, ignoring older measurements
python tools/eval_ollama_models.py report --per-model latest

# full markdown report over every batch (commit this)
python tools/eval_ollama_models.py report --full --out all.md

# hand-picked rows, or the full markdown report
python tools/eval_ollama_models.py report docs/model-evals/llama3.2_3b-*.json
python tools/eval_ollama_models.py report --full --out docs/model-evals/report.md
```

Because run ids are part of each filename, `--run-id` picks a batch out without
touching the others; the header names the batch so you always know what you're
looking at. It filters on the row's stored `run.id`, so it works regardless of
how the filename happens to be spelled.

The terminal view is tab-separated so it pastes straight into a spreadsheet:

```
model           size   quality  tier          startup_s  suite_s  flags
llama3.2:3b     3B     100.0    good          0.89       5.51
qwen2.5:7b      7B     100.0    ok            2.40       15.40
gemma4:e4b      8.0B   100.0    discouraged   5.11       82.79
qwq:32b         32B    56.5     discouraged   9.77       414.07   in-progress >16GB
```

Rows are one per model, sorted by `quality` (best first) and then by `suite_s`
(fastest first), so the first row is the best default and ties break toward the
quicker model.

Columns: `model` (display name, `latest` alias resolved), `size`, `quality`
(composite 0–100, below), `tier` (`good`/`ok`/`discouraged` — a label on
the quality × speed grid, below), `startup_s` (cold load), `suite_s` (average time
to run the whole suite once, below), `flags`. Full detail — `req_s`, `p95_s`,
`ok`/`clean`/`json` rates, `est_gb`, per-interaction rows, and the grid itself —
lives in the markdown view (`--full`/`--out`), which also carries a field guide
and one detail table per model in the same order.

The report is one line per model, always. `--per-model all` (the default) doesn't
add rows — it pools every included batch into that one line (rates pool their
cells, `suite_s` averages one pass per included run), and the per-interaction
table for that model gains a `run` column so the batches sit side by side.
Single-batch reports omit the column since it's constant.

Flag values: `in-progress` (row interrupted mid-run), `timeout×N`, `error×N`,
`slept×N` (a request spanned a machine sleep), `>NG B` (estimated footprint over
the device budget — flagged, never hidden).

CLI flags:

- `--per-model all|latest|max-cells` — which rows feed the report when a model has
  several (default `all`: every row pooled into one line; `latest`: its newest
  row only, preferring finished ones; `max-cells`: the row with the most
  measurements). Whichever is chosen, the summary stays one line per model —
  `all` is about how much data is behind that line, not how many lines there are.
  With pooled rows the per-interaction table gains a `run` column; the header's
  `run-ids:` line names every batch that went in.
- `--run-id RUN_ID` — keep only rows from this batch. Repeatable, so two batches
  can be compared in one report.
- `--exclude MODEL` — drop a model, matched against its id (`qwq:latest`), its
  display name (`qwq:32b`), or its bare family (`qwq`). Repeatable.
- `--min-schema N` — skip rows older than schema `N` (default: the current
  schema). Old rows can't contribute derived data reliably, so they are dropped
  rather than misread.
- `--allow-fixtures` — include rows measured under a different prompt version.
- `--full` — print the full markdown report (field guide + per-model detail)
  instead of the terminal table.
- `--out PATH` — write the full-detail markdown there (terminal output still
  prints).
- `--device-memory-gb N` — budget used for the `>NG B` fit flag (default 16).

### Suite time

`suite_s` is the average time to run the whole suite once — one pass over every
(fixture, interaction) group — averaged across runs. Averaging rather than summing
across runs is what keeps it independent of `--runs`: a `--runs 3` and a `--runs 5`
sweep of the same model report nearly the same number, so batches taken at
different depths still compare. Cells that errored or timed out are excluded (they
delivered no answer), so a model that hangs is judged on what it completed — read
`suite_s` beside the `timeout×N` / `error×N` flags.

It's also the fastest way to see whether a model is usable at all: a well-behaved
3B model runs the whole suite in single-digit seconds, while one that ignores the
format and generates to the context cap can take ten minutes.

### Composite quality score

`quality` blends the measured rates into one 0–100 number, renormalized over the
components that are present (so a row that never generated questions isn't
penalized for a missing JSON score):

```
quality = (0.5·json_ok + 0.3·clean + 0.2·ok) / (weights present) × 100
```

Speed is deliberately **not** part of quality — it's reported separately as
`startup_s`/`suite_s`/`req_s`, since a fast model that formats badly shouldn't
outrank a slower one that formats well.

### Two-axis recommendation

The report rates every model on two independent axes and prints a grid
(`--full`) so the two can be read together. They genuinely disagree — a
flawless-but-slow model is a different proposition from a fast-but-flawed one,
and collapsing them into a single score would hide exactly that.

| axis | bands |
|---|---|
| quality (composite score) | `strong` ≥ 98, `adequate` ≥ 85, else `weak` |
| speed (average suite time) | `fast` < 10s, `fine` < 35s, else `slow` |

`unknown` is its own band on either axis: a model with no usable data is ranked
and shown, never guessed at or demoted. This is the same "present, don't filter"
rule the `>16GB` flag follows. The tool never emits it — every row it reports
carries measurements — but the app meets models that have never been swept, and
needs an answer for them.

Quality deliberately avoids the words used for tiers, so `good` means one thing in
a report.

`tier` — `good` / `ok` / `discouraged` (and `unknown` in the app) — is a *label on
that grid*:

```
good         = strong × fast
ok           = strong × fine  or  adequate × fast
discouraged  = everything else
```

So a `strong`/`slow` model is still fully listed; it just isn't dressed up as a
safe default. Both axes are derived at read time, so the cuts are tunable
without re-running a sweep.

## Metrics

Per (model, fixture, interaction):

- `ok` — produced *usable* output (non-empty title; parsed JSON array of strings
  for questions; non-empty summary).
- `clean` — full formatting conformance: title ≤60 chars, no surrounding quotes,
  no preamble, no trailing period, single line; summary ≤150 words, no
  bullets/headings, no preamble.
- `json_ok` / `array_strings` — question generation only (blank elsewhere, since
  JSON isn't requested there).
- latency `mean`/`p50`/`p95`/`std`/`min`/`max`, over successful requests only (an
  error/timeout's latency is capped by the timeout, not a real generation time).
- `timeouts`/`errors`/`sleeps` — counts of timed-out, failed, and sleep-straddling
  requests in the group.

Per-model timing, aggregated from the cells above:

- `suite_s` — average time to run the whole suite once (see *Suite time*).
- `req_s` — mean per-request time; `p95_s` the worst-case request.

Per-model performance, taken from Ollama's own counters rather than inferred from
wall clock:

- `load_s` — model load time (weights into memory), measured by the unscored
  warmup. This is the delay a fresh session feels before its first token.
- `gen_tps` — median output tokens/sec at steady state (`eval_count /
  eval_duration`).
- Per-request `timings` also record `prompt_eval_s`, `eval_s`, and token counts.

Memory fit: `est_gb` estimates the footprint (weights × 1.15 + 1 GB overhead),
preferring the on-disk `size` from `/api/tags` and falling back to
`parameter_count` and the quantization; models above `--device-memory-gb`
(default 16) are flagged `>NG B` in the report.

## Notes

- Run on the target hardware (this tool characterizes models on the specific
  machine; e.g. M2 Max 64GB). Hardware drives tokens/sec and latency.
- Deterministic-ish settings (`temperature=0.0`) and multi-run aggregation;
  treat results as guidance, not ground truth. Use `--runs 3` or more — format
  conformance is intermittent and `runs=1` gives no variance.
- Each model gets one **unscored warmup request** that both loads the model (so
  scored requests measure inference, not cold load) and *measures* that load.
  Don't confuse a long warmup wall time with load time — a "thinking" model can
  spend most of it generating reasoning tokens; trust `load_duration`.
- It prints progress as it runs (model X/Y, interaction/fixture, run count) and
  unloads models between runs by default.
- Watch `clean_rate`, not just `ok_rate`: a "thinking" model can return a
  perfectly non-empty reply that is actually its reasoning trace (e.g.
  `Thinking Process: 1. **Analyze the Request:** …`), which would become the
  project title. The raw reply is kept in the row to confirm such failures.
- `--load-timeout` (default 600s) bounds the cold-load request separately from
  `--timeout` (default 120s), which bounds scored requests.
- `--run-id` sets the run identifier used in filenames; the default is the sweep
  start timestamp (`YYYYMMDD-HHMMSS`).
- **Laptop sleep.** Rows are flushed after every request, so a sleep, a kill, or
  a crash never loses completed work, and resuming with the same `--run-id` skips
  finished rows and re-runs any row left `in_progress`. Timing itself is
  sleep-*excluding*: `latency_s` uses `perf_counter` (`mach_absolute_time`), which
  stops while the Mac sleeps, so a request that straddles a lid-close reports only
  its compute time. Each request also records `wall_s` (CLOCK_REALTIME); when the
  two diverge by more than a few seconds the cell records `sleep_s` and the model
  is flagged `slept×N`. Since a sleep also lets Ollama unload the model
  (`keep_alive` expires), the next request can silently pay a reload — re-run a
  `slept` model rather than trust its timing.

## Committing results

`docs/model-evals/.gitignore` ignores all row JSON (verbatim replies, local-only).
Commit readable markdown reports (`--out docs/model-evals/report.md`); the rows
regenerate locally via `report`.

## Note on llmfit

[llmfit](https://github.com/AlexsJones/llmfit) was considered and deliberately not
adopted: it scores hardware fit and estimated speed from a static catalog with a
generic quality *prior*, but never runs our prompts, so it cannot tell us whether
a model emits clean JSON or leaks its reasoning trace. Its `quality` score is
exactly the kind of prior this harness exists to replace with measured evidence.
