# Ollama evals — 24 model(s) from 45 row(s)

- rows: 45 file(s) read, 45 usable, 0 too old, 0 fixture mismatch, 0 unreadable
- per-model: all — 45 row(s) pooled into 24 model(s)
- run-ids: afternoon-20261015, morning-20261015, overnight-20261014
- params: base_url=http://localhost:11434, runs=mixed, num_ctx=2048, temp=0.0
- hardware: {"cpu": "Apple M2 Max", "platform": "darwin", "python": "3.15.0", "ram_gb": 64.0}
- device budget: 16GB (models above it are flagged, never hidden)

## Per-model (sorted by composite quality, then suite speed)

| model                | size  | quality | tier        | startup_s | suite_s | req_s | p95_s  | ok   | clean | json | est_gb | flags             |
| ----------------------| -------| ---------| -------------| -----------| ---------| -------| --------| ------| -------| ------| --------| -------------------|
| llama3.2:3b          | 3B    | 100.0   | good        | 0.89      | 5.51    | 0.61  | 1.57   | 1.00 | 1.00  | 1.00 | 3.2    |                   |
| qwen2.5:7b           | 7B    | 100.0   | ok          | 2.40      | 15.40   | 1.71  | 4.85   | 1.00 | 1.00  | 1.00 | 6.0    |                   |
| gemma4:e4b           | 8.0B  | 100.0   | discouraged | 5.11      | 82.79   | 9.20  | 24.51  | 1.00 | 1.00  | 1.00 | 11.3   |                   |
| qwen3.6:35b-mlx      | 35.1B | 100.0   | discouraged | 4.96      | 208.53  | 23.17 | 37.61  | 1.00 | 1.00  | 1.00 | 19.8   | >16GB             |
| qwen3.8:27b-mlx      | 27.8B | 100.0   | discouraged | 9.33      | 264.69  | 29.41 | 58.39  | 1.00 | 1.00  | 1.00 | 15.9   |                   |
| muse-glimmer:30b-mlx | 32.3B | 100.0   | discouraged | 10.93     | 372.97  | 41.44 | 63.45  | 1.00 | 1.00  | 1.00 | 18.3   | >16GB             |
| deepseek-r1:14b      | 14B   | 96.7    | discouraged | 4.08      | 269.71  | 29.97 | 64.17  | 1.00 | 0.89  | 1.00 | 10.6   |                   |
| gemma4:26b           | 25.8B | 94.4    | discouraged | 9.63      | 162.36  | 18.04 | 40.87  | 0.89 | 0.89  | 1.00 | 16.5   | >16GB             |
| gemma4:31b-mlx       | 31.7B | 94.4    | discouraged | 7.12      | 260.42  | 62.27 | 300.02 | 0.89 | 0.89  | 1.00 | 18.0   | timeout×3 >16GB   |
| gemma4:31b           | 31.3B | 94.4    | discouraged | 11.68     | 770.30  | 85.59 | 257.38 | 0.89 | 0.89  | 1.00 | 19.8   | >16GB             |
| deepseek-r1:8b       | 8B    | 94.1    | discouraged | 2.58      | 196.79  | 21.87 | 82.13  | 1.00 | 0.80  | 1.00 | 6.6    | slept×1           |
| deepseek-r1:1.5b     | 1.5B  | 93.3    | discouraged | 1.06      | 27.91   | 3.10  | 5.45   | 1.00 | 0.78  | 1.00 | 2.2    |                   |
| nemotron-mini:4b     | 4B    | 90.0    | ok          | 1.73      | 5.78    | 0.64  | 1.73   | 1.00 | 0.67  | 1.00 | 3.9    |                   |
| gemma4:26b-mlx       | 26.2B | 85.2    | discouraged | 8.82      | 90.74   | 77.75 | 300.00 | 0.70 | 0.70  | 1.00 | 15.0   | timeout×8         |
| gemma4:12b           | 12B   | 61.1    | discouraged | 3.14      | 592.47  | 65.83 | 114.08 | 0.56 | 0.56  | 0.67 | 9.1    |                   |
| gemma4-12b-128k:12b  | 12B   | 61.1    | discouraged | 4.11      | 626.29  | 69.59 | 107.99 | 0.56 | 0.56  | 0.67 | 8.2    |                   |
| qwq:32b              | 32B   | 56.5    | discouraged | 9.77      | 414.07  | 90.72 | 236.89 | 0.92 | 0.16  | 0.67 | 22.3   | in-progress >16GB |
| qwen3.6:36b          | 36.0B | 50.0    | discouraged | 7.54      | 241.16  | 26.80 | 34.13  | 0.67 | 0.67  | 0.33 | 22.7   | >16GB             |
| tev1:0.8b            | 0.8B  | 35.6    | discouraged | 0.93      | 109.74  | 12.19 | 15.51  | 0.78 | 0.11  | 0.33 | 1.9    |                   |
| qwen3.5:4b           | 4.7B  | 33.3    | discouraged | 2.32      | 307.75  | 34.19 | 43.42  | 0.33 | 0.33  | 0.33 | 4.6    |                   |
| qwen3-vl:4b          | 4.4B  | 31.9    | discouraged | 2.31      | 192.53  | 21.39 | 42.53  | 0.64 | 0.64  | 0.00 | 4.5    |                   |
| tinyllama:1b         | 1B    | 16.7    | discouraged | 0.40      | 332.84  | 36.98 | 95.50  | 0.67 | 0.11  | 0.00 | 1.7    | slept×1           |
| qwen3.5:9.7b         | 9.7B  | 11.1    | discouraged | 3.08      | 548.89  | 60.99 | 67.43  | 0.22 | 0.22  | 0.00 | 8.1    |                   |
| qwen3.5:0.8b         | 0.9B  | 0.0     | discouraged | 1.45      | 129.33  | 14.37 | 14.92  | 0.00 | 0.00  | 0.00 | 2.1    |                   |

## Recommendation grid

Two independent axes — **quality** (composite score) and **speed** (average
suite time) — so a flawless-but-slow model stays distinguishable from a
fast-but-flawed one. Tiers are a label on the grid, not a filter:
**good** = strong × fast, **ok** = strong × fine or adequate × fast,
everything else is **discouraged**. No model is hidden by its cell.

| quality \ speed | fast (<10s)      | fine (<35s)      | slow (≥35s)                                                                                                                         | unknown |
| -----------------| ------------------| ------------------| -------------------------------------------------------------------------------------------------------------------------------------| ---------|
| **strong**      | llama3.2:3b      | qwen2.5:7b       | gemma4:e4b, qwen3.6:35b-mlx, qwen3.8:27b-mlx, muse-glimmer:30b-mlx                                                                  |         |
| adequate        | nemotron-mini:4b | deepseek-r1:1.5b | deepseek-r1:14b, gemma4:26b, gemma4:31b-mlx, gemma4:31b, deepseek-r1:8b, gemma4:26b-mlx                                             |         |
| weak            |                  |                  | gemma4:12b, gemma4-12b-128k:12b, qwq:32b, qwen3.6:36b, tev1:0.8b, qwen3.5:4b, qwen3-vl:4b, tinyllama:1b, qwen3.5:9.7b, qwen3.5:0.8b |         |
| unknown         |                  |                  |                                                                                                                                     |         |

## Per-interaction detail (one table per model, same order as above)

### llama3.2:3b

