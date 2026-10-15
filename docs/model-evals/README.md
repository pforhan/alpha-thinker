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
# default: compact tab-separated table over every saved row (latest per model)
python tools/eval_ollama_models.py report

# just one batch — rows are filtered by their stored run id, nothing is deleted
python tools/eval_ollama_models.py report --run-id morning-20261015

# one batch minus a model you cancelled (matched by id, display name, or family)
python tools/eval_ollama_models.py report --run-id morning-20261015 --exclude qwq

# compare two batches side by side (shows both rows per model)
python tools/eval_ollama_models.py report --run-id overnight-20261014 --run-id morning-20261015 --per-model all

# full markdown report — field guide + per-model detail (commit this)
python tools/eval_ollama_models.py report --run-id morning-20261015 --exclude qwq --full --out morning-20261015.md
```

Batches are reportable straight out of this folder because the run id is part of
each filename, so `--run-id` picks one out without you having to move or delete
the others. Per-batch reports live next to this README (`overnight-20261014.md`,
`morning-20261015.md`).

Scoring happens at read time from each row's raw replies, so older rows can be
re-scored after a rule change; `report` skips rows whose `schema` is too old
(`--min-schema`) or whose `fixtures_version` differs (`--allow-fixtures`).

See `tools/README.md` for what each metric means.
