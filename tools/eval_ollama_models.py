#!/usr/bin/env python3
"""
Evaluate Ollama models for formatting, structure, and speed. Local-only.

Storage is files-as-rows: **one JSON file per (run, model)**, all flat in one
folder, named `<model-slug>-<run-id>.json` so a directory listing groups by model:

    docs/model-evals/llama3.2_3b-20261014-210806.json
    docs/model-evals/qwen2.5_7b-20261014-210806.json

Each row is self-describing (run params, hardware, model metadata, the unscored
warmup, and one entry per scored request) and is flushed after every request, so
an interrupted sweep keeps its work. `report` globs rows and aggregates them.

Scoring is *derived*: a row stores each cell's raw reply and timings, and the
report re-scores from `raw` at read time. Tightening a scoring rule therefore
re-scores old rows instead of invalidating them; `schema` only guards structural
changes that can't be derived, and `fixtures_version` guards prompt comparability.
"""
import argparse
import json
import re
import signal
import statistics
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

try:
    import httpx
except ImportError:
    print("httpx is required. Install with: pip install httpx", file=sys.stderr)
    sys.exit(1)

TOOLS_DIR = Path(__file__).parent
ROOT_DIR = TOOLS_DIR.parent
EVALS_DIR = ROOT_DIR / "docs" / "model-evals"

# Bump SCHEMA when the row shape changes in a way that can't be derived from the
# stored raw replies (e.g. a renamed field, a dropped measurement). Bump
# FIXTURES_VERSION when the prompts or fixtures change, since rows measured under
# different prompts are not comparable.
SCHEMA = 1
FIXTURES_VERSION = 1

# Composite quality score weights. `json` dominates because the app's Q&A loop
# depends on parseable question arrays; `clean` covers title/summary formatting;
# `ok` is basic usability. Weights are renormalized over whichever components a
# row actually has, so a partial row isn't penalized for missing cells.
QUALITY_WEIGHTS = (("json_ok_rate", 0.5), ("clean_rate", 0.3), ("ok_rate", 0.2))

# Two-axis recommendation bands. Both axes are derived at read time, so these
# cuts are tunable without re-running a sweep. Quality is the composite score;
# speed is the average suite time (how long one pass over the whole suite takes).
# Each tuple is an inclusive floor (quality) or exclusive ceiling (speed) and the
# band it opens; both lists must run high-to-low. Quality deliberately avoids the
# words used for tiers below, so "good" means one thing in this report.
QUALITY_BANDS = ((98.0, "strong"), (85.0, "adequate"))
SPEED_BANDS = ((10.0, "fast"), (35.0, "fine"))
BAND_QUALITY_ORDER = ("strong", "adequate", "weak", "unknown")
BAND_SPEED_ORDER = ("fast", "fine", "slow", "unknown")

# The tier is a *label* on that grid, not the underlying model: a slow-but-
# flawless model reads `discouraged` rather than being hidden, which is what
# keeps the curation presentation-only. `unknown` only ever arises when an axis
# has no data — i.e. in the app, where most installed models have never been
# swept — never in this tool's output, where every row carries measurements.
GOOD_CELLS = frozenset({("strong", "fast")})
OK_CELLS = frozenset({("strong", "fine"), ("adequate", "fast")})
TIER_GOOD, TIER_OK, TIER_DISCOURAGED, TIER_UNKNOWN = "good", "ok", "discouraged", "unknown"

# Bits per weight by quantization level, for the memory estimate. Approximate
# on purpose — this only feeds a "will it fit?" flag, not a precise number.
QUANT_BITS = {
    "Q2_K": 2.6,
    "Q3_K_S": 3.5,
    "Q3_K_M": 3.9,
    "Q3_K_L": 4.3,
    "Q4_0": 4.5,
    "Q4_1": 5.0,
    "Q4_K_S": 4.5,
    "Q4_K_M": 4.5,
    "Q5_0": 5.5,
    "Q5_K_S": 5.5,
    "Q5_K_M": 5.5,
    "Q6_K": 6.6,
    "Q8_0": 8.5,
    "NVFP4": 4.0,
    "NVFP8": 8.0,
    "F16": 16.0,
    "BF16": 16.0,
    "F32": 32.0,
}
DEFAULT_QUANT_BITS = 4.5
# Default device memory budget for the "will this fit?" flag (a mid-range phone).
DEFAULT_DEVICE_GB = 16.0
# A single request whose wall-clock time exceeds its monotonic time by more than
# this many seconds spanned a machine sleep (macOS mach_absolute_time pauses then).
SLEEP_THRESHOLD_S = 5.0

INTERACTIONS = ["title", "initial_questions", "summarize"]

# Fixture set matching PlanningEngine prompt shapes
FIXTURES = [
    {
        "name": "simple-product",
        "title_input": "A mobile app to track daily water intake",
        "synopsis": "Build a simple iOS/Android app that reminds users to drink water, logs intake, and shows progress toward a daily goal.",
        "transcript": "(none)",
        "target_interactions": ["title", "initial_questions", "summarize"],
    },
    {
        "name": "home-project",
        "title_input": "Kitchen cabinet refinish plan",
        "synopsis": "Refinish existing kitchen cabinets to match a new color scheme with minimal cost and low VOC materials.",
        "transcript": "Q: What is the current cabinet material?\nA: committed: solid oak with factory finish\nQ: What is the desired timeline?\nA: committed: 2–3 weekends",
        "target_interactions": ["title", "initial_questions", "summarize"],
    },
    {
        "name": "software-plan",
        "title_input": "Project planning app",
        "synopsis": "An edge-LLM powered project planning app with iterative question synthesis to help users plan projects.",
        "transcript": "Q: Who is the target user?\nA: committed: solo builders and small teams\nQ: Offline first?\nA: committed: yes",
        "target_interactions": ["title", "initial_questions", "summarize"],
    },
]


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #

def parse_run_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="eval_ollama_models.py run",
        description="Evaluate Ollama models and write one row file per model.",
        epilog=(
            "Subcommands:\n"
            "  (default), run     evaluate models and write rows\n"
            "  report [paths...]  aggregate rows into a comparison view\n"
        ),
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    p.add_argument("--base-url", default="http://localhost:11434", help="Ollama base URL")
    p.add_argument("--models", nargs="*", help="Filter to specific model names")
    p.add_argument(
        "--exclude",
        nargs="*",
        default=None,
        help="Skip these models while running (full id like qwq:latest, or bare family like qwq). "
             "Useful to resume a batch without a model you deliberately cancelled",
    )
    p.add_argument("--runs", type=int, default=3, help="Runs per (model, fixture, interaction)")
    p.add_argument("--timeout", type=float, default=120.0, help="HTTP timeout for scored requests, seconds")
    p.add_argument(
        "--load-timeout",
        type=float,
        default=600.0,
        help="HTTP timeout for the unscored cold-load request (weights take a while to load)",
    )
    p.add_argument("--temperature", type=float, default=0.0, help="Temperature for deterministic-ish output")
    p.add_argument("--num-ctx", type=int, default=2048, help="num_ctx parameter if supported")
    p.add_argument(
        "--rows-dir",
        default=str(EVALS_DIR),
        help="Folder the row files live in (flat, one file per model per run)",
    )
    p.add_argument("--run-id", default=None, help="Run id used in filenames (default: start timestamp)")
    p.add_argument("--force", action="store_true", help="Re-evaluate even if a row file already exists")
    p.add_argument(
        "--device-memory-gb",
        type=float,
        default=DEFAULT_DEVICE_GB,
        help="Flag models whose estimated footprint exceeds this (0 disables)",
    )
    p.add_argument(
        "--stop-between-models",
        action="store_true",
        default=True,
        help="Stop/unload Ollama model between models to free memory (default: True)",
    )
    p.add_argument(
        "--no-stop-between-models",
        action="store_false",
        dest="stop_between_models",
        help="Do not stop Ollama between models",
    )
    return p.parse_args(argv)


def parse_report_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="eval_ollama_models.py report",
        description="Aggregate saved row files into one comparison view.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    p.add_argument(
        "paths",
        nargs="*",
        default=[str(EVALS_DIR)],
        help="Folders or individual row JSON files. Defaults to docs/model-evals.",
    )
    p.add_argument(
        "--min-schema",
        type=int,
        default=SCHEMA,
        help="Skip rows with a schema older than this (they can't contribute useful data)",
    )
    p.add_argument(
        "--allow-fixtures",
        action="store_true",
        help="Include rows measured under a different prompt/fixture version",
    )
    p.add_argument(
        "--per-model",
        choices=["latest", "max-cells", "all"],
        default="all",
        help=(
            "Which rows feed the report when a model has several: every row pooled into one line "
            "(all, the default), its newest, or the one with most cells"
        ),
    )
    p.add_argument(
        "--run-id",
        dest="run_ids",
        action="append",
        default=None,
        metavar="RUN_ID",
        help="Only include rows from this run id (e.g. morning-20261015). Repeatable to compare batches",
    )
    p.add_argument(
        "--exclude",
        dest="exclude_models",
        action="append",
        default=None,
        metavar="MODEL",
        help="Drop this model (matched against its id, display name, or bare family name). Repeatable",
    )
    p.add_argument("--full", action="store_true", help="Print the full markdown report (with field guide + per-model detail)")
    p.add_argument("--out", default=None, help="Write the full report markdown to this path")
    p.add_argument(
        "--device-memory-gb",
        type=float,
        default=DEFAULT_DEVICE_GB,
        help="Flag models whose estimated footprint exceeds this (0 disables)",
    )
    p.add_argument(
        "--emit",
        choices=["kotlin", "json"],
        default=None,
        help="Emit source-ready snippet for copy-paste into ModelProfiles/curation (JSON or Kotlin)",
    )
    p.add_argument(
        "--emit-out",
        default=None,
        help="Write the emitted snippet to this path instead of stdout",
    )
    return p.parse_args(argv)