| run                | fixture        | interaction       | reps | ok   | clean | json | arr  | timeouts | mean | p50  | p95  | std  | min  | max  | tps   |
| --------------------| ----------------| -------------------| ------| ------| -------| ------| ------| ----------| ------| ------| ------| ------| ------| ------| -------|
| overnight-20261014 | simple-product | title             | 3    | 1.00 | 1.00  | -    | -    | 0        | 0.12 | 0.06 | 0.22 | 0.08 | 0.06 | 0.22 | 139.3 |
| overnight-20261014 | home-project   | title             | 3    | 1.00 | 1.00  | -    | -    | 0        | 0.08 | 0.07 | 0.10 | 0.02 | 0.07 | 0.10 | 133.0 |
| overnight-20261014 | software-plan  | title             | 3    | 1.00 | 1.00  | -    | -    | 0        | 0.07 | 0.06 | 0.10 | 0.02 | 0.06 | 0.10 | 138.5 |
| overnight-20261014 | simple-product | initial_questions | 3    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.75 | 0.72 | 0.87 | 0.09 | 0.65 | 0.87 | 94.4  |
| overnight-20261014 | home-project   | initial_questions | 3    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.90 | 0.86 | 0.98 | 0.06 | 0.85 | 0.98 | 73.4  |
| overnight-20261014 | software-plan  | initial_questions | 3    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.75 | 0.72 | 0.82 | 0.05 | 0.70 | 0.82 | 74.6  |
| overnight-20261014 | simple-product | summarize         | 3    | 1.00 | 1.00  | -    | -    | 0        | 1.29 | 1.23 | 1.43 | 0.09 | 1.23 | 1.43 | 73.2  |
| overnight-20261014 | home-project   | summarize         | 3    | 1.00 | 1.00  | -    | -    | 0        | 0.86 | 0.84 | 0.91 | 0.04 | 0.82 | 0.91 | 72.9  |
| overnight-20261014 | software-plan  | summarize         | 3    | 1.00 | 1.00  | -    | -    | 0        | 0.48 | 0.47 | 0.55 | 0.05 | 0.43 | 0.55 | 77.1  |
| morning-20261015   | simple-product | title             | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.08 | 0.06 | 0.14 | 0.03 | 0.06 | 0.14 | 139.7 |
| morning-20261015   | home-project   | title             | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.08 | 0.07 | 0.10 | 0.01 | 0.07 | 0.10 | 131.6 |
| morning-20261015   | software-plan  | title             | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.07 | 0.07 | 0.10 | 0.01 | 0.06 | 0.10 | 125.6 |
| morning-20261015   | simple-product | initial_questions | 5    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.89 | 0.85 | 1.04 | 0.08 | 0.84 | 1.04 | 74.4  |
| morning-20261015   | home-project   | initial_questions | 5    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.90 | 0.87 | 1.04 | 0.07 | 0.86 | 1.04 | 72.5  |
| morning-20261015   | software-plan  | initial_questions | 5    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.73 | 0.71 | 0.83 | 0.05 | 0.70 | 0.83 | 75.3  |
| morning-20261015   | simple-product | summarize         | 5    | 1.00 | 1.00  | -    | -    | 0        | 1.26 | 1.22 | 1.44 | 0.09 | 1.22 | 1.44 | 73.6  |
| morning-20261015   | home-project   | summarize         | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.83 | 0.80 | 0.93 | 0.05 | 0.79 | 0.93 | 74.9  |
| morning-20261015   | software-plan  | summarize         | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.46 | 0.45 | 0.54 | 0.04 | 0.43 | 0.54 | 75.6  |
| afternoon-20261015 | simple-product | title             | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.08 | 0.07 | 0.15 | 0.03 | 0.06 | 0.15 | 127.5 |
| afternoon-20261015 | home-project   | title             | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.08 | 0.08 | 0.11 | 0.01 | 0.07 | 0.11 | 121.5 |
| afternoon-20261015 | software-plan  | title             | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.11 | 0.11 | 0.13 | 0.01 | 0.09 | 0.13 | 89.6  |
| afternoon-20261015 | simple-product | initial_questions | 5    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 1.02 | 0.97 | 1.22 | 0.10 | 0.95 | 1.22 | 65.5  |
| afternoon-20261015 | home-project   | initial_questions | 5    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.97 | 0.94 | 1.11 | 0.07 | 0.92 | 1.11 | 67.6  |
| afternoon-20261015 | software-plan  | initial_questions | 5    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 0.79 | 0.77 | 0.90 | 0.05 | 0.76 | 0.90 | 68.5  |
| afternoon-20261015 | simple-product | summarize         | 5    | 1.00 | 1.00  | -    | -    | 0        | 1.40 | 1.36 | 1.57 | 0.09 | 1.35 | 1.57 | 66.6  |
| afternoon-20261015 | home-project   | summarize         | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.91 | 0.88 | 1.03 | 0.06 | 0.87 | 1.03 | 67.9  |
| afternoon-20261015 | software-plan  | summarize         | 5    | 1.00 | 1.00  | -    | -    | 0        | 0.49 | 0.47 | 0.57 | 0.04 | 0.47 | 0.57 | 71.6  |

### qwen2.5:7b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 0.14 | 0.09 | 0.26 | 0.08 | 0.08 | 0.26 | 75.9 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 0.16 | 0.14 | 0.21 | 0.03 | 0.14 | 0.21 | 66.6 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 0.15 | 0.13 | 0.20 | 0.03 | 0.12 | 0.20 | 68.5 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 4.02 | 3.88 | 4.30 | 0.20 | 3.87 | 4.30 | 33.3 |
| overnight-20261014 | home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 2.74 | 2.67 | 2.89 | 0.11 | 2.67 | 2.89 | 32.3 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 2.44 | 2.34 | 2.66 | 0.15 | 2.33 | 2.66 | 33.9 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 2.72 | 2.62 | 2.93 | 0.15 | 2.62 | 2.93 | 32.2 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 1.83 | 1.76 | 2.00 | 0.12 | 1.73 | 2.00 | 34.3 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 0.59 | 0.53 | 0.73 | 0.10 | 0.52 | 0.73 | 36.8 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.13 | 0.09 | 0.27 | 0.07 | 0.09 | 0.27 | 74.9 |
| morning-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.16 | 0.14 | 0.22 | 0.03 | 0.14 | 0.22 | 65.8 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.16 | 0.16 | 0.20 | 0.02 | 0.13 | 0.20 | 58.9 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 4.15 | 4.09 | 4.55 | 0.22 | 3.94 | 4.55 | 31.9 |
| morning-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 2.69 | 2.68 | 2.97 | 0.16 | 2.49 | 2.97 | 33.0 |
| morning-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 2.43 | 2.43 | 2.50 | 0.04 | 2.38 | 2.50 | 32.8 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 2.65 | 2.58 | 2.94 | 0.15 | 2.53 | 2.94 | 32.7 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 1.75 | 1.69 | 1.98 | 0.12 | 1.68 | 1.98 | 35.0 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 0.59 | 0.57 | 0.71 | 0.07 | 0.52 | 0.71 | 34.0 |
| afternoon-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.13 | 0.09 | 0.27 | 0.07 | 0.09 | 0.27 | 71.4 |
| afternoon-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.21 | 0.22 | 0.27 | 0.04 | 0.15 | 0.27 | 51.5 |
| afternoon-20261015 | software-plan | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.21 | 0.20 | 0.30 | 0.05 | 0.17 | 0.30 | 43.5 |
| afternoon-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 4.45 | 4.32 | 4.85 | 0.20 | 4.32 | 4.85 | 29.8 |
| afternoon-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 2.99 | 2.92 | 3.20 | 0.13 | 2.84 | 3.20 | 29.5 |
| afternoon-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 2.80 | 2.76 | 2.97 | 0.10 | 2.68 | 2.97 | 29.0 |
| afternoon-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 2.93 | 2.86 | 3.30 | 0.19 | 2.79 | 3.30 | 29.9 |
| afternoon-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 2.07 | 1.99 | 2.35 | 0.15 | 1.94 | 2.35 | 30.1 |
| afternoon-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 0.65 | 0.61 | 0.82 | 0.09 | 0.58 | 0.82 | 33.1 |

### gemma4:e4b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 0.18 | 0.15 | 0.26 | 0.06 | 0.13 | 0.26 | 76.2 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 0.15 | 0.11 | 0.24 | 0.06 | 0.11 | 0.24 | 86.2 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 0.23 | 0.19 | 0.32 | 0.06 | 0.18 | 0.32 | 69.8 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 14.38 | 14.51 | 14.56 | 0.21 | 14.09 | 14.56 | 45.6 |
| overnight-20261014 | home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 15.72 | 15.48 | 16.20 | 0.34 | 15.48 | 16.20 | 45.3 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 20.57 | 20.49 | 20.89 | 0.24 | 20.32 | 20.89 | 45.3 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 8.18 | 8.09 | 8.38 | 0.14 | 8.07 | 8.38 | 45.6 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 10.94 | 10.81 | 11.22 | 0.20 | 10.78 | 11.22 | 45.3 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 9.15 | 9.03 | 9.42 | 0.19 | 9.00 | 9.42 | 45.4 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.16 | 0.13 | 0.28 | 0.06 | 0.12 | 0.28 | 77.8 |
| morning-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.14 | 0.12 | 0.25 | 0.05 | 0.11 | 0.25 | 84.3 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.20 | 0.18 | 0.30 | 0.05 | 0.18 | 0.30 | 69.6 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 14.33 | 14.37 | 14.41 | 0.12 | 14.09 | 14.41 | 46.1 |
| morning-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 15.44 | 15.33 | 15.66 | 0.17 | 15.29 | 15.66 | 45.8 |
| morning-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 20.33 | 20.26 | 20.65 | 0.16 | 20.18 | 20.65 | 45.7 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 8.05 | 8.01 | 8.20 | 0.07 | 8.01 | 8.20 | 46.0 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 10.75 | 10.69 | 11.05 | 0.16 | 10.61 | 11.05 | 45.9 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 8.98 | 8.91 | 9.25 | 0.14 | 8.87 | 9.25 | 46.0 |
| afternoon-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.16 | 0.14 | 0.27 | 0.05 | 0.13 | 0.27 | 74.1 |
| afternoon-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.14 | 0.12 | 0.24 | 0.05 | 0.11 | 0.24 | 83.1 |
| afternoon-20261015 | software-plan | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.21 | 0.18 | 0.31 | 0.05 | 0.18 | 0.31 | 67.3 |
| afternoon-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 15.84 | 15.75 | 16.76 | 0.59 | 14.98 | 16.76 | 42.0 |
| afternoon-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 18.60 | 18.91 | 18.97 | 0.59 | 17.43 | 18.97 | 37.1 |
| afternoon-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 23.18 | 22.82 | 24.51 | 0.72 | 22.49 | 24.51 | 40.6 |
| afternoon-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 9.01 | 8.96 | 9.18 | 0.15 | 8.82 | 9.18 | 41.3 |
| afternoon-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 11.95 | 11.80 | 12.42 | 0.30 | 11.61 | 12.42 | 41.5 |
| afternoon-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 10.10 | 10.08 | 10.33 | 0.14 | 9.90 | 10.33 | 40.7 |

