# Model evals

Output from `tools/eval_ollama_models.py`.

Every evaluation is **one JSON file per (run, model)**, flat in this folder and
named `<model-slug>-<run-id>.json`:

```
llama3.2_3b-20261014-210806.json
llama3.2_3b-20261015-090000.json
qwen2.5_7b-20261014-210806.json
```

Sorting the folder groups a model's history together. Each row is
self-contained: run parameters, hardware, model metadata, the unscored warmup,
and one entry per scored request (raw reply, monotonic latency, realtime span,
Ollama timing counters). Files are flushed after every request, so an interrupted
sweep keeps its work (`in_progress: true`) and resumes by re-running only that
row.

Only readable markdown reports are tracked in git — the row JSON is local-only
raw output (see `.gitignore`). Build a report at any time:

```bash
# default: compact tab-separated table, one line per model, all rows pooled in
python tools/eval_ollama_models.py report

# just one batch — rows are filtered by their stored run id, nothing is deleted
python tools/eval_ollama_models.py report --run-id morning-20261015

# one batch minus a model you cancelled (matched by id, display name, or family)
python tools/eval_ollama_models.py report --run-id morning-20261015 --exclude qwq

# compare two batches side by side (each model's runs in one table, `run` column)
python tools/eval_ollama_models.py report --run-id overnight-20261014 --run-id morning-20261015

# full markdown report — field guide + per-model detail (commit this)
python tools/eval_ollama_models.py report --run-id morning-20261015 --exclude qwq --full --out morning-20261015.md
```

Batches are reportable straight out of this folder because the run id is part of
each filename, so `--run-id` picks one out without you having to move or delete
the others. `all.md` holds **every** saved row
across all batches: the summary is still one line per model — the batches behind
that line are pooled into it (`--per-model all`, the default), and the `run-ids:`
header names them — while each model's per-interaction table gains a `run` column,
so you can see how it holds up batch to batch.

The full report rates each model on two independent axes — **quality** (composite
score: strong ≥ 98, adequate ≥ 85, else weak) and **speed** (average suite time:
fast < 10s, fine < 35s, else slow) — and prints them as a grid. `good` / `ok` /
`discouraged` are labels on that grid, not filters: nothing is hidden, and a model
with no data lands in `unknown` rather than being guessed at. See
`tools/README.md` for the cuts and the tier mapping.

Scoring happens at read time from each row's raw replies, so older rows can be
re-scored after a rule change; `report` skips rows whose `schema` is too old
(`--min-schema`) or whose `fixtures_version` differs (`--allow-fixtures`).

See `tools/README.md` for what each metric means.