# --------------------------------------------------------------------------- #
# Ollama access
# --------------------------------------------------------------------------- #

def get_installed_models(base_url: str, timeout: float) -> List[Dict[str, Any]]:
    # Try to get from CLI first for accurate sizes
    try:
        result = subprocess.run(["ollama", "list"], timeout=timeout, capture_output=True, text=True)
        if result.returncode == 0 and result.stdout:
            models = []
            lines = result.stdout.strip().splitlines()
            for line in lines[1:]:  # skip header
                parts = line.split()
                if parts:
                    name = parts[0]
                    # Find size - it's usually in MB/GB
                    size_bytes = None
                    for p in parts:
                        if 'GB' in p.upper():
                            try:
                                num = float(re.findall(r'[\d.]+', p)[0])
                                size_bytes = int(num * 1024**3)
                            except:
                                pass
                        elif 'MB' in p.upper():
                            try:
                                num = float(re.findall(r'[\d.]+', p)[0])
                                size_bytes = int(num * 1024**2)
                            except:
                                pass
                    models.append({"name": name, "model": name, "size": size_bytes})
            if models:
                return models
    except Exception:
        pass
    try:
        with httpx.Client(timeout=timeout) as client:
            resp = client.get(f"{base_url}/api/tags")
            resp.raise_for_status()
            return resp.json().get("models", [])
    except Exception as e:
        print(f"Failed to list models: {e}", file=sys.stderr)
        sys.exit(1)


def get_model_info(base_url: str, model: str, timeout: float) -> Optional[Dict[str, Any]]:
    try:
        with httpx.Client(timeout=timeout) as client:
            resp = client.post(f"{base_url}/api/show", json={"model": model})
            resp.raise_for_status()
            return resp.json()
    except Exception:
        return None


def stop_ollama(base_url: str, timeout: float, model_id: str) -> None:
    # Prefer CLI over API
    try:
        subprocess.run(["ollama", "stop", model_id], timeout=timeout, capture_output=True)
        return
    except Exception:
        pass
    try:
        with httpx.Client(timeout=timeout) as client:
            client.post(f"{base_url}/api/stop", json={"model": model_id})
    except Exception:
        pass