### qwen3.6:35b-mlx

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 15.68 | 15.05 | 17.13 | 1.03 | 14.85 | 17.13 | 66.1 |
| home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 21.97 | 21.19 | 23.61 | 1.16 | 21.11 | 23.61 | 65.0 |
| software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 35.75 | 37.57 | 37.61 | 2.60 | 32.07 | 37.61 | 65.2 |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 26.19 | 27.31 | 27.55 | 1.76 | 23.70 | 27.55 | 65.1 |
| home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 33.06 | 33.89 | 33.90 | 1.19 | 31.38 | 33.90 | 65.2 |
| software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 30.43 | 31.65 | 31.81 | 1.84 | 27.82 | 31.81 | 65.3 |
| simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 19.37 | 19.84 | 19.86 | 0.68 | 18.41 | 19.86 | 65.3 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 13.83 | 14.34 | 14.38 | 0.74 | 12.78 | 14.38 | 65.4 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 12.26 | 12.32 | 12.41 | 0.16 | 12.04 | 12.41 | 65.3 |

### qwen3.8:27b-mlx

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 15.66 | 16.21 | 16.44 | 0.94 | 14.33 | 16.44 | 15.1 |
| home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 22.97 | 23.21 | 24.62 | 1.46 | 21.07 | 24.62 | 16.7 |
| software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 31.09 | 30.05 | 36.32 | 3.92 | 26.89 | 36.32 | 15.9 |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 37.07 | 37.32 | 37.59 | 0.56 | 36.30 | 37.59 | 15.4 |
| home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 49.02 | 49.20 | 53.87 | 4.03 | 44.00 | 53.87 | 15.8 |
| software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 53.15 | 50.65 | 58.39 | 3.71 | 50.40 | 58.39 | 15.9 |
| simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 17.42 | 17.10 | 18.85 | 1.06 | 16.31 | 18.85 | 16.5 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 18.00 | 17.67 | 19.26 | 0.92 | 17.09 | 19.26 | 16.4 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 20.32 | 20.33 | 21.48 | 0.96 | 19.14 | 21.48 | 17.1 |

### muse-glimmer:30b-mlx

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 24.53 | 24.64 | 26.98 | 2.05 | 21.97 | 26.98 | 14.6 |
| home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 22.98 | 22.26 | 26.59 | 2.71 | 20.08 | 26.59 | 14.8 |
| software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 33.67 | 35.49 | 35.90 | 2.87 | 29.62 | 35.90 | 13.2 |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 43.81 | 43.86 | 45.83 | 1.67 | 41.74 | 45.83 | 13.2 |
| home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 57.04 | 55.99 | 63.45 | 4.86 | 51.67 | 63.45 | 13.1 |
| software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 52.70 | 49.95 | 59.42 | 4.78 | 48.71 | 59.42 | 13.6 |
| simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 48.08 | 47.01 | 50.58 | 1.78 | 46.64 | 50.58 | 13.8 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 40.06 | 39.75 | 40.70 | 0.45 | 39.73 | 40.70 | 16.6 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 50.11 | 51.40 | 51.49 | 1.89 | 47.45 | 51.49 | 14.8 |

### deepseek-r1:14b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 27.94 | 27.88 | 28.19 | 0.18 | 27.76 | 28.19 | 16.8 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 20.69 | 20.68 | 20.74 | 0.03 | 20.66 | 20.74 | 16.7 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 16.51 | 16.50 | 16.56 | 0.04 | 16.46 | 16.56 | 16.5 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 61.07 | 60.26 | 62.93 | 1.32 | 60.00 | 62.93 | 17.2 |
| overnight-20261014 | home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 32.24 | 32.15 | 32.53 | 0.21 | 32.05 | 32.53 | 16.4 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 31.03 | 30.90 | 31.41 | 0.27 | 30.79 | 31.41 | 16.5 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 24.52 | 24.32 | 25.06 | 0.39 | 24.17 | 25.06 | 16.5 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 30.22 | 29.95 | 31.34 | 0.83 | 29.36 | 31.34 | 17.1 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 23.72 | 23.62 | 23.90 | 0.13 | 23.62 | 23.90 | 16.4 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 28.03 | 28.24 | 28.67 | 0.49 | 27.29 | 28.67 | 16.6 |
| morning-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 20.92 | 20.89 | 21.03 | 0.07 | 20.85 | 21.03 | 16.5 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 1.00 | - | - | 0 | 16.56 | 16.52 | 16.74 | 0.13 | 16.38 | 16.74 | 16.5 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 62.22 | 63.10 | 64.17 | 1.72 | 59.42 | 64.17 | 16.6 |
| morning-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 31.72 | 32.01 | 32.69 | 0.74 | 30.49 | 32.69 | 16.4 |
| morning-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 30.44 | 30.17 | 31.45 | 0.65 | 29.58 | 31.45 | 16.8 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 24.70 | 24.58 | 25.61 | 0.53 | 24.08 | 25.61 | 16.2 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 31.36 | 31.18 | 32.44 | 0.56 | 30.81 | 32.44 | 16.4 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 24.84 | 24.42 | 26.54 | 0.91 | 24.09 | 26.54 | 15.9 |

### gemma4:26b

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 8.46 | 8.62 | 8.72 | 0.30 | 8.04 | 8.72 | 49.9 |
| home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 13.44 | 13.39 | 13.59 | 0.10 | 13.35 | 13.59 | 49.2 |
| software-plan | title | 3 | 0.00 | 0.00 | - | - | 0 | 40.75 | 40.70 | 40.87 | 0.09 | 40.67 | 40.87 | 48.2 |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 17.96 | 17.74 | 18.60 | 0.46 | 17.55 | 18.60 | 48.5 |
| home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 15.96 | 15.42 | 17.06 | 0.78 | 15.40 | 17.06 | 48.4 |
| software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 16.97 | 16.82 | 17.30 | 0.23 | 16.80 | 17.30 | 48.5 |
| simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 25.26 | 24.51 | 26.84 | 1.12 | 24.43 | 26.84 | 48.3 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 11.03 | 10.94 | 11.26 | 0.17 | 10.89 | 11.26 | 49.1 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 12.52 | 12.49 | 12.97 | 0.36 | 12.09 | 12.97 | 48.8 |

### gemma4:31b-mlx

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 29.35 | 29.18 | 32.38 | 2.42 | 26.47 | 32.38 | 17.0 |
| home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 23.57 | 23.13 | 26.22 | 2.00 | 21.37 | 26.22 | 15.1 |
| software-plan | title | 3 | 0.00 | 0.00 | - | - | 3 | 300.02 | 300.02 | 300.02 | 0.00 | 300.01 | 300.02 | - |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 44.99 | 44.00 | 49.93 | 3.70 | 41.03 | 49.93 | 13.7 |
| home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 41.46 | 41.51 | 43.41 | 1.61 | 39.46 | 43.41 | 14.6 |
| software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 53.74 | 52.32 | 57.81 | 2.92 | 51.11 | 57.81 | 14.1 |
| simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 27.21 | 27.33 | 27.48 | 0.28 | 26.81 | 27.48 | 16.1 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 23.32 | 23.03 | 24.31 | 0.72 | 22.62 | 24.31 | 17.1 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 16.79 | 16.51 | 17.50 | 0.51 | 16.35 | 17.50 | 19.1 |

### gemma4:31b

| fixture        | interaction       | reps | ok   | clean | json | arr  | timeouts | mean   | p50    | p95    | std  | min    | max    | tps |
| ----------------| -------------------| ------| ------| -------| ------| ------| ----------| --------| --------| --------| ------| --------| --------| -----|
| simple-product | title             | 3    | 1.00 | 1.00  | -    | -    | 0        | 44.03  | 43.80  | 44.66  | 0.45 | 43.64  | 44.66  | 7.7 |
| home-project   | title             | 3    | 1.00 | 1.00  | -    | -    | 0        | 44.51  | 44.06  | 45.45  | 0.66 | 44.01  | 45.45  | 7.7 |
| software-plan  | title             | 3    | 0.00 | 0.00  | -    | -    | 0        | 256.68 | 256.57 | 257.38 | 0.53 | 256.09 | 257.38 | 7.7 |
| simple-product | initial_questions | 3    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 81.52  | 80.98  | 82.76  | 0.88 | 80.83  | 82.76  | 7.7 |
| home-project   | initial_questions | 3    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 83.50  | 82.80  | 84.94  | 1.02 | 82.75  | 84.94  | 7.7 |
| software-plan  | initial_questions | 3    | 1.00 | 1.00  | 1.00 | 1.00 | 0        | 109.60 | 108.82 | 111.28 | 1.19 | 108.71 | 111.28 | 7.7 |
| simple-product | summarize         | 3    | 1.00 | 1.00  | -    | -    | 0        | 53.70  | 53.10  | 54.92  | 0.86 | 53.09  | 54.92  | 7.7 |
| home-project   | summarize         | 3    | 1.00 | 1.00  | -    | -    | 0        | 52.47  | 51.77  | 53.99  | 1.08 | 51.64  | 53.99  | 7.7 |
| software-plan  | summarize         | 3    | 1.00 | 1.00  | -    | -    | 0        | 44.29  | 43.49  | 45.97  | 1.19 | 43.41  | 45.97  | 7.7 |

### deepseek-r1:8b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 22.46 | 22.50 | 22.58 | 0.12 | 22.31 | 22.58 | 30.6 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 0.33 | - | - | 0 | 39.81 | 31.44 | 73.72 | 24.98 | 14.26 | 73.72 | 30.4 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 0.00 | - | - | 0 | 12.65 | 12.67 | 12.71 | 0.05 | 12.58 | 12.71 | 30.4 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 21.93 | 21.88 | 22.15 | 0.16 | 21.76 | 22.15 | 30.4 |
| overnight-20261014 | home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 22.25 | 22.23 | 22.35 | 0.08 | 22.16 | 22.35 | 30.4 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 32.31 | 32.32 | 32.41 | 0.08 | 32.21 | 32.41 | 30.3 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 16.58 | 16.44 | 16.92 | 0.24 | 16.40 | 16.92 | 30.4 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 12.49 | 12.46 | 12.67 | 0.14 | 12.33 | 12.67 | 30.3 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 11.82 | 11.85 | 11.94 | 0.12 | 11.66 | 11.94 | 30.5 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 23.04 | 23.17 | 23.45 | 0.35 | 22.53 | 23.45 | 29.4 |
| morning-20261015 | home-project | title | 5 | 1.00 | 0.20 | - | - | 0 | 31.27 | 16.88 | 74.44 | 22.43 | 16.16 | 74.44 | 27.0 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 13.12 | 12.63 | 14.53 | 0.77 | 12.52 | 14.53 | 30.3 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 22.22 | 21.95 | 23.54 | 0.67 | 21.70 | 23.54 | 30.2 |
| morning-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 25.53 | 25.76 | 26.83 | 1.39 | 22.92 | 26.83 | 25.8 |
| morning-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 34.98 | 34.20 | 40.69 | 2.95 | 32.71 | 40.69 | 28.5 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 17.04 | 16.98 | 17.55 | 0.26 | 16.80 | 17.55 | 29.4 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 12.73 | 12.66 | 13.25 | 0.27 | 12.52 | 13.25 | 29.7 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 12.10 | 12.05 | 12.40 | 0.19 | 11.88 | 12.40 | 30.0 |
| afternoon-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 24.91 | 24.96 | 25.18 | 0.21 | 24.55 | 25.18 | 27.3 |
| afternoon-20261015 | home-project | title | 5 | 1.00 | 0.20 | - | - | 0 | 32.96 | 15.98 | 82.13 | 25.68 | 15.70 | 82.13 | 27.3 |
| afternoon-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 14.21 | 13.95 | 15.52 | 0.68 | 13.58 | 15.52 | 27.5 |
| afternoon-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 24.47 | 24.44 | 24.89 | 0.31 | 24.07 | 24.89 | 27.1 |
| afternoon-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 25.34 | 25.30 | 26.29 | 0.54 | 24.65 | 26.29 | 26.6 |
| afternoon-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 36.59 | 36.35 | 37.24 | 0.54 | 35.85 | 37.24 | 26.9 |
| afternoon-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 18.30 | 18.24 | 18.67 | 0.21 | 18.04 | 18.67 | 27.4 |
| afternoon-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 13.91 | 13.84 | 14.34 | 0.26 | 13.61 | 14.34 | 27.4 |
| afternoon-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 13.55 | 13.44 | 14.23 | 0.37 | 13.12 | 14.23 | 26.7 |

### deepseek-r1:1.5b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 2.19 | 2.21 | 2.22 | 0.03 | 2.14 | 2.22 | 124.4 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 1.88 | 1.88 | 1.96 | 0.06 | 1.81 | 1.96 | 125.7 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 0.00 | - | - | 0 | 1.80 | 1.79 | 1.81 | 0.01 | 1.79 | 1.81 | 131.1 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 3.19 | 3.23 | 3.24 | 0.07 | 3.09 | 3.24 | 125.1 |
| overnight-20261014 | home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 3.96 | 3.94 | 4.00 | 0.03 | 3.93 | 4.00 | 125.6 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 3.53 | 3.53 | 3.60 | 0.06 | 3.46 | 3.60 | 129.7 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 3.55 | 3.50 | 3.72 | 0.13 | 3.42 | 3.72 | 127.2 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 2.94 | 2.96 | 3.01 | 0.07 | 2.85 | 3.01 | 122.3 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 3.44 | 3.44 | 3.48 | 0.03 | 3.41 | 3.48 | 129.6 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 2.13 | 2.08 | 2.21 | 0.06 | 2.07 | 2.21 | 132.4 |
| morning-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 1.85 | 1.85 | 1.99 | 0.08 | 1.77 | 1.99 | 128.3 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 1.86 | 1.86 | 1.99 | 0.07 | 1.78 | 1.99 | 126.8 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 3.06 | 3.06 | 3.11 | 0.05 | 3.01 | 3.11 | 130.8 |
| morning-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 3.90 | 3.92 | 4.02 | 0.10 | 3.77 | 4.02 | 130.0 |
| morning-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 3.51 | 3.51 | 3.61 | 0.08 | 3.40 | 3.61 | 130.7 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 3.51 | 3.43 | 3.74 | 0.12 | 3.42 | 3.74 | 130.1 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 2.83 | 2.75 | 2.99 | 0.10 | 2.74 | 2.99 | 131.7 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 4.00 | 3.89 | 4.40 | 0.21 | 3.85 | 4.40 | 113.7 |
| afternoon-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 2.38 | 2.37 | 2.44 | 0.04 | 2.33 | 2.44 | 117.5 |
| afternoon-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 2.01 | 2.01 | 2.04 | 0.02 | 2.00 | 2.04 | 118.6 |
| afternoon-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 2.04 | 2.01 | 2.15 | 0.06 | 2.00 | 2.15 | 117.8 |
| afternoon-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 3.68 | 3.81 | 3.92 | 0.22 | 3.35 | 3.92 | 106.2 |
| afternoon-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 4.90 | 4.84 | 5.45 | 0.36 | 4.41 | 5.45 | 103.3 |
| afternoon-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 4.13 | 4.18 | 4.27 | 0.12 | 3.95 | 4.27 | 108.5 |
| afternoon-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 3.81 | 3.80 | 3.93 | 0.07 | 3.72 | 3.93 | 117.3 |
| afternoon-20261015 | home-project | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 3.23 | 3.22 | 3.28 | 0.05 | 3.15 | 3.28 | 112.9 |
| afternoon-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 3.85 | 3.81 | 3.98 | 0.07 | 3.80 | 3.98 | 116.4 |