def slugify(model_id: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", model_id) or "model"


def hardware_info() -> Dict[str, Any]:
    """Best-effort host description, so a row is self-explanatory later."""
    info: Dict[str, Any] = {"platform": sys.platform, "python": sys.version.split()[0]}
    try:
        info["cpu"] = subprocess.run(
            ["sysctl", "-n", "machdep.cpu.brand_string"], capture_output=True, text=True
        ).stdout.strip()
    except Exception:
        pass
    try:
        raw = subprocess.run(["sysctl", "-n", "hw.memsize"], capture_output=True, text=True).stdout.strip()
        if raw:
            info["ram_gb"] = round(int(raw) / 1024**3, 1)
    except Exception:
        pass
    return {k: v for k, v in info.items() if v}


# --------------------------------------------------------------------------- #
# Prompts + scoring (scoring is a pure function of the reply)
# --------------------------------------------------------------------------- #

def build_prompt(interaction: str, fixture: Dict[str, Any]) -> str:
    title = fixture["title_input"]
    synopsis = fixture["synopsis"]
    transcript = fixture["transcript"]
    if interaction == "title":
        return (
            "You are a project-planning assistant. Recommend a short, memorable project title from the user's synopsis. "
            "Reply with only the title — no quotes, no explanation, no trailing period. Keep it under 60 characters.\n\n"
            f"Project synopsis:\n{synopsis}\n\nReturn the project title."
        )
    if interaction == "initial_questions":
        return (
            "You are a project-planning assistant. The user provides the current planning phase and the interview history "
            "(format: question | status/answer). Ask 3-5 new, focused questions for this phase. Reply with ONLY a valid JSON array of strings, "
            "e.g. [\"Question 1?\", \"Question 2?\"]\n\n"
            f"Planning a project titled \"{title}\".\nProject synopsis:\n{synopsis}\n\nThe interview so far:\n{transcript}\n\n"
            "Propose new questions for this phase. Focus on Phase: Scope & Goals — Define the project's purpose, boundaries, and success criteria.\n"
            "but if there are crucial details missing that block this phase you may go deeper on what has been answered."
        )
    if interaction == "summarize":
        return (
            "You are a project-planning assistant. Condense a finished planning phase of a project interview into a short summary that a later "
            "planning round can rely on. Keep the decisions the user made, the facts and constraints they committed to, and anything they explicitly ruled out — "
            "those are what must not be re-asked. Drop the reasoning, the restatement, and the questions with nothing behind them. Write plain prose under 150 words, "
            "no headings, no bullets, no preamble.\n\n"
            f"Planning a project titled \"{title}\".\nProject synopsis:\n{synopsis}\n\nThis planning phase (Scope & Goals) is finished:\n{transcript}\n\n"
            "Summarize what this phase settled, so a later round does not ask again."
        )
    return synopsis


TITLE_MAX_CHARS = 60
SUMMARY_MAX_WORDS = 150

# A title that opens with one of these is the model explaining itself rather than
# answering, which is a real formatting failure the app's prompt asks it to avoid.
TITLE_PREAMBLES = (
    "the title is",
    "title:",
    "suggested title",
    "here is",
    "here's",
    "i suggest",
)

SUMMARY_PREAMBLES = (
    "here is",
    "here's",
    "summary:",
    "in summary",
)


def _has_bullets_or_headings(text: str) -> bool:
    for line in text.splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith(("-", "*", "#", "•")):
            return True
        # "1. ", "2) " etc.
        head = s.split(" ", 1)[0]
        if head.endswith(".") or head.endswith(")"):
            if head[:-1].isdigit():
                return True
    return False


def score_result(interaction: str, text: str) -> Dict[str, Any]:
    """Pure function of (interaction, reply). Never reads anything else."""
    text_stripped = (text or "").strip()
    score: Dict[str, Any] = {
        "ok": False,
        "clean": False,
        "error": None,
        "json_ok": None,
        "array_strings": None,
        "word_count": len(text_stripped.split()) if text_stripped else 0,
        "chars": len(text_stripped),
    }
    try:
        if interaction == "title":
            if not text_stripped:
                score["error"] = "empty title"
                return score
            score["ok"] = True
            lowered = text_stripped.lower()
            problems = []
            if len(text_stripped) > TITLE_MAX_CHARS:
                problems.append(f">{TITLE_MAX_CHARS} chars")
            if text_stripped.endswith("."):
                problems.append("trailing period")
            if text_stripped[:1] in ("\"", "'") and text_stripped[-1:] == text_stripped[:1]:
                problems.append("wrapped in quotes")
            if lowered.startswith(TITLE_PREAMBLES):
                problems.append("preamble")
            if "\n" in text_stripped:
                problems.append("multi-line")
            if problems:
                score["error"] = "title: " + ", ".join(problems)
            else:
                score["clean"] = True
        elif interaction == "initial_questions":
            s = text_stripped
            start = s.find("[")
            end = s.rfind("]")
            s2 = s[start : end + 1] if start >= 0 and end > start else s
            try:
                arr = json.loads(s2)
                score["json_ok"] = True
                if isinstance(arr, list) and all(isinstance(x, str) and x.strip() for x in arr):
                    score["array_strings"] = True
                    score["ok"] = True
                    score["clean"] = True
                else:
                    score["array_strings"] = False
                    score["error"] = "not array of non-empty strings"
            except Exception as e:
                score["json_ok"] = False
                score["array_strings"] = False
                score["error"] = f"json parse failed: {e}"
        elif interaction == "summarize":
            if not text_stripped:
                score["error"] = "empty summary"
                return score
            score["ok"] = True
            problems = []
            if score["word_count"] > SUMMARY_MAX_WORDS:
                problems.append(f">{SUMMARY_MAX_WORDS} words")
            if _has_bullets_or_headings(text_stripped):
                problems.append("bullets/headings")
            if text_stripped.lower().startswith(SUMMARY_PREAMBLES):
                problems.append("preamble")
            if problems:
                score["error"] = "summary: " + ", ".join(problems)
            else:
                score["clean"] = True
    except Exception as e:
        score["error"] = str(e)
    return score


# --------------------------------------------------------------------------- #
# Measurement
# --------------------------------------------------------------------------- #

def _slept_s(perf_elapsed: float, wall_elapsed: float) -> float:
    """Seconds of machine sleep spanned by a request, from the two clock kinds.

    macOS `mach_absolute_time` (what `perf_counter` uses) stops while the system
    is asleep; CLOCK_REALTIME (`time.time`) keeps going. Their divergence is the
    sleep, which we keep out of latency stats but surface as a flag.
    """
    slept = wall_elapsed - perf_elapsed
    return round(slept, 3) if slept > SLEEP_THRESHOLD_S else 0.0


def _is_timeout(cell: Dict[str, Any]) -> bool:
    """Whether a cell hit the HTTP timeout.

    Newer cells carry an explicit `timeout` flag; rows written before that
    existed recorded the httpx client error, whose text ends in "timed out" /
    "timeout". Recognizing both keeps old rows correctly classified at read time.
    """
    if cell.get("timeout"):
        return True
    err = (cell.get("error") or "").lower()
    return "timed out" in err or "timeout" in err


def _fmt_elapsed(cell: Dict[str, Any]) -> str:
    """One-line outcome for a completed request: duration plus any failure status.

    Falls back to `wall_s` so it also renders a warmup, which has no
    `latency_s`. Errors are truncated to keep the progress line to a readable
    width; the full text is still stored in the row.
    """
    secs = cell.get("latency_s")
    if secs is None:
        secs = cell.get("wall_s")
    parts = [f"{secs:.2f}s" if secs is not None else "?"]
    if cell.get("timeout"):
        parts.append("TIMEOUT")
    elif cell.get("error"):
        parts.append(f"ERROR {cell['error'][:60]}")
    if cell.get("sleep_s"):
        parts.append(f"slept {cell['sleep_s']:.0f}s")
    return " ".join(parts)


def extract_timings(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Pull Ollama's own timing counters out of a /api/generate response.

    Ollama reports nanoseconds, and separates `load_duration` (weights into
    memory) from `eval_duration` (token generation). Keeping them apart lets us
    report cold-load cost and steady-state tokens/sec separately.
    """

    def secs(key: str) -> Optional[float]:
        v = data.get(key)
        return round(v / 1e9, 4) if isinstance(v, (int, float)) else None

    eval_count = data.get("eval_count")
    eval_s = secs("eval_duration")
    return {
        "total_s": secs("total_duration"),
        "load_s": secs("load_duration"),
        "prompt_eval_count": data.get("prompt_eval_count"),
        "prompt_eval_s": secs("prompt_eval_duration"),
        "eval_count": eval_count,
        "eval_s": eval_s,
        "gen_tps": round(eval_count / eval_s, 2) if eval_count and eval_s else None,
    }


def warm_up(base_url: str, model_id: str, args: argparse.Namespace) -> Dict[str, Any]:
    """
    One unscored request that both loads the model and measures that load.

    It is the only request that pays the weight-loading cost, so `load_s` here is
    the latency a fresh session feels before its first token.
    """
    result: Dict[str, Any] = {"ok": False, "wall_s": None, "timings": None, "error": None, "timeout": False}
    t0 = time.perf_counter()
    w0 = time.time()
    try:
        with httpx.Client(timeout=args.load_timeout) as client:
            resp = client.post(
                f"{base_url}/api/generate",
                json={
                    "model": model_id,
                    "prompt": "Reply with the single word: ready",
                    "stream": False,
                    "options": {"temperature": args.temperature, "num_ctx": args.num_ctx},
                },
            )
            resp.raise_for_status()
            data = resp.json()
        result["ok"] = True
        result["wall_s"] = round(time.time() - w0, 4)
        result["sleep_s"] = _slept_s(time.perf_counter() - t0, result["wall_s"])
        result["timings"] = extract_timings(data)
        result["load_s"] = result["timings"].get("load_s")
    except httpx.TimeoutException as e:
        result["wall_s"] = round(time.time() - w0, 4)
        result["error"] = f"timeout: {e}"
        result["timeout"] = True
    except Exception as e:
        result["wall_s"] = round(time.time() - w0, 4)
        result["error"] = str(e)
    return result


def run_cell(
    client: httpx.Client,
    base_url: str,
    model_id: str,
    interaction: str,
    fixture: Dict[str, Any],
    run_idx: int,
    args: argparse.Namespace,
) -> Dict[str, Any]:
    cell: Dict[str, Any] = {
        "fixture": fixture["name"],
        "interaction": interaction,
        "run": run_idx + 1,
    }
    t0 = time.perf_counter()
    w0 = time.time()
    try:
        resp = client.post(
            f"{base_url}/api/generate",
            json={
                "model": model_id,
                "prompt": build_prompt(interaction, fixture),
                "stream": False,
                "options": {"temperature": args.temperature, "num_ctx": args.num_ctx},
            },
        )
        resp.raise_for_status()
        data = resp.json()
        wall = round(time.time() - w0, 4)
        return {
            **cell,
            "latency_s": round(time.perf_counter() - t0, 4),
            "wall_s": wall,
            "sleep_s": _slept_s(time.perf_counter() - t0, wall),
            "timings": extract_timings(data),
            "raw": data.get("response", ""),
            "error": None,
            "timeout": False,
        }
    except httpx.TimeoutException as e:
        return {
            **cell,
            "latency_s": round(time.perf_counter() - t0, 4),
            "wall_s": round(time.time() - w0, 4),
            "sleep_s": 0.0,
            "timings": None,
            "raw": None,
            "error": f"timeout: {e}",
            "timeout": True,
        }
    except Exception as e:
        return {
            **cell,
            "latency_s": round(time.perf_counter() - t0, 4),
            "wall_s": round(time.time() - w0, 4),
            "sleep_s": 0.0,
            "timings": None,
            "raw": None,
            "error": str(e),
            "timeout": False,
        }


def _fmt_size_label(size_label: Optional[str], size_bytes: Optional[int] = None, estimated_gb: Optional[float] = None) -> str:
    """Render size as human readable bytes if available, else estimated_gb with asterisk."""
    if size_bytes:
        if size_bytes >= 1024**3:
            return f"{size_bytes / 1024**3:.1f} GB"
        elif size_bytes >= 1024**2:
            return f"{size_bytes / 1024**2:.0f} MB"
        else:
            return f"{size_bytes / 1024:.0f} KB"
    if estimated_gb:
        return f"{estimated_gb:.1f} GB*"
    if size_label:
        s = str(size_label).strip()
        m = re.fullmatch(r"([\d.]+)\s*M", s, re.IGNORECASE)
        if m:
            return f"{float(m.group(1)) / 1000:.1f}B"
        return s
    return "?"


def _size_tag(size_label: Optional[str]) -> Optional[str]:
    """Turn a declared size ("3B", "7.6B", "8.0B") into an Ollama tag token ("3b", "7.6b", "8b")."""
    if not size_label:
        return None
    s = str(size_label).strip().lower().rstrip("b").strip()
    if not s:
        return None
    # Drop a redundant trailing ".0" ("8.0" -> "8") but keep meaningful fractions ("7.6").
    if re.fullmatch(r"\d+\.0", s):
        s = s[:-2]
    return f"{s}b"


def resolve_alias(model_id: str, size_label: Optional[str]) -> str:
    """Resolve a bare `:latest` alias to its real size tag.

    Ollama treats `latest` as a plain tag alias, so `llama3.2:latest` *is*
    `llama3.2:3b`. The registry connection that would confirm this isn't
    available offline, so we distill it from the model's own metadata: the
    author-declared `general.size_label` (or the fallback parameter size). A
    model with a real tag, or one whose size is unknown, is left alone.
    """
    base, sep, tag = model_id.partition(":")
    if not sep:
        base, tag = model_id, "latest"
    if tag != "latest":
        return model_id
    token = _size_tag(size_label)
    return f"{base}:{token}" if token else model_id


def model_meta(
    model_id: str,
    info: Optional[Dict[str, Any]],
    context_len: Optional[int],
    size_bytes: Optional[int] = None,
) -> Dict[str, Any]:
    details = (info or {}).get("details", {}) or {}
    model_info = (info or {}).get("model_info", {}) or {}
    size_label = model_info.get("general.size_label") or details.get("parameter_size")
    return {
        "parameter_size": details.get("parameter_size"),
        # The author-declared label ("3B"), and the exact count when present.
        "size_label": size_label,
        "parameter_count": model_info.get("general.parameter_count"),
        "basename": model_info.get("general.basename"),
        "finetune": model_info.get("general.finetune"),
        "quantization": details.get("quantization_level"),
        "family": details.get("family"),
        "format": details.get("format"),
        "context_length": context_len,
        "capabilities": (info or {}).get("capabilities"),
        # On-disk weights size (from /api/tags), used for the footprint estimate.
        "size_bytes": size_bytes,
        # Distinct from model_id: `latest` resolved to the real size tag, for
        # display. model_id stays the canonical reference you'd pass to Ollama.
        "display_name": resolve_alias(model_id, size_label),
    }


def estimate_memory_gb(meta: Dict[str, Any]) -> Optional[float]:
    """Rough resident-memory estimate (weights + KV/runtime headroom), in GB.

    Only feeds the "will this fit a small device?" flag, so it stays deliberately
    coarse: prefer the on-disk weight size, else derive it from the parameter
    count and quantization, then add ~15% plus 1 GB of headroom.
    """
    size_bytes = meta.get("size_bytes")
    if size_bytes:
        weights_gb = size_bytes / 1024**3
    else:
        params = meta.get("parameter_count")
        if not params:
            return None
        quant = (meta.get("quantization") or "").upper() or "Q4_K_M"
        bits = QUANT_BITS.get(quant, DEFAULT_QUANT_BITS)
        weights_gb = params * bits / 8 / 1024**3
    return round(weights_gb * 1.15 + 1.0, 1)


def context_length_of(info: Optional[Dict[str, Any]]) -> Optional[int]:
    for k, v in ((info or {}).get("model_info", {}) or {}).items():
        if k.endswith(".context_length"):
            try:
                return int(v)
            except Exception:
                pass
    return None


def build_row(
    run_meta: Dict[str, Any],
    model_id: str,
    meta: Dict[str, Any],
    warmup: Dict[str, Any],
    cells: List[Dict[str, Any]],
    started_at: str,
    in_progress: bool,
    finished_at: Optional[str] = None,
) -> Dict[str, Any]:
    return {
        "schema": SCHEMA,
        "fixtures_version": FIXTURES_VERSION,
        "model_id": model_id,
        "model": meta,
        "run": run_meta,
        "warmup": warmup,
        "cells": cells,
        "started_at": started_at,
        "finished_at": finished_at,
        "in_progress": in_progress,
    }


def run_model(
    base_url: str,
    model_id: str,
    run_meta: Dict[str, Any],
    args: argparse.Namespace,
    on_cell_start: Callable[[str], None],
    on_cell_end: Callable[[str], None],
    on_flush: Optional[Callable[[Dict[str, Any]], None]] = None,
    size_bytes: Optional[int] = None,
) -> Dict[str, Any]:
    """Evaluate one model end to end and return its row.

    `on_cell_start` receives a label before a request begins and `on_cell_end`
    its outcome (duration + status) once it lands, so the caller can draw one
    progress line that fills in its own timing.

    `on_flush` receives the row-so-far after the warmup and after every request,
    so an interrupted sweep still leaves a partial (but readable) file.
    """
    started_at = datetime.now().isoformat(timespec="seconds")
    info = get_model_info(base_url, model_id, args.timeout)
    meta = model_meta(model_id, info, context_length_of(info), size_bytes)
    cells: List[Dict[str, Any]] = []

    def flush(in_progress: bool, finished_at: Optional[str] = None) -> Dict[str, Any]:
        row = build_row(run_meta, model_id, meta, warmup, cells, started_at, in_progress, finished_at)
        if on_flush:
            on_flush(row)
        return row

    on_cell_start("warming up (unscored, measures cold load)")
    warmup = warm_up(base_url, model_id, args)
    suffix = _fmt_elapsed(warmup)
    if warmup.get("load_s") is not None:
        suffix += f" (cold load {warmup['load_s']:.2f}s)"
    on_cell_end(suffix)
    flush(in_progress=True)

    # Only fixtures that actually exercise this interaction run, so count the
    # real groups rather than INTERACTIONS × FIXTURES.
    groups = [(i, f) for i in INTERACTIONS for f in FIXTURES if i in f["target_interactions"]]
    total_cells = len(groups) * args.runs
    done = 0
    with httpx.Client(timeout=args.timeout) as client:
        for interaction, fixture in groups:
            for run_idx in range(args.runs):
                done += 1
                on_cell_start(
                    f"{interaction}/{fixture['name']} run {run_idx + 1}/{args.runs} ({done}/{total_cells})"
                )
                cell = run_cell(client, base_url, model_id, interaction, fixture, run_idx, args)
                cells.append(cell)
                on_cell_end(_fmt_elapsed(cell))
                flush(in_progress=True)

    return flush(in_progress=False, finished_at=datetime.now().isoformat(timespec="seconds"))


# --------------------------------------------------------------------------- #
# Aggregation (derived from raw at read time)
# --------------------------------------------------------------------------- #

def _rate(items: List[Dict[str, Any]], field: str) -> Optional[float]:
    values = [x["eval"].get(field) for x in items]
    if all(v is None for v in values):
        return None
    return round(sum(1 for v in values if v) / len(values), 3)


def _pooled_rate(cells: List[Dict[str, Any]], field: str) -> Optional[float]:
    """Exact share of cells with `field` truthy — no per-group rounding.

    The model-level rates use this so the composite score (and therefore the
    quality band) is a deterministic function of the pooled cells, not of how
    many groups they were bucketed into.
    """
    values = [c["eval"].get(field) for c in cells]
    if all(v is None for v in values):
        return None
    return sum(1 for v in values if v) / len(values)


def _p95(values: List[float]) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(len(ordered) * 0.95))]


def _mean(values: List[float]) -> float:
    return round(statistics.mean(values), 4) if values else 0.0


def _suite_seconds(cells: List[Dict[str, Any]]) -> Optional[float]:
    """Average time to run the whole suite once, across runs.

    A "run" is one pass over every (fixture, interaction) group, so this sums a
    run's cells and averages those sums. Averaging — rather than summing — across
    runs is what keeps the figure independent of `--runs`, so a `--runs 3` sweep
    and a `--runs 5` sweep of the same model compare directly.

    Cells that errored or timed out are left out (they delivered no answer), so a
    model that hangs is only judged on what it completed — read this beside the
    `timeout×N` / `error×N` flags rather than on its own.
    """
    by_run: Dict[Any, List[float]] = {}
    for cell in cells:
        if _is_timeout(cell) or cell.get("error"):
            continue
        if cell.get("latency_s") is None:
            continue
        by_run.setdefault(cell.get("run"), []).append(cell["latency_s"])
    totals = [sum(v) for v in by_run.values() if v]
    return round(sum(totals) / len(totals), 3) if totals else None


def _median_tps(cells: List[Dict[str, Any]]) -> Optional[float]:
    tps = [c["timings"]["gen_tps"] for c in cells if c.get("timings") and c["timings"].get("gen_tps")]
    return round(statistics.median(tps), 2) if tps else None


def _composite_quality(rollup: Dict[str, Any]) -> Optional[float]:
    """Weighted 0–100 quality score; components are renormalized over what's present."""
    total = 0.0
    weight_sum = 0.0
    for field, weight in QUALITY_WEIGHTS:
        value = rollup.get(field)
        if value is None:
            continue
        total += weight * value
        weight_sum += weight
    return round(100 * total / weight_sum, 1) if weight_sum else None


def quality_band(quality: Optional[float]) -> str:
    """Map a composite score onto its band; no data is its own band."""
    if quality is None:
        return "unknown"
    for floor, label in QUALITY_BANDS:
        if quality >= floor:
            return label
    return "weak"


def speed_band(suite_s: Optional[float]) -> str:
    """Map average suite time onto its band; no data is its own band."""
    if suite_s is None:
        return "unknown"
    for ceiling, label in SPEED_BANDS:
        if suite_s < ceiling:
            return label
    return "slow"


def recommendation_tier(q_band: str, s_band: str) -> str:
    """good / ok / discouraged as a label on the quality × speed grid.

    Returns `unknown` when either axis has no data. That case can't arise from
    this tool (every row it reports carries measurements), but it is the normal
    case in the app for a model that has never been swept — the bands are shared,
    so the tier rule has to answer for it.
    """
    if "unknown" in (q_band, s_band):
        return TIER_UNKNOWN
    cell = (q_band, s_band)
    if cell in GOOD_CELLS:
        return TIER_GOOD
    if cell in OK_CELLS:
        return TIER_OK
    return TIER_DISCOURAGED


def _group_stats(
    model_id: Optional[str], fixture: Optional[str], interaction: Optional[str], items: List[Dict[str, Any]]
) -> Dict[str, Any]:
    """Rates and latency distribution over one bucket of scored cells."""
    # Timing distribution over successful requests only; an error/timeout
    # latency is capped by the timeout, not a real generation time.
    ok_items = [c for c in items if not c.get("error")] or items
    lats = [c.get("latency_s") or 0.0 for c in ok_items]
    return {
        "model_id": model_id,
        "fixture": fixture,
        "interaction": interaction,
        "reps": len(items),
        "ok_rate": _rate(items, "ok"),
        "clean_rate": _rate(items, "clean"),
        "json_ok_rate": _rate(items, "json_ok"),
        "array_strings_rate": _rate(items, "array_strings"),
        "timeouts": sum(1 for c in items if _is_timeout(c)),
        "errors": sum(1 for c in items if c.get("error")),
        "sleeps": sum(1 for c in items if c.get("sleep_s")),
        "latency_mean_s": _mean(lats),
        "latency_p50_s": round(statistics.median(lats), 4) if lats else 0.0,
        "latency_p95_s": round(_p95(lats), 4),
        "latency_std_s": round(statistics.pstdev(lats), 4) if len(lats) > 1 else 0.0,
        "latency_min_s": round(min(lats), 4) if lats else 0.0,
        "latency_max_s": round(max(lats), 4) if lats else 0.0,
        "gen_tps_median": _median_tps(ok_items),
    }


def _collect_format_notes(scored: List[Dict[str, Any]]) -> List[str]:
    """Aggregate recurring format failures into human-readable notes."""
    from collections import defaultdict

    notes: List[str] = []

    # Track patterns
    title_quotes = {"count": 0, "fixtures": set()}
    title_period = {"count": 0, "fixtures": set()}
    title_preamble = {"count": 0, "fixtures": set()}
    title_multiline = {"count": 0, "fixtures": set()}
    title_long = {"count": 0, "fixtures": set()}

    questions_json = {"count": 0, "fixtures": set()}
    questions_bad_array = {"count": 0, "fixtures": set()}

    summary_bullets = {"count": 0, "fixtures": set()}
    summary_preamble = {"count": 0, "fixtures": set()}
    summary_long = {"count": 0, "fixtures": set()}

    title_total = 0
    questions_total = 0
    summary_total = 0

    for c in scored:
        interaction = c.get("interaction")
        fixture = c.get("fixture") or "?"
        evald = c.get("eval") or {}
        err = (evald.get("error") or "").lower()

        if interaction == "title":
            title_total += 1
            if "wrapped in quotes" in err:
                title_quotes["count"] += 1
                title_quotes["fixtures"].add(fixture)
            elif "trailing period" in err:
                title_period["count"] += 1
                title_period["fixtures"].add(fixture)
            elif "preamble" in err:
                title_preamble["count"] += 1
                title_preamble["fixtures"].add(fixture)
            elif "multi-line" in err:
                title_multiline["count"] += 1
                title_multiline["fixtures"].add(fixture)
            elif ">60 chars" in err:
                title_long["count"] += 1
                title_long["fixtures"].add(fixture)
        elif interaction == "initial_questions":
            questions_total += 1
            if "json parse failed" in err:
                questions_json["count"] += 1
                questions_json["fixtures"].add(fixture)
            elif "not array of non-empty strings" in err or "array of non-empty strings" in err:
                questions_bad_array["count"] += 1
                questions_bad_array["fixtures"].add(fixture)
        elif interaction == "summarize":
            summary_total += 1
            if "bullets/headings" in err:
                summary_bullets["count"] += 1
                summary_bullets["fixtures"].add(fixture)
            elif "preamble" in err:
                summary_preamble["count"] += 1
                summary_preamble["fixtures"].add(fixture)
            elif ">150 words" in err:
                summary_long["count"] += 1
                summary_long["fixtures"].add(fixture)

    def _fmt_fixtures(s):
        items = sorted(list(s))
        if not items:
            return ""
        if len(items) == 1:
            return items[0]
        return ", ".join(items)

    # Only surface if it's notable (>= 25% of that interaction or affects >= 2 fixtures)
    if title_total > 0 and title_quotes["count"] > 0:
        if title_quotes["count"] / title_total >= 0.25 or len(title_quotes["fixtures"]) >= 2:
            fx = _fmt_fixtures(title_quotes["fixtures"])
            notes.append(f"Titles wrapped in quotes ({fx})" if fx else "Titles wrapped in quotes")
    if title_total > 0 and title_period["count"] > 0:
        if title_period["count"] / title_total >= 0.25 or len(title_period["fixtures"]) >= 2:
            fx = _fmt_fixtures(title_period["fixtures"])
            notes.append(f"Titles with trailing periods ({fx})" if fx else "Titles with trailing periods")
    if title_total > 0 and title_preamble["count"] > 0:
        if title_preamble["count"] / title_total >= 0.25 or len(title_preamble["fixtures"]) >= 2:
            fx = _fmt_fixtures(title_preamble["fixtures"])
            notes.append(f"Titles include preamble/explanation ({fx})" if fx else "Titles include preamble/explanation")
    if title_total > 0 and title_multiline["count"] > 0:
        if title_multiline["count"] / title_total >= 0.25 or len(title_multiline["fixtures"]) >= 2:
            fx = _fmt_fixtures(title_multiline["fixtures"])
            notes.append(f"Multi-line titles ({fx})" if fx else "Multi-line titles")
    if title_total > 0 and title_long["count"] > 0:
        if title_long["count"] / title_total >= 0.25 or len(title_long["fixtures"]) >= 2:
            fx = _fmt_fixtures(title_long["fixtures"])
            notes.append(f"Titles over 60 chars ({fx})" if fx else "Titles over 60 chars")

    if questions_total > 0 and questions_json["count"] > 0:
        if questions_json["count"] / questions_total >= 0.25 or len(questions_json["fixtures"]) >= 2:
            fx = _fmt_fixtures(questions_json["fixtures"])
            notes.append(f"Questions failed to parse as JSON arrays ({fx})" if fx else "Questions failed to parse as JSON arrays")
    if questions_total > 0 and questions_bad_array["count"] > 0:
        if questions_bad_array["count"] / questions_total >= 0.25 or len(questions_bad_array["fixtures"]) >= 2:
            fx = _fmt_fixtures(questions_bad_array["fixtures"])
            notes.append(f"Questions not valid arrays of non-empty strings ({fx})" if fx else "Questions not valid arrays of non-empty strings")

    if summary_total > 0 and summary_bullets["count"] > 0:
        if summary_bullets["count"] / summary_total >= 0.25 or len(summary_bullets["fixtures"]) >= 2:
            fx = _fmt_fixtures(summary_bullets["fixtures"])
            notes.append(f"Summaries include bullets/headings ({fx})" if fx else "Summaries include bullets/headings")
    if summary_total > 0 and summary_preamble["count"] > 0:
        if summary_preamble["count"] / summary_total >= 0.25 or len(summary_preamble["fixtures"]) >= 2:
            fx = _fmt_fixtures(summary_preamble["fixtures"])
            notes.append(f"Summaries include preamble ({fx})" if fx else "Summaries include preamble")
    if summary_total > 0 and summary_long["count"] > 0:
        if summary_long["count"] / summary_total >= 0.25 or len(summary_long["fixtures"]) >= 2:
            fx = _fmt_fixtures(summary_long["fixtures"])
            notes.append(f"Summaries over 150 words ({fx})" if fx else "Summaries over 150 words")

    return notes


def analyze_row(row: Dict[str, Any]) -> Dict[str, Any]:
    """Score a row's cells and aggregate them per (fixture, interaction).

    Every scored cell keeps the batch it came from (`run_id`), so a merged row —
    several batches pooled into one model line — can still be broken down per run
    in the detail table without re-deriving anything.
    """
    row_run_id = (row.get("run") or {}).get("id")
    scored: List[Dict[str, Any]] = [
        {
            **cell,
            "run_id": cell.get("run_id") or row_run_id,
            "eval": score_result(cell.get("interaction", ""), cell.get("raw") or ""),
        }
        for cell in row.get("cells", [])
    ]

    by_key: Dict[Tuple[str, str], List[Dict[str, Any]]] = {}
    for cell in scored:
        by_key.setdefault((cell.get("fixture"), cell.get("interaction")), []).append(cell)

    aggs: List[Dict[str, Any]] = [
        _group_stats(row.get("model_id"), fixture, interaction, items)
        for (fixture, interaction), items in by_key.items()
    ]

    questions = [c for c in scored if c.get("interaction") == "initial_questions"]
    tps = [a["gen_tps_median"] for a in aggs if a.get("gen_tps_median")]
    model = row.get("model") or {}
    warmup = row.get("warmup") or {}
    estimated_gb = estimate_memory_gb(model)
    size_bytes = model.get("size_bytes")
    rollup = {
        "model_id": row.get("model_id"),
        "run_id": (row.get("run") or {}).get("id"),
        "display_name": model.get("display_name") or row.get("model_id"),
        "size_label": _fmt_size_label(model.get("size_label") or model.get("parameter_size"), size_bytes, estimated_gb),
        "size_bytes": size_bytes,
        "parameter_size": model.get("parameter_size"),
        "quantization": model.get("quantization"),
        "estimated_gb": estimated_gb,
        "started_at": row.get("started_at"),
        "in_progress": row.get("in_progress"),
        "cells": len(row.get("cells", [])),
        "timeouts": sum(1 for c in row.get("cells", []) if _is_timeout(c)) + (1 if warmup.get("timeout") else 0),
        "errors": sum(1 for c in row.get("cells", []) if c.get("error")) + (1 if warmup.get("error") else 0),
        "sleeps": sum(1 for c in row.get("cells", []) if c.get("sleep_s")) + (1 if warmup.get("sleep_s") else 0),
        # Model-level rates pool every included cell directly rather than
        # averaging the per-group rates: those are rounded for display, and
        # averaging them would let the group count (and so the number of batches
        # pooled) nudge the score that picks the quality band.
        "ok_rate": _pooled_rate(scored, "ok"),
        "clean_rate": _pooled_rate(scored, "clean"),
        "json_ok_rate": _pooled_rate(questions, "json_ok") if questions else None,
        "array_strings_rate": _pooled_rate(questions, "array_strings") if questions else None,
        "latency_mean_s": _mean([a["latency_mean_s"] for a in aggs]),
        "latency_p95_s": round(max((a["latency_p95_s"] for a in aggs), default=0.0), 4),
        "suite_s": _suite_seconds(row.get("cells", [])),
        "gen_tps_median": round(statistics.median(tps), 2) if tps else None,
        "load_s": warmup.get("load_s"),
    }
    rollup["quality"] = _composite_quality(rollup)
    rollup["quality_band"] = quality_band(rollup["quality"])
    rollup["speed_band"] = speed_band(rollup.get("suite_s"))
    rollup["tier"] = recommendation_tier(rollup["quality_band"], rollup["speed_band"])
    rollup["included_runs"] = list(row.get("run_ids") or ([rollup["run_id"]] if rollup["run_id"] else []))
    rollup["notes"] = _collect_format_notes(scored)
    return {"row": row, "rollup": rollup, "cells": scored}


# --------------------------------------------------------------------------- #
# Row collection / selection
# --------------------------------------------------------------------------- #

def collect_rows(paths: List[str]) -> Tuple[List[Dict[str, Any]], Dict[str, int]]:
    stats = {"files": 0, "unreadable": 0}
    rows: List[Dict[str, Any]] = []
    seen: set = set()
    for raw in paths:
        path = Path(raw)
        candidates: List[Path] = []
        if path.is_dir():
            candidates = sorted(path.glob("*.json"))
        elif path.is_file():
            candidates = [path]
        else:
            print(f"warning: {raw} not found", file=sys.stderr)
        for c in candidates:
            rp = c.resolve()
            if rp in seen:
                continue
            seen.add(rp)
            stats["files"] += 1
            try:
                data = json.loads(c.read_text())
            except Exception as e:
                stats["unreadable"] += 1
                print(f"warning: skipped {c.name}: {e}", file=sys.stderr)
                continue
            if isinstance(data, dict) and "model_id" in data and "cells" in data:
                rows.append(data)
            else:
                stats["unreadable"] += 1
    return rows, stats


def _name_matches(name: str, patterns: List[str]) -> bool:
    """Match an installed model name against user patterns.

    Accepts the full id (`qwq:latest`) or the bare family (`qwq`), so you don't
    have to remember which tag happens to be installed.
    """
    lowered = name.lower()
    family = lowered.split(":", 1)[0]
    return any(pattern.lower() in {lowered, family} for pattern in patterns)


def _matches_exclusion(row: Dict[str, Any], names: List[str]) -> bool:
    """True if `row` is one of the named models.

    Accepts the canonical id (`qwq:latest`), the resolved display name
    (`qwq:32b`), or the bare family (`qwq`), so you don't have to remember how a
    model's id happens to be spelled.
    """
    model = row.get("model") or {}
    candidates = {
        str(row.get("model_id") or "").lower(),
        str(model.get("display_name") or "").lower(),
        str(model.get("basename") or "").lower(),
    }
    return any(name.lower() in candidates for name in names)


def filter_rows(
    rows: List[Dict[str, Any]],
    min_schema: int,
    allow_fixtures: bool,
    run_ids: Optional[List[str]] = None,
    exclude_models: Optional[List[str]] = None,
) -> Tuple[List[Dict[str, Any]], Dict[str, int]]:
    kept: List[Dict[str, Any]] = []
    stats = {"old_schema": 0, "fixtures_mismatch": 0, "other_run": 0, "excluded": 0}
    for row in rows:
        if row.get("schema", 0) < min_schema:
            stats["old_schema"] += 1
            continue
        if not allow_fixtures and row.get("fixtures_version", 0) != FIXTURES_VERSION:
            stats["fixtures_mismatch"] += 1
            continue
        if run_ids and (row.get("run") or {}).get("id") not in run_ids:
            stats["other_run"] += 1
            continue
        if exclude_models and _matches_exclusion(row, exclude_models):
            stats["excluded"] += 1
            continue
        kept.append(row)
    return kept, stats


def select_rows(rows: List[Dict[str, Any]], per_model: str) -> List[Dict[str, Any]]:
    if per_model == "all":
        return sorted(rows, key=lambda r: (r.get("model_id") or "", r.get("started_at") or ""))
    by_model: Dict[str, List[Dict[str, Any]]] = {}
    for r in rows:
        by_model.setdefault(r.get("model_id") or "?", []).append(r)
    out: List[Dict[str, Any]] = []
    for group in by_model.values():
        # Prefer finished rows; fall back to in-progress ones if that's all we have.
        usable = [r for r in group if not r.get("in_progress")] or group
        if per_model == "max-cells":
            pick = max(usable, key=lambda r: (len(r.get("cells", [])), r.get("started_at") or ""))
        else:  # latest
            pick = max(usable, key=lambda r: r.get("started_at") or "")
        out.append(pick)
    return out


def group_by_model(rows: List[Dict[str, Any]]) -> List[List[Dict[str, Any]]]:
    """Bucket selected rows by model, each bucket in chronological order."""
    by_model: Dict[str, List[Dict[str, Any]]] = {}
    for r in rows:
        by_model.setdefault(r.get("model_id") or "?", []).append(r)
    return [
        sorted(group, key=lambda r: r.get("started_at") or "")
        for _, group in sorted(by_model.items())
    ]


def merge_rows(rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Pool every included row of one model into a single synthetic row.

    The summary is one line per model, so a model swept in several batches has to
    collapse into one. Pooling the *cells* — rather than averaging the per-row
    summaries — keeps the arithmetic honest: `ok_rate` becomes total-ok over
    total-cells across every included run, and `suite_s` stays the mean duration
    of one pass over the suite. Each cell's `run` is re-keyed with its batch so
    run indices from different sweeps can't collide (batch A's run 0 and batch
    B's run 0 are different passes).

    A single row passes through untouched, so `--per-model latest` behaves exactly
    as before.
    """
    if len(rows) == 1:
        return rows[0]
    # Size is the one model field that varies between batches: `size_bytes` from
    # /api/tags is measured on disk, while the fallback (parameter count ×
    # quantization bits) under-reports. Keep the largest estimate so the memory
    # figure and the `>NGB` fit flag stay conservative.
    base = dict(max(rows, key=lambda r: estimate_memory_gb(r.get("model") or {}) or 0.0))
    cells: List[Dict[str, Any]] = []
    for src in rows:
        run_id = (src.get("run") or {}).get("id") or "?"
        for cell in src.get("cells", []):
            cells.append({**cell, "run": f"{run_id}#{cell.get('run')}", "run_id": run_id})
    base["cells"] = cells
    base["run_ids"] = [(src.get("run") or {}).get("id") for src in rows]
    base["in_progress"] = any(src.get("in_progress") for src in rows)
    base["started_at"] = min((src.get("started_at") or "" for src in rows), default="")
    base["finished_at"] = max((src.get("finished_at") or "" for src in rows), default=None)
    base["warmup"] = _merge_warmups([src.get("warmup") or {} for src in rows])
    return base


def _merge_warmups(warmups: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Collapse several cold-load measurements into one.

    Cold load is the only warmup figure that reaches the report, and it is the
    noisiest (it depends on whether the weights were already resident), so pool it
    as a mean and keep `ok` only if every contributing load succeeded.
    """
    loads = [w["load_s"] for w in warmups if w.get("load_s") is not None]
    walls = [w["wall_s"] for w in warmups if w.get("wall_s") is not None]
    return {
        "ok": all(w.get("ok") for w in warmups),
        "load_s": round(statistics.mean(loads), 4) if loads else None,
        "wall_s": round(statistics.mean(walls), 4) if walls else None,
        "timeout": any(w.get("timeout") for w in warmups),
        "error": None,
        "sleep_s": sum(w.get("sleep_s") or 0.0 for w in warmups) or None,
    }


def analyze_models(rows: List[Dict[str, Any]], per_model: str) -> List[Dict[str, Any]]:
    """Select rows, pool each model's, and analyze — the one path both renderers use.

    This is where `--per-model all` stops meaning "one row per run" and starts
    meaning "one row per model, every run pooled". Each entry keeps the original
    rows as `sources` so the header can still report which batches, parameters and
    hardware went in.
    """
    return [
        {**analyze_row(merge_rows(group)), "sources": group}
        for group in group_by_model(select_rows(rows, per_model))
    ]


# --------------------------------------------------------------------------- #
# Rendering
# --------------------------------------------------------------------------- #

def _fmt(v: Optional[float], nd: int = 2) -> str:
    return "-" if v is None else f"{v:.{nd}f}"


def _common(rows: List[Dict[str, Any]], getter: Callable[[Dict[str, Any]], Any]) -> Any:
    vals = {json.dumps(getter(r), sort_keys=True) for r in rows}
    return json.loads(vals.pop()) if len(vals) == 1 else "mixed"


def _sort_key(rollup: Dict[str, Any]) -> Tuple[float, float, float]:
    """Sort by tier (good > ok > discouraged), then quality desc, then speed asc."""
    tier_order = {TIER_GOOD: 0, TIER_OK: 1, TIER_DISCOURAGED: 2, TIER_UNKNOWN: 3}
    tier = rollup.get("tier") or TIER_UNKNOWN
    return (
        tier_order.get(tier, 99),
        -(rollup.get("quality") or 0.0),
        rollup.get("suite_s") if rollup.get("suite_s") is not None else float("inf"),
    )


def _flags(rollup: Dict[str, Any], device_gb: float) -> List[str]:
    flags: List[str] = []
    if rollup.get("in_progress"):
        flags.append("in-progress")
    timeouts = rollup.get("timeouts") or 0
    if timeouts:
        flags.append(f"timeout×{timeouts}")
    # `errors` counts every failed cell, timeouts included; report only the rest.
    other_errors = (rollup.get("errors") or 0) - timeouts
    if other_errors > 0:
        flags.append(f"error×{other_errors}")
    if rollup.get("sleeps"):
        flags.append(f"slept×{rollup['sleeps']}")
    est = rollup.get("estimated_gb")
    if device_gb and est and est > device_gb:
        flags.append(f">{device_gb:g}GB")
    return flags


def _header_lines(analyzed: List[Dict[str, Any]], stats: Dict[str, int], per_model: str, device_gb: float) -> List[str]:
    # Pooled entries carry their contributing rows in `sources`; run ids, params
    # and hardware have to come from those, not from the merged synthetic row.
    rows = [r for a in analyzed for r in (a.get("sources") or [a["row"]])]
    dropped = "".join(
        f", {stats[key]} {label}" for key, label in (("other_run", "other run"), ("excluded", "excluded"))
        if stats.get(key)
    )
    out = [
        f"rows: {stats.get('files', 0)} file(s) read, {stats.get('used', len(rows))} usable, "
        f"{stats.get('old_schema', 0)} too old, {stats.get('fixtures_mismatch', 0)} fixture mismatch, "
        f"{stats.get('unreadable', 0)} unreadable{dropped}",
    ]
    if len(rows) > len(analyzed):
        out.append(f"per-model: {per_model} — {len(rows)} row(s) pooled into {len(analyzed)} model(s)")
    else:
        out.append(f"per-model: {per_model}")
    run_ids = sorted({(r.get("run") or {}).get("id") for r in rows if (r.get("run") or {}).get("id")})
    if len(run_ids) == 1:
        out.append(f"run-id: {run_ids[0]}")
    elif run_ids:
        out.append(f"run-ids: {', '.join(run_ids)}")
    if rows:
        out.append(
            f"params: base_url={_common(rows, lambda r: (r.get('run') or {}).get('base_url'))}, "
            f"runs={_common(rows, lambda r: (r.get('run') or {}).get('runs'))}, "
            f"num_ctx={_common(rows, lambda r: (r.get('run') or {}).get('num_ctx'))}, "
            f"temp={_common(rows, lambda r: (r.get('run') or {}).get('temperature'))}"
        )
        hw = _common(rows, lambda r: (r.get('run') or {}).get('hardware'))
        out.append(f"hardware: {json.dumps(hw)}")
    if device_gb:
        out.append(f"device budget: {device_gb:g}GB (models above it are flagged, never hidden)")
    return out


def render_console(
    analyzed: List[Dict[str, Any]],
    stats: Dict[str, int],
    per_model: str,
    device_gb: float,
) -> str:
    """Compact tab-separated view for the terminal: name, size, quality, tier."""
    out: List[str] = [f"# Ollama evals — {_count_label(analyzed)}"]
    out += ["# " + line for line in _header_lines(analyzed, stats, per_model, device_gb)]
    out.append("")
    out.append("model\tsize\tquality\ttier\tsuite_s\tnotes")
    rollups = sorted((a["rollup"] for a in analyzed), key=_sort_key)
    for r in rollups:
        notes_list = list(r.get("notes") or [])
        notes_list.extend(_flags(r, device_gb))
        notes_str = "; ".join(notes_list)
        out.append(
            "\t".join(
                [
                    r.get("display_name") or r["model_id"],
                    str(r.get("size_label") or "?"),
                    _fmt(r.get("quality"), 1),
                    str(r.get("tier") or ""),
                    _fmt(r.get("suite_s")),
                    notes_str,
                ]
            )
        )
    return "\n".join(out)


FIELD_GUIDE = """\
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
- `disksize` — Size on disk, or estimated size (shown with an asterisk).
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
sweep."""


def _count_label(analyzed: List[Dict[str, Any]]) -> str:
    """Title counter: one entry per model, however many rows were pooled into it."""
    models = len(analyzed)
    rows = sum(len(a.get("sources") or [a["row"]]) for a in analyzed)
    if rows != models:
        return f"{models} model(s) from {rows} row(s)"
    return f"{models} model(s)"


def _quality_headers() -> List[str]:
    """Band labels for the grid's rows, with the cut that opens each one."""
    labels = [f"{label} (≥{floor:g})" for floor, label in QUALITY_BANDS]
    return labels + ["weak", "unknown"]


def _speed_headers() -> List[str]:
    """Band labels for the grid's columns, with the cut that opens each one."""
    labels = [f"{label} (<{ceiling:g}s)" for ceiling, label in SPEED_BANDS]
    return labels + [f"slow (≥{SPEED_BANDS[-1][0]:g}s)", "unknown"]


def _render_grid(rollups: List[Dict[str, Any]]) -> List[str]:
    """Quality × speed grid, each cell listing its models (none are dropped)."""
    # Drop unknown rows/columns as they're always empty
    q_bands = ["strong", "adequate", "weak"]
    s_bands = ["fast", "fine", "slow"]
    cells: Dict[Tuple[str, str], List[Dict[str, Any]]] = {}
    for r in rollups:
        if r["quality_band"] == "unknown" or r["speed_band"] == "unknown":
            continue
        cells.setdefault((r["quality_band"], r["speed_band"]), []).append(r)
    out = [
        "| quality \\ speed | fast (<10s) | fine (<35s) | slow (≥35s) |",
        "|---|---|---|---|",
    ]
    for q_band in q_bands:
        row = [f"**{q_band}**" if q_band == "strong" else q_band]
        for s_band in s_bands:
            members = sorted(cells.get((q_band, s_band), []), key=_sort_key)
            names = list(dict.fromkeys(m.get("display_name") or m["model_id"] for m in members))
            row.append(", ".join(names))
        out.append("| " + " | ".join(row) + " |")
    return out


def _detail_groups(entry: Dict[str, Any], per_run: bool) -> List[Dict[str, Any]]:
    """Bucket an entry's scored cells for the per-interaction table.

    With one included run the bucket is (fixture, interaction), the same bucket the
    per-group rates in a row file use. With several, the bucket is (run, fixture,
    interaction) so every pooled batch gets its own rows inside the one table
    rather than a table of its own. Insertion order is preserved, and merged rows
    are built batch-major, so runs come out in the order they were swept.
    """
    by_key: Dict[Tuple[Any, ...], List[Dict[str, Any]]] = {}
    for cell in entry["cells"]:
        if per_run:
            key: Tuple[Any, ...] = (cell.get("run_id"), cell.get("fixture"), cell.get("interaction"))
        else:
            key = (cell.get("fixture"), cell.get("interaction"))
        by_key.setdefault(key, []).append(cell)

    groups: List[Dict[str, Any]] = []
    for key, items in by_key.items():
        run_id, fixture, interaction = key if per_run else (None, *key)
        group = _group_stats(entry["rollup"].get("model_id"), fixture, interaction, items)
        group["run_id"] = run_id
        groups.append(group)
    return groups


def render_markdown(
    analyzed: List[Dict[str, Any]],
    stats: Dict[str, int],
    per_model: str,
    device_gb: float,
) -> str:
    out: List[str] = [f"# Ollama evals — {_count_label(analyzed)}\n"]
    out += [f"- {line}" for line in _header_lines(analyzed, stats, per_model, device_gb)]
    out.append("")

    # One row per model: sorted by tier then suite speed.
    rollups = sorted(((a["rollup"], a) for a in analyzed), key=lambda pair: _sort_key(pair[0]))

    out.append("## Per-model (sorted by tier, then quality, then suite speed)\n")
    columns = [
        "model", "disksize", "quality", "tier", "suite_s", "ok", "clean", "json", "notes",
    ]
    out.append("| " + " | ".join(columns) + " |")
    out.append("|" + "---|" * len(columns))
    for r, _ in rollups:
        notes_list = list(r.get("notes") or [])
        notes_list.extend(_flags(r, device_gb))
        notes_str = "<br>".join(notes_list)
        cells = [
            r.get("display_name") or r["model_id"],
            r.get("size_label") or "?",
            _fmt(r.get("quality"), 1),
            r.get("tier"),
            _fmt(r.get("suite_s")),
            f"{r['ok_rate']:.2f}",
            f"{r['clean_rate']:.2f}",
            _fmt(r["json_ok_rate"]),
            notes_str,
        ]
        out.append("| " + " | ".join(cells) + " |")
    out.append("")

    out.append("## Recommendation grid\n")
    out.append(
        "Two independent axes — **quality** (composite score) and **speed** (average\n"
        "suite time) — so a flawless-but-slow model stays distinguishable from a\n"
        "fast-but-flawed one. Tiers are a label on the grid, not a filter:\n"
        "**good** = strong × fast, **ok** = strong × fine or adequate × fast,\n"
        "everything else is **discouraged**. No model is hidden by its cell.\n"
    )
    out += _render_grid([r for r, _ in rollups])
    out.append("")

    out.append("## Per-interaction detail (one table per model, same order as above)\n")
    for r, a in rollups:
        heading = r.get("display_name") or r["model_id"]
        # A model pooled from several batches gets a `run` column instead of one
        # table per batch, so its runs sit side by side.
        per_run = len({c.get("run_id") for c in a["cells"] if c.get("run_id")}) > 1
        # Add model summary if we have details
        model_details = a["row"].get("model") or {}
        summary_parts = []
        if model_details.get("size_bytes"):
            summary_parts.append(f"Size: {model_details['size_bytes']/1024**3:.2f} GB")
        elif r.get("estimated_gb"):
            summary_parts.append(f"Est. size: {r['estimated_gb']:.1f} GB")
        if model_details.get("quantization"):
            summary_parts.append(f"Quant: {model_details['quantization']}")
        if model_details.get("parameter_size"):
            summary_parts.append(f"Params: {model_details['parameter_size']}")
        if summary_parts:
            out.append(f"### {heading} ({', '.join(summary_parts)})\n")
        else:
            out.append(f"### {heading}\n")
        columns = (["run"] if per_run else []) + [
            "fixture", "interaction", "reps", "ok", "clean", "json", "arr",
            "timeouts", "mean", "p50", "p95", "std", "min", "max", "tps",
        ]
        out.append("| " + " | ".join(columns) + " |")
        out.append("|" + "---|" * len(columns))
        for g in _detail_groups(a, per_run):
            cells = ([g["run_id"] or "?"] if per_run else []) + [
                g["fixture"],
                g["interaction"],
                str(g["reps"]),
                _fmt(g["ok_rate"]),
                _fmt(g["clean_rate"]),
                _fmt(g["json_ok_rate"]),
                _fmt(g["array_strings_rate"]),
                str(g["timeouts"]),
                f"{g['latency_mean_s']:.2f}",
                f"{g['latency_p50_s']:.2f}",
                f"{g['latency_p95_s']:.2f}",
                f"{g['latency_std_s']:.2f}",
                f"{g['latency_min_s']:.2f}",
                f"{g['latency_max_s']:.2f}",
                _fmt(g.get("gen_tps_median"), 1),
            ]
            out.append("| " + " | ".join(cells) + " |")
        out.append("")

    out.append(FIELD_GUIDE)
    out.append("")
    return "\n".join(out)


def _quality_band_from_score(quality: Optional[float]) -> str:
    if quality is None:
        return "Unknown"
    if quality >= 98.0:
        return "Strong"
    if quality >= 85.0:
        return "Adequate"
    return "Weak"


def _speed_band_from_suite(suite_s: Optional[float]) -> str:
    if suite_s is None:
        return "Unknown"
    if suite_s < 10.0:
        return "Fast"
    if suite_s < 35.0:
        return "Fine"
    return "Slow"


def emit_curated_snippet(analyzed: List[Dict[str, Any]], fmt: str) -> str:
    """Emit source-ready data for ModelProfiles from evaluated rows."""
    items = []
    for a in analyzed:
        r = a["rollup"]
        items.append(
            {
                "modelIdPattern": r["model_id"],
                "displayName": r.get("display_name") or r["model_id"],
                "qualityScore": round(r.get("quality"), 1) if r.get("quality") is not None else None,
                "qualityBand": _quality_band_from_score(r.get("quality")),
                "speedBand": r.get("speed_band") or _speed_band_from_suite(r.get("suite_s")),
                "suiteSeconds": round(r.get("suite_s"), 2) if r.get("suite_s") is not None else None,
                "okRate": round(r.get("ok_rate"), 3) if r.get("ok_rate") is not None else None,
                "cleanRate": round(r.get("clean_rate"), 3) if r.get("clean_rate") is not None else None,
                "jsonOkRate": round(r.get("json_ok_rate"), 3) if r.get("json_ok_rate") is not None else None,
                "notes": r.get("notes") or [],
            }
        )

    if fmt == "json":
        return json.dumps({"measuredSeeds": items}, indent=2) + "\n"

    # Kotlin snippet
    lines = ["  /** Measured seeds from eval runs (paste into ModelProfiles.measuredSeeds). */"]
    lines.append("  val measuredSeeds = listOf(")
    for item in items:
        mid = item["modelIdPattern"]
        qband = item["qualityBand"]
        sband = item["speedBand"]
        suite = item["suiteSeconds"]
        ok = item["okRate"]
        clean = item["cleanRate"]
        json_ok = item["jsonOkRate"]
        notes = item["notes"]
        lines.append(f"    ModelMeasurement(")
        lines.append(f'      modelIdPattern = "{mid}",')
        qscore = item["qualityScore"]
        if qscore is not None:
            lines.append(f"      qualityScore = {qscore},")
        lines.append(f"      qualityBand = ModelQuality.{qband},")
        lines.append(f"      speedBand = ModelSpeed.{sband},")
        if suite is not None:
            lines.append(f"      suiteSeconds = {suite},")
        if ok is not None:
            lines.append(f"      okRate = {ok},")
        if clean is not None:
            lines.append(f"      cleanRate = {clean},")
        if json_ok is not None:
            lines.append(f"      jsonOkRate = {json_ok},")
        if notes:
            notes_str = ", ".join(f'"{n}"' for n in notes)
            lines.append(f"      notes = listOf({notes_str}),")
        lines.append("    ),")
    lines.append("  )")
    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------- #
# Commands
# --------------------------------------------------------------------------- #

def _row_is_incomplete(path: Path) -> bool:
    """True if a saved row was left mid-run (so a resume should redo it)."""
    try:
        return bool(json.loads(path.read_text()).get("in_progress"))
    except Exception:
        return True


def main_run(argv: Optional[List[str]] = None) -> int:
    args = parse_run_args(argv)
    rows_dir = Path(args.rows_dir).expanduser()
    rows_dir.mkdir(parents=True, exist_ok=True)
    run_id = args.run_id or datetime.now().strftime("%Y%m%d-%H%M%S")
    run_meta = {
        "id": run_id,
        "started_at": datetime.now().isoformat(timespec="seconds"),
        "base_url": args.base_url,
        "runs": args.runs,
        "num_ctx": args.num_ctx,
        "temperature": args.temperature,
        "load_timeout": args.load_timeout,
        "script": Path(__file__).name,
        "hardware": hardware_info(),
    }

    installed = get_installed_models(args.base_url, args.timeout)
    selected = [
        m.get("name") or m.get("model") or ""
        for m in installed
        if not args.models or (m.get("name") or m.get("model")) in args.models
    ]
    selected = [s for s in selected if s]
    if args.exclude:
        selected = [m for m in selected if not _name_matches(m, args.exclude)]
    if not selected:
        print("No installed models matched.", file=sys.stderr)
        return 1
    size_by_name = {
        (m.get("name") or m.get("model")): m.get("size") for m in installed if (m.get("name") or m.get("model"))
    }

    print(f"Found {len(installed)} installed, testing {len(selected)}", flush=True)
    print(f"Rows folder: {rows_dir}  (run id: {run_id})", flush=True)

    # On a terminal each step draws one line that fills in its own duration
    # ("  title/x run 1/3 (1/9) ... 0.51s"). Redirected to a file that would
    # concatenate the whole sweep onto one line, so logs keep one line per step
    # and print the duration on a following indented line instead.
    progressive = sys.stdout.isatty()

    def on_cell_start(label: str) -> None:
        if progressive:
            print(f"  {label} ...", end="", flush=True)
        else:
            print(f"  {label}", flush=True)

    def on_cell_end(outcome: str) -> None:
        if progressive:
            print(f" {outcome}", flush=True)
        else:
            print(f"      {outcome}", flush=True)

    def _close_progress_line(signum: int, frame: Any) -> None:
        # Ctrl-C mid-request would otherwise leave a dangling, un-terminated line.
        print(flush=True)
        raise KeyboardInterrupt

    if progressive:
        signal.signal(signal.SIGINT, _close_progress_line)

    written: List[Path] = []
    for idx, model_id in enumerate(selected, 1):
        path = rows_dir / f"{slugify(model_id)}-{run_id}.json"
        if path.exists() and not args.force and not _row_is_incomplete(path):
            print(f"[{idx}/{len(selected)}] {model_id}: row exists, skipping ({path.name})", flush=True)
            written.append(path)
            continue
        if path.exists():
            print(f"[{idx}/{len(selected)}] {model_id}: re-running incomplete row ({path.name})", flush=True)
        else:
            print(f"[{idx}/{len(selected)}] Testing model: {model_id}", flush=True)

        def flush_row(row: Dict[str, Any], path: Path = path) -> None:
            path.write_text(json.dumps(row, indent=2) + "\n")

        row = run_model(
            args.base_url,
            model_id,
            run_meta,
            args,
            on_cell_start=on_cell_start,
            on_cell_end=on_cell_end,
            on_flush=flush_row,
            size_bytes=size_by_name.get(model_id),
        )
        flush_row(row)
        written.append(path)
        if args.stop_between_models:
            on_cell_start(f"unloading {model_id} to free memory")
            unload_started = time.perf_counter()
            stop_ollama(args.base_url, args.timeout, model_id)
            on_cell_end(f"{time.perf_counter() - unload_started:.2f}s")

    # Show the result of this run immediately, using the same query path.
    rows, stats = collect_rows([str(p) for p in written])
    kept, fstats = filter_rows(rows, SCHEMA, allow_fixtures=False)
    stats["used"] = len(kept)
    stats.update(fstats)
    print("\n" + render_console(analyze_models(kept, "all"), stats, "all", args.device_memory_gb), flush=True)
    print(f"\nFull report:  python {Path(__file__).name} report {rows_dir} --full", flush=True)
    return 0


def main_report(argv: Optional[List[str]] = None) -> int:
    args = parse_report_args(argv)
    rows, stats = collect_rows(args.paths)
    if not rows:
        print("No row files found.", file=sys.stderr)
        return 1
    kept, fstats = filter_rows(
        rows,
        args.min_schema,
        args.allow_fixtures,
        run_ids=args.run_ids,
        exclude_models=args.exclude_models,
    )
    stats["used"] = len(kept)
    stats.update(fstats)
    if not kept:
        print(
            f"No usable rows (read {stats['files']}, {stats['old_schema']} too old, "
            f"{stats['fixtures_mismatch']} fixture mismatch).",
            file=sys.stderr,
        )
        return 1

    analyzed = analyze_models(kept, args.per_model)

    if args.emit:
        snippet = emit_curated_snippet(analyzed, args.emit)
        if args.emit_out:
            out_path = Path(args.emit_out).expanduser()
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_text(snippet)
            print(f"Wrote {out_path}", file=sys.stderr)
        else:
            print(snippet, end="")
        return 0

    if args.full:
        print(render_markdown(analyzed, stats, args.per_model, args.device_memory_gb))
    else:
        print(render_console(analyzed, stats, args.per_model, args.device_memory_gb))
    if args.out:
        out_path = Path(args.out).expanduser()
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(render_markdown(analyzed, stats, args.per_model, args.device_memory_gb) + "\n")
        print(f"\nWrote {out_path}", file=sys.stderr)
    return 0


def main() -> int:
    argv = sys.argv[1:]
    if argv and argv[0] == "report":
        return main_report(argv[1:])
    if argv and argv[0] == "run":
        argv = argv[1:]
    return main_run(argv)


if __name__ == "__main__":
    sys.exit(main())