### nemotron-mini:4b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 0.00 | - | - | 0 | 0.11 | 0.08 | 0.16 | 0.04 | 0.08 | 0.16 | 117.4 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 0.00 | - | - | 0 | 0.09 | 0.08 | 0.11 | 0.02 | 0.08 | 0.11 | 117.0 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 0.00 | - | - | 0 | 0.12 | 0.11 | 0.14 | 0.02 | 0.11 | 0.14 | 109.9 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 0.99 | 1.02 | 1.02 | 0.05 | 0.92 | 1.02 | 67.9 |
| overnight-20261014 | home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 1.20 | 1.15 | 1.32 | 0.08 | 1.13 | 1.32 | 66.9 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 0.71 | 0.69 | 0.78 | 0.05 | 0.66 | 0.78 | 69.5 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 1.47 | 1.40 | 1.61 | 0.10 | 1.39 | 1.61 | 66.4 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 0.60 | 0.57 | 0.69 | 0.06 | 0.54 | 0.69 | 70.8 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 0.35 | 0.33 | 0.43 | 0.06 | 0.29 | 0.43 | 77.2 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.10 | 0.08 | 0.16 | 0.03 | 0.08 | 0.16 | 115.6 |
| morning-20261015 | home-project | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.08 | 0.08 | 0.11 | 0.01 | 0.08 | 0.11 | 115.6 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.11 | 0.11 | 0.14 | 0.01 | 0.11 | 0.14 | 108.8 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 1.08 | 1.07 | 1.15 | 0.04 | 1.03 | 1.15 | 65.4 |
| morning-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 1.17 | 1.13 | 1.31 | 0.07 | 1.12 | 1.31 | 67.2 |
| morning-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 0.69 | 0.67 | 0.77 | 0.04 | 0.66 | 0.77 | 67.6 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 1.47 | 1.48 | 1.59 | 0.06 | 1.41 | 1.59 | 65.4 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 0.58 | 0.56 | 0.69 | 0.05 | 0.55 | 0.69 | 69.9 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 0.34 | 0.33 | 0.43 | 0.05 | 0.30 | 0.43 | 74.8 |
| afternoon-20261015 | simple-product | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.10 | 0.09 | 0.17 | 0.03 | 0.08 | 0.17 | 108.1 |
| afternoon-20261015 | home-project | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.09 | 0.08 | 0.12 | 0.01 | 0.08 | 0.12 | 108.1 |
| afternoon-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.12 | 0.12 | 0.15 | 0.01 | 0.12 | 0.15 | 100.6 |
| afternoon-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 1.13 | 1.13 | 1.19 | 0.04 | 1.07 | 1.19 | 62.1 |
| afternoon-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 1.26 | 1.22 | 1.41 | 0.07 | 1.22 | 1.41 | 62.0 |
| afternoon-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 0.74 | 0.72 | 0.85 | 0.05 | 0.71 | 0.85 | 64.0 |
| afternoon-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 1.56 | 1.52 | 1.73 | 0.08 | 1.51 | 1.73 | 60.9 |
| afternoon-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 0.63 | 0.60 | 0.73 | 0.05 | 0.60 | 0.73 | 64.8 |
| afternoon-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 0.36 | 0.34 | 0.45 | 0.05 | 0.33 | 0.45 | 69.6 |

### gemma4:26b-mlx

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 0.00 | 0.00 | - | - | 3 | 300.00 | 300.00 | 300.00 | 0.00 | 300.00 | 300.00 | - |
| home-project | title | 3 | 0.33 | 0.33 | - | - | 2 | 13.53 | 13.53 | 13.53 | 0.00 | 13.53 | 13.53 | 53.5 |
| software-plan | title | 3 | 0.00 | 0.00 | - | - | 3 | 300.00 | 300.00 | 300.00 | 0.00 | 300.00 | 300.00 | - |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 18.31 | 18.15 | 18.76 | 0.32 | 18.03 | 18.76 | 54.2 |
| home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 15.89 | 15.72 | 16.60 | 0.52 | 15.35 | 16.60 | 54.4 |
| software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 16.36 | 16.35 | 16.92 | 0.45 | 15.81 | 16.92 | 53.1 |
| simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 16.61 | 16.56 | 17.23 | 0.48 | 16.05 | 17.23 | 54.2 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 8.30 | 8.10 | 8.79 | 0.34 | 8.02 | 8.79 | 59.8 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 10.75 | 10.46 | 11.70 | 0.68 | 10.10 | 11.70 | 58.5 |

### gemma4:12b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 0.00 | 0.00 | - | - | 0 | 101.63 | 101.37 | 104.07 | 1.89 | 99.46 | 104.07 | 19.4 |
| overnight-20261014 | home-project | title | 3 | 0.00 | 0.00 | - | - | 0 | 104.78 | 104.76 | 105.19 | 0.33 | 104.39 | 105.19 | 18.7 |
| overnight-20261014 | software-plan | title | 3 | 0.00 | 0.00 | - | - | 0 | 101.78 | 100.06 | 105.70 | 2.78 | 99.58 | 105.70 | 19.6 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 47.97 | 47.76 | 48.92 | 0.71 | 47.22 | 48.92 | 19.3 |
| overnight-20261014 | home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 93.77 | 93.62 | 94.26 | 0.36 | 93.42 | 94.26 | 19.6 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 61.34 | 60.90 | 62.31 | 0.69 | 60.80 | 62.31 | 19.5 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 35.30 | 35.36 | 36.08 | 0.66 | 34.47 | 36.08 | 18.8 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 22.68 | 22.40 | 23.23 | 0.39 | 22.40 | 23.23 | 18.7 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 26.44 | 25.96 | 27.51 | 0.75 | 25.86 | 27.51 | 19.4 |
| morning-20261015 | simple-product | title | 5 | 0.00 | 0.00 | - | - | 0 | 102.57 | 99.85 | 114.08 | 5.93 | 97.97 | 114.08 | 19.6 |
| morning-20261015 | home-project | title | 5 | 0.00 | 0.00 | - | - | 0 | 101.98 | 102.01 | 104.25 | 2.09 | 98.85 | 104.25 | 19.2 |
| morning-20261015 | software-plan | title | 5 | 0.00 | 0.00 | - | - | 0 | 98.93 | 98.96 | 100.52 | 0.96 | 97.51 | 100.52 | 19.9 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 48.14 | 48.64 | 48.67 | 0.64 | 47.17 | 48.67 | 18.9 |
| morning-20261015 | home-project | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 94.58 | 93.89 | 96.14 | 1.23 | 93.18 | 96.14 | 19.6 |
| morning-20261015 | software-plan | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 61.16 | 60.96 | 62.54 | 0.85 | 60.05 | 62.54 | 19.5 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 34.75 | 34.79 | 35.38 | 0.57 | 33.83 | 35.38 | 19.3 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 22.13 | 22.20 | 22.38 | 0.30 | 21.55 | 22.38 | 18.9 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 26.31 | 26.35 | 26.81 | 0.41 | 25.64 | 26.81 | 19.2 |

### gemma4-12b-128k:12b

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 0.00 | 0.00 | - | - | 0 | 106.82 | 107.53 | 107.59 | 1.05 | 105.34 | 107.59 | 18.2 |
| home-project | title | 3 | 0.00 | 0.00 | - | - | 0 | 107.90 | 107.89 | 107.99 | 0.07 | 107.82 | 107.99 | 18.1 |
| software-plan | title | 3 | 0.00 | 0.00 | - | - | 0 | 107.44 | 107.74 | 107.96 | 0.59 | 106.61 | 107.96 | 18.2 |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 50.68 | 50.59 | 51.02 | 0.24 | 50.44 | 51.02 | 18.2 |
| home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 101.21 | 100.91 | 102.13 | 0.66 | 100.61 | 102.13 | 18.2 |
| software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 65.33 | 65.08 | 65.93 | 0.42 | 64.99 | 65.93 | 18.2 |
| simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 35.95 | 36.36 | 36.93 | 1.01 | 34.56 | 36.93 | 18.4 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 23.25 | 23.02 | 23.83 | 0.41 | 22.91 | 23.83 | 18.3 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 27.70 | 27.50 | 28.28 | 0.42 | 27.32 | 28.28 | 18.3 |

### qwq:32b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 0.00 | - | - | 0 | 151.18 | 138.48 | 177.84 | 18.86 | 137.23 | 177.84 | 7.5 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 0.00 | - | - | 0 | 19.27 | 19.22 | 19.39 | 0.08 | 19.21 | 19.39 | 7.5 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 0.00 | - | - | 0 | 38.72 | 38.70 | 38.92 | 0.16 | 38.52 | 38.92 | 7.6 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 72.04 | 71.44 | 73.29 | 0.88 | 71.39 | 73.29 | 7.6 |
| overnight-20261014 | home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 129.81 | 129.93 | 130.49 | 0.62 | 129.00 | 130.49 | 7.6 |
| overnight-20261014 | software-plan | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 84.24 | 83.87 | 85.01 | 0.55 | 83.83 | 85.01 | 7.6 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 235.26 | 234.50 | 236.89 | 1.16 | 234.37 | 236.89 | 7.5 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 59.09 | 58.70 | 59.90 | 0.58 | 58.66 | 59.90 | 7.6 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 32.65 | 32.38 | 33.21 | 0.39 | 32.37 | 33.21 | 7.6 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 0.00 | - | - | 0 | 142.91 | 134.69 | 176.26 | 16.68 | 134.00 | 176.26 | 7.7 |
| morning-20261015 | home-project | title | 5 | 1.00 | 0.00 | - | - | 0 | 18.64 | 18.71 | 19.16 | 0.36 | 18.19 | 19.16 | 7.8 |
| morning-20261015 | software-plan | title | 1 | 1.00 | 0.00 | - | - | 0 | 38.06 | 38.06 | 38.06 | 0.00 | 38.06 | 38.06 | 7.8 |

### qwen3.6:36b

| fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 24.84 | 24.91 | 24.92 | 0.10 | 24.71 | 24.92 | 55.5 |
| home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 30.80 | 30.76 | 30.94 | 0.10 | 30.71 | 30.94 | 55.3 |
| software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 21.22 | 21.23 | 21.30 | 0.07 | 21.12 | 21.30 | 55.5 |
| simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 25.59 | 25.59 | 25.71 | 0.10 | 25.47 | 25.71 | 55.1 |
| home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 33.70 | 33.67 | 33.95 | 0.19 | 33.49 | 33.95 | 55.0 |
| software-plan | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 33.81 | 33.70 | 34.05 | 0.17 | 33.67 | 34.05 | 55.1 |
| simple-product | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 33.95 | 34.00 | 34.13 | 0.17 | 33.73 | 34.13 | 55.3 |
| home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 13.65 | 13.67 | 13.77 | 0.11 | 13.50 | 13.77 | 55.4 |
| software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 23.59 | 23.48 | 23.95 | 0.26 | 23.35 | 23.95 | 54.9 |

### tev1:0.8b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 0.00 | - | - | 0 | 2.51 | 2.51 | 2.52 | 0.01 | 2.50 | 2.52 | 156.0 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 0.00 | - | - | 0 | 13.48 | 13.52 | 13.60 | 0.11 | 13.32 | 13.60 | 142.8 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 0.00 | - | - | 0 | 13.86 | 13.72 | 14.16 | 0.22 | 13.69 | 14.16 | 141.1 |
| overnight-20261014 | simple-product | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 13.94 | 13.81 | 14.25 | 0.22 | 13.77 | 14.25 | 133.3 |
| overnight-20261014 | home-project | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 13.63 | 13.62 | 13.74 | 0.08 | 13.54 | 13.74 | 132.8 |
| overnight-20261014 | software-plan | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 12.76 | 12.92 | 12.93 | 0.22 | 12.45 | 12.93 | 141.2 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 12.84 | 12.81 | 12.90 | 0.04 | 12.81 | 12.90 | 142.7 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 12.38 | 12.51 | 12.52 | 0.20 | 12.10 | 12.52 | 144.7 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 12.58 | 12.61 | 12.62 | 0.06 | 12.50 | 12.62 | 144.2 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 0.00 | - | - | 0 | 2.57 | 2.51 | 2.71 | 0.09 | 2.49 | 2.71 | 154.8 |
| morning-20261015 | home-project | title | 5 | 1.00 | 0.00 | - | - | 0 | 13.81 | 13.41 | 15.51 | 0.88 | 13.04 | 15.51 | 143.9 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 14.51 | 14.48 | 14.72 | 0.14 | 14.35 | 14.72 | 133.3 |
| morning-20261015 | simple-product | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 13.36 | 13.35 | 13.81 | 0.31 | 12.94 | 13.81 | 137.4 |
| morning-20261015 | home-project | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 13.22 | 12.89 | 14.15 | 0.67 | 12.46 | 14.15 | 140.4 |
| morning-20261015 | software-plan | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 12.75 | 13.01 | 13.05 | 0.36 | 12.15 | 13.05 | 140.0 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 12.80 | 12.71 | 13.57 | 0.41 | 12.35 | 13.57 | 143.9 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 13.22 | 13.23 | 14.22 | 0.63 | 12.29 | 14.22 | 136.4 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 14.55 | 14.57 | 14.98 | 0.30 | 14.16 | 14.98 | 124.4 |

### qwen3.5:4b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 25.24 | 25.23 | 25.30 | 0.05 | 25.19 | 25.30 | 50.5 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 23.98 | 23.99 | 24.11 | 0.11 | 23.84 | 24.11 | 50.3 |
| overnight-20261014 | software-plan | title | 3 | 0.00 | 0.00 | - | - | 0 | 39.29 | 39.24 | 39.41 | 0.08 | 39.23 | 39.41 | 50.2 |
| overnight-20261014 | simple-product | initial_questions | 3 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 29.39 | 29.27 | 29.73 | 0.24 | 29.17 | 29.73 | 49.8 |
| overnight-20261014 | home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 37.27 | 37.16 | 37.52 | 0.17 | 37.14 | 37.52 | 49.7 |
| overnight-20261014 | software-plan | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 37.30 | 37.21 | 37.62 | 0.23 | 37.08 | 37.62 | 49.9 |
| overnight-20261014 | simple-product | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 37.28 | 37.17 | 37.54 | 0.19 | 37.12 | 37.54 | 50.2 |
| overnight-20261014 | home-project | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 36.64 | 36.56 | 36.83 | 0.14 | 36.52 | 36.83 | 50.4 |
| overnight-20261014 | software-plan | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 36.92 | 37.02 | 37.05 | 0.17 | 36.68 | 37.05 | 50.4 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 25.29 | 25.12 | 25.75 | 0.25 | 25.10 | 25.75 | 50.6 |
| morning-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 23.72 | 23.74 | 23.81 | 0.06 | 23.63 | 23.81 | 50.8 |
| morning-20261015 | software-plan | title | 5 | 0.00 | 0.00 | - | - | 0 | 39.19 | 39.26 | 39.78 | 0.46 | 38.56 | 39.78 | 50.1 |
| morning-20261015 | simple-product | initial_questions | 5 | 1.00 | 1.00 | 1.00 | 1.00 | 0 | 29.91 | 29.32 | 32.68 | 1.41 | 28.86 | 32.68 | 49.8 |
| morning-20261015 | home-project | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 42.49 | 42.49 | 42.95 | 0.32 | 42.11 | 42.95 | 43.4 |
| morning-20261015 | software-plan | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 38.91 | 36.94 | 43.42 | 2.69 | 36.77 | 43.42 | 50.2 |
| morning-20261015 | simple-product | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 37.39 | 37.31 | 37.73 | 0.19 | 37.17 | 37.73 | 50.0 |
| morning-20261015 | home-project | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 36.64 | 36.57 | 36.98 | 0.17 | 36.50 | 36.98 | 50.3 |
| morning-20261015 | software-plan | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 36.88 | 36.87 | 37.10 | 0.13 | 36.73 | 37.10 | 50.2 |

### qwen3-vl:4b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 0.67 | 0.67 | - | - | 0 | 35.51 | 34.36 | 37.82 | 1.63 | 34.35 | 37.82 | 52.4 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 1.00 | - | - | 0 | 10.03 | 10.03 | 10.05 | 0.01 | 10.02 | 10.05 | 53.8 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 1.00 | - | - | 0 | 5.66 | 5.66 | 5.68 | 0.02 | 5.63 | 5.68 | 54.2 |
| overnight-20261014 | simple-product | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 36.13 | 36.21 | 36.27 | 0.16 | 35.92 | 36.27 | 52.3 |
| overnight-20261014 | home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 35.50 | 35.53 | 35.62 | 0.12 | 35.35 | 35.62 | 52.3 |
| overnight-20261014 | software-plan | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 35.73 | 35.69 | 35.84 | 0.08 | 35.65 | 35.84 | 52.2 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 11.08 | 11.01 | 11.25 | 0.12 | 10.99 | 11.25 | 53.6 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 9.45 | 9.38 | 9.62 | 0.12 | 9.35 | 9.62 | 53.6 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 8.40 | 8.41 | 8.47 | 0.07 | 8.32 | 8.47 | 53.8 |
| morning-20261015 | simple-product | title | 5 | 0.80 | 0.80 | - | - | 0 | 35.27 | 34.40 | 38.59 | 1.68 | 34.19 | 38.59 | 52.3 |
| morning-20261015 | home-project | title | 5 | 1.00 | 1.00 | - | - | 0 | 9.97 | 9.96 | 10.09 | 0.08 | 9.88 | 10.09 | 54.1 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 1.00 | - | - | 0 | 5.62 | 5.61 | 5.79 | 0.09 | 5.55 | 5.79 | 54.3 |
| morning-20261015 | simple-product | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 36.93 | 36.07 | 40.30 | 1.69 | 35.87 | 40.30 | 52.0 |
| morning-20261015 | home-project | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 41.64 | 41.65 | 42.53 | 0.58 | 40.83 | 42.53 | 44.7 |
| morning-20261015 | software-plan | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 36.72 | 35.86 | 41.04 | 2.19 | 35.25 | 41.04 | 51.9 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 11.25 | 11.32 | 11.38 | 0.13 | 11.06 | 11.38 | 52.7 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 9.63 | 9.63 | 9.83 | 0.12 | 9.47 | 9.83 | 52.4 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 8.52 | 8.48 | 8.77 | 0.19 | 8.30 | 8.77 | 52.7 |

### tinyllama:1b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 0.07 | 0.06 | 0.10 | 0.02 | 0.06 | 0.10 | 315.5 |
| overnight-20261014 | home-project | title | 3 | 1.00 | 0.00 | - | - | 0 | 0.10 | 0.09 | 0.11 | 0.01 | 0.09 | 0.11 | 302.2 |
| overnight-20261014 | software-plan | title | 3 | 1.00 | 0.00 | - | - | 0 | 0.10 | 0.10 | 0.12 | 0.01 | 0.09 | 0.12 | 298.5 |
| overnight-20261014 | simple-product | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 61.87 | 61.80 | 62.58 | 0.56 | 61.21 | 62.58 | 228.1 |
| overnight-20261014 | home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 88.38 | 88.41 | 88.93 | 0.46 | 87.81 | 88.93 | 232.3 |
| overnight-20261014 | software-plan | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 88.89 | 88.86 | 88.98 | 0.07 | 88.82 | 88.98 | 230.9 |
| overnight-20261014 | simple-product | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 0.77 | 0.78 | 0.79 | 0.02 | 0.74 | 0.79 | 245.1 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 0.57 | 0.57 | 0.60 | 0.02 | 0.55 | 0.60 | 239.1 |
| overnight-20261014 | software-plan | summarize | 3 | 1.00 | 0.00 | - | - | 0 | 88.28 | 88.06 | 88.80 | 0.37 | 87.98 | 88.80 | 232.9 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 0.07 | 0.06 | 0.10 | 0.02 | 0.06 | 0.10 | 305.9 |
| morning-20261015 | home-project | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.10 | 0.10 | 0.12 | 0.01 | 0.10 | 0.12 | 286.1 |
| morning-20261015 | software-plan | title | 5 | 1.00 | 0.00 | - | - | 0 | 0.11 | 0.10 | 0.13 | 0.01 | 0.10 | 0.13 | 273.3 |
| morning-20261015 | simple-product | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 61.05 | 60.95 | 61.70 | 0.35 | 60.67 | 61.70 | 231.2 |
| morning-20261015 | home-project | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 89.41 | 89.61 | 90.84 | 1.21 | 87.47 | 90.84 | 226.9 |
| morning-20261015 | software-plan | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 92.42 | 91.65 | 95.50 | 1.74 | 90.47 | 95.50 | 223.9 |
| morning-20261015 | simple-product | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 0.77 | 0.75 | 0.86 | 0.05 | 0.72 | 0.86 | 238.5 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 0.55 | 0.55 | 0.58 | 0.02 | 0.53 | 0.58 | 247.6 |
| morning-20261015 | software-plan | summarize | 5 | 1.00 | 0.00 | - | - | 0 | 90.63 | 90.50 | 91.30 | 0.35 | 90.33 | 91.30 | 226.6 |

### qwen3.5:9.7b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 1.00 | 1.00 | - | - | 0 | 35.39 | 35.27 | 35.63 | 0.17 | 35.26 | 35.63 | 29.3 |
| overnight-20261014 | home-project | title | 3 | 0.00 | 0.00 | - | - | 0 | 67.24 | 67.19 | 67.43 | 0.14 | 67.11 | 67.43 | 29.3 |
| overnight-20261014 | software-plan | title | 3 | 0.00 | 0.00 | - | - | 0 | 67.18 | 67.12 | 67.31 | 0.09 | 67.11 | 67.31 | 29.3 |
| overnight-20261014 | simple-product | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 64.00 | 63.82 | 64.41 | 0.29 | 63.77 | 64.41 | 29.3 |
| overnight-20261014 | home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 63.24 | 62.93 | 63.86 | 0.44 | 62.92 | 63.86 | 29.3 |
| overnight-20261014 | software-plan | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 64.95 | 64.90 | 65.38 | 0.33 | 64.57 | 65.38 | 28.6 |
| overnight-20261014 | simple-product | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 64.54 | 64.03 | 66.22 | 1.22 | 63.38 | 66.22 | 29.1 |
| overnight-20261014 | home-project | summarize | 3 | 1.00 | 1.00 | - | - | 0 | 62.93 | 62.97 | 63.25 | 0.28 | 62.58 | 63.25 | 29.4 |
| overnight-20261014 | software-plan | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 61.94 | 61.63 | 62.64 | 0.49 | 61.57 | 62.64 | 30.0 |
| morning-20261015 | simple-product | title | 5 | 1.00 | 1.00 | - | - | 0 | 35.17 | 35.08 | 35.54 | 0.19 | 35.04 | 35.54 | 29.4 |
| morning-20261015 | home-project | title | 5 | 0.00 | 0.00 | - | - | 0 | 66.69 | 66.59 | 67.26 | 0.29 | 66.47 | 67.26 | 29.5 |
| morning-20261015 | software-plan | title | 5 | 0.00 | 0.00 | - | - | 0 | 66.73 | 66.66 | 66.86 | 0.10 | 66.63 | 66.86 | 29.5 |
| morning-20261015 | simple-product | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 63.65 | 63.60 | 63.90 | 0.13 | 63.54 | 63.90 | 29.4 |
| morning-20261015 | home-project | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 62.83 | 62.78 | 63.25 | 0.28 | 62.52 | 63.25 | 29.5 |
| morning-20261015 | software-plan | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 63.22 | 63.07 | 63.62 | 0.26 | 62.95 | 63.62 | 29.4 |
| morning-20261015 | simple-product | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 63.42 | 63.25 | 63.94 | 0.28 | 63.20 | 63.94 | 29.5 |
| morning-20261015 | home-project | summarize | 5 | 1.00 | 1.00 | - | - | 0 | 62.65 | 62.58 | 63.00 | 0.19 | 62.43 | 63.00 | 29.4 |
| morning-20261015 | software-plan | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 63.03 | 62.91 | 63.45 | 0.24 | 62.82 | 63.45 | 29.4 |

### qwen3.5:0.8b

| run | fixture | interaction | reps | ok | clean | json | arr | timeouts | mean | p50 | p95 | std | min | max | tps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| overnight-20261014 | simple-product | title | 3 | 0.00 | 0.00 | - | - | 0 | 14.41 | 14.45 | 14.46 | 0.07 | 14.31 | 14.46 | 136.1 |
| overnight-20261014 | home-project | title | 3 | 0.00 | 0.00 | - | - | 0 | 14.69 | 14.68 | 14.74 | 0.04 | 14.64 | 14.74 | 134.4 |
| overnight-20261014 | software-plan | title | 3 | 0.00 | 0.00 | - | - | 0 | 14.72 | 14.73 | 14.76 | 0.04 | 14.67 | 14.76 | 133.7 |
| overnight-20261014 | simple-product | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 14.27 | 14.32 | 14.33 | 0.07 | 14.17 | 14.33 | 131.8 |
| overnight-20261014 | home-project | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 14.01 | 14.01 | 14.11 | 0.08 | 13.91 | 14.11 | 131.8 |
| overnight-20261014 | software-plan | initial_questions | 3 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 14.09 | 14.10 | 14.13 | 0.04 | 14.05 | 14.13 | 132.1 |
| overnight-20261014 | simple-product | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 14.19 | 14.21 | 14.25 | 0.06 | 14.12 | 14.25 | 131.3 |
| overnight-20261014 | home-project | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 14.08 | 14.08 | 14.20 | 0.10 | 13.96 | 14.20 | 130.7 |
| overnight-20261014 | software-plan | summarize | 3 | 0.00 | 0.00 | - | - | 0 | 14.15 | 14.17 | 14.19 | 0.03 | 14.11 | 14.19 | 130.8 |
| morning-20261015 | simple-product | title | 5 | 0.00 | 0.00 | - | - | 0 | 14.38 | 14.37 | 14.52 | 0.07 | 14.31 | 14.52 | 136.5 |
| morning-20261015 | home-project | title | 5 | 0.00 | 0.00 | - | - | 0 | 14.83 | 14.84 | 14.89 | 0.04 | 14.79 | 14.89 | 132.7 |
| morning-20261015 | software-plan | title | 5 | 0.00 | 0.00 | - | - | 0 | 14.81 | 14.80 | 14.92 | 0.06 | 14.74 | 14.92 | 133.1 |
| morning-20261015 | simple-product | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 14.36 | 14.35 | 14.48 | 0.10 | 14.20 | 14.48 | 130.7 |
| morning-20261015 | home-project | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 14.22 | 14.18 | 14.54 | 0.17 | 14.08 | 14.54 | 130.2 |
| morning-20261015 | software-plan | initial_questions | 5 | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 14.24 | 14.26 | 14.31 | 0.06 | 14.14 | 14.31 | 130.5 |
| morning-20261015 | simple-product | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 14.45 | 14.48 | 14.56 | 0.11 | 14.26 | 14.56 | 128.8 |
| morning-20261015 | home-project | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 14.18 | 14.16 | 14.26 | 0.04 | 14.13 | 14.26 | 129.9 |
| morning-20261015 | software-plan | summarize | 5 | 0.00 | 0.00 | - | - | 0 | 14.29 | 14.21 | 14.48 | 0.14 | 14.13 | 14.48 | 130.1 |

## Field guide

**Per-model table**

One row per model. When a model was swept in several batches (and `--per-model
all`, the default, is in effect) its rows are pooled: rates pool their cells, and
`suite_s` averages one pass over the suite per included run, so every figure
covers all included runs at once. The contributing batches are named in the
`run-ids:` header line, and are broken out per run in the per-interaction tables
below.

- `model` — display name with any `:latest` alias resolved to its real size tag
  (e.g. `llama3.2:latest` → `llama3.2:3b`), distilled from the model's own
  metadata. The canonical id lives in the row file as `model_id`.
- `size` — parameter size label (`general.size_label`, falling back to
  `details.parameter_size`), e.g. `3B`, `7.6B`.
- `quality` — composite 0–100 quality score (see *Composite quality score* below).
- `tier` — `good` / `ok` / `discouraged`, a label derived from the
  quality × speed grid below. It is a presentation label, never a filter: every
  model is listed whichever cell it lands in. (`unknown` — no usable data on an
  axis — is the fourth tier, and belongs to the app, which sees models this tool
  has never swept.)
- `startup_s` — cold-load time in seconds (from the unscored warmup's
  Ollama `load_duration`): the delay a fresh session feels before its first token.
- `suite_s` — average time to run the whole suite once: one pass over every
  (fixture, interaction) group, averaged across runs. Averaging rather than
  summing across runs is what keeps it independent of `--runs`, so a `--runs 3`
  and a `--runs 5` sweep of the same model compare directly. Cells that errored
  or timed out are excluded (they delivered no answer), so a model that hangs is
  judged only on what it completed — read this beside the `timeout×N` / `error×N`
  flags rather than on its own.
- `req_s` — mean per-request time in seconds (monotonic, sleep-excluding),
  averaged across all (fixture, interaction) groups and over successful requests
  only.
- `p95_s` — worst-case (95th percentile) request time across the groups.
- `ok` — share of every included request that produced *usable* output: a non-empty
  title, a parsed JSON array of non-empty strings for questions, or a non-empty
  summary. Pooled across all included runs, not averaged per batch.
- `clean` — share of every included request that fully conformed to format: title
  ≤60 chars, no surrounding quotes, no preamble, no trailing period, single line;
  summary ≤150 words, no bullets/headings, no preamble.
- `json` — share of question-generation requests whose reply parsed as JSON
  (blank where JSON isn't requested).
- `est_gb` — rough resident-memory estimate (weights + KV/runtime headroom), from
  the on-disk weight size or the parameter count × quantization bits.
- `flags` — space-separated markers: `in-progress` (row was interrupted mid-run),
  `timeout×N` (N requests hit the HTTP timeout), `error×N` (non-timeout failures),
  `slept×N` (N requests spanned a machine sleep — see below), `>16GB` (estimated
  footprint exceeds the device budget; flagged, not hidden).

**Per-interaction table (one per model, same order as the model table)**

- `run` — which batch a row was measured in. Only shown when the model was
  pooled from more than one batch; then each included run gets its own rows,
  side by side, in sweep order.
- `fixture` — which of the fixtures the row measured (`simple-product`,
  `home-project`, `software-plan`).
- `interaction` — `title`, `initial_questions`, or `summarize`.
- `reps` — number of repetitions aggregated for this row.
- `ok`, `clean`, `json` — as above, but for this single group.
- `arr` — share whose reply was a JSON array of non-empty strings (stricter than
  `json`: a syntactically valid array of non-strings does not count).
- `timeouts` — repetitions that hit the HTTP timeout (recognized from the explicit
  flag, or from the error text on rows written before that flag existed).
- `mean`, `p50`, `p95`, `std`, `min`, `max` — wall-clock request latency
  distribution, in seconds (`std` is the population standard deviation).
- `tps` — median output tokens/sec at steady state (`eval_count / eval_duration`).

**Row file (`docs/model-evals/<model-slug>-<run-id>.json`)**

- `schema` — row-shape version; rows older than `--min-schema` are skipped.
- `fixtures_version` — prompt/fixture version; rows from other versions are
  skipped unless `--allow-fixtures`.
- `model_id` — the canonical Ollama id (`llama3.2:latest`); `model` holds the
  display name, size, quantization, family, format, context length, capabilities,
  and on-disk `size_bytes`.
- `run` — run parameters: `id`, `started_at`, `base_url`, `runs`, `num_ctx`,
  `temperature`, `load_timeout`, and the host `hardware`.
- `warmup` — the unscored cold-load request: `ok`, `wall_s`, `load_s`, `timings`,
  and `timeout`.
- `cells` — one entry per scored request: `fixture`, `interaction`, `run`,
  `latency_s` (monotonic, sleep-excluding), `wall_s` (realtime), `sleep_s`
  (machine-sleep seconds spanned), `timings` (Ollama counters), the verbatim
  `raw` reply, `error`, and `timeout`.
- `started_at` / `finished_at` / `in_progress` — row-level timing; `in_progress`
  is true while a run is still writing (or if it was interrupted).

## Sleep and timing

`latency_s` comes from `time.perf_counter()` (`mach_absolute_time` on macOS),
which **stops while the machine is asleep**, so a request that spans a lid-close
reports only its compute time. `wall_s` comes from CLOCK_REALTIME, which keeps
going; when the two diverge by more than a few seconds the cell records `sleep_s`
and the model is flagged `slept×N`. Because a sleep also lets Ollama unload the
model (its `keep_alive` expires), the next request can silently pay a reload — the
`slept` flag is your cue to re-run that model rather than trust its timing.

Persistence: rows are flushed after every request, so a sleep, a kill, or a crash
never loses completed work. Re-running with the same `--run-id` resumes — it skips
finished rows and **re-runs any row left `in_progress`**.

## Composite quality score

`quality` is a weighted blend of the model-level rates, scaled to 0–100:

    quality = 100 × (0.5·json + 0.3·clean + 0.2·ok) / (0.5 + 0.3 + 0.2)

`json` is weighted highest because the app's Q&A loop depends on parseable
question arrays. Weights are renormalized over whichever components a row
actually has (so a partial row is not penalized for absent cells). Speed is
deliberately excluded — it is reported separately as `startup_s`/`suite_s`.

## Recommendation grid

Two independent axes, because they genuinely disagree: a flawless-but-slow model
is a different proposition from a fast-but-flawed one, and one combined score
would have to hide that.

- **quality** bands the composite score: `strong` ≥ 98, `adequate` ≥ 85, else `weak`.
- **speed** bands the average suite time: `fast` < 10s, `fine` < 35s, else `slow`.
- `unknown` is its own band on either axis — a model with no usable data is
  ranked and shown, never guessed at or demoted. This tool always has data, so it
  never prints `unknown`; the app, which meets unevaluated models constantly,
  does, and gets `unknown` as the tier for them.

`tier` is then a *label on the grid*: **good** = strong × fast, **ok** = strong ×
fine or adequate × fast, and everything else is **discouraged**. It exists so the
UI has one name to show, but the axes are the real data — a `strong`/`slow` model
is still fully listed, just not dressed up as a safe default.

Both axes are derived at read time, so the cuts are tunable without re-running a
sweep.

