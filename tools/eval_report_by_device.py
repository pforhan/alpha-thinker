#!/usr/bin/env python3
"""
Write a per-device model recommendation report from saved evaluation rows.

`eval_ollama_models.py` answers "how does this model compare across everything I
measured". This script answers the other question: "on *this* phone or laptop,
which model should I install?" Rows are grouped by hardware and OS first, then
by model within a device, because a speed band measured on a laptop says nothing
about the same model on a phone. Pooling the two would let a fast machine
recommend a model that is unusable on the device the user is holding.

It reuses the harness's scoring and aggregation, so a row recorded by the app and
a row recorded by `eval_ollama_models.py` are read the same way.

Usage:

    # every row under docs/model-evals, markdown to stdout
    python tools/eval_report_by_device.py

    # write the committed report, with per-interaction detail
    python tools/eval_report_by_device.py --details --out docs/model-evals/by-device.md

    # just the rows shared from one phone
    python tools/eval_report_by_device.py --device pixel

    # machine-readable companion
    python tools/eval_report_by_device.py --json-out docs/model-evals/by-device.json
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

TOOLS_DIR = Path(__file__).parent
ROOT_DIR = TOOLS_DIR.parent
DEFAULT_ROWS_DIR = ROOT_DIR / "docs" / "model-evals"

DEVICE_FIELDS = ("platform", "os", "os_version", "device", "cpu", "ram_gb", "app_version", "api_level")
TIER_ORDER = {"good": 0, "ok": 1, "discouraged": 2, "unknown": 3}


def import_harness() -> Optional[Any]:
    """Import the sibling harness, which needs httpx even for its report half."""
    try:
        import importlib.util

        if importlib.util.find_spec("httpx") is None:
            raise ImportError
        import eval_ollama_models

        return eval_ollama_models
    except ImportError:
        print(
            "eval_report_by_device.py reuses eval_ollama_models.py, which needs httpx.\n"
            "Install it with: pip install httpx",
            file=sys.stderr,
        )
        return None


def hardware_of(row: Dict[str, Any]) -> Dict[str, Any]:
    hardware = (row.get("run") or {}).get("hardware") or {}
    return hardware if isinstance(hardware, dict) else {}


def _text(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def device_key(row: Dict[str, Any]) -> str:
    """The row's device key, or one derived from its hardware fields when absent.

    App rows always carry `device_key`; rows from the Python harness never do,
    so they group by the hardware fields they recorded.
    """
    key = row.get("device_key")
    if isinstance(key, str) and key.strip():
        return key.strip()
    hardware = hardware_of(row)
    parts: List[str] = []
    for field in ("platform", "os", "os_version", "device", "cpu"):
        value = _text(hardware.get(field))
        if value:
            parts.append(value)
    ram = hardware.get("ram_gb")
    if isinstance(ram, (int, float)):
        parts.append(f"{ram}GB")
    return "|".join(parts) or "unknown"


def device_label(hardware: Dict[str, Any]) -> str:
    parts: List[str] = []
    os_name = _text(hardware.get("os"))
    os_version = _text(hardware.get("os_version"))
    if os_name:
        parts.append(f"{os_name} {os_version}".strip())
    elif _text(hardware.get("platform")):
        parts.append(_text(hardware["platform"]))
    for field in ("device", "cpu"):
        value = _text(hardware.get(field))
        if value:
            parts.append(value)
    ram = hardware.get("ram_gb")
    if isinstance(ram, (int, float)):
        parts.append(f"{ram}GB")
    return " · ".join(parts) if parts else "Unknown device"


def merged_hardware(rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Fill the device description from the newest rows, field by field."""
    merged: Dict[str, Any] = {}
    newest_first = sorted(rows, key=lambda row: row.get("started_at") or "", reverse=True)
    for row in newest_first:
        for field, value in hardware_of(row).items():
            if field in DEVICE_FIELDS and field not in merged and value not in (None, ""):
                merged[field] = value
    return merged


def select_rows(rows: List[Dict[str, Any]], per_model: str) -> List[Dict[str, Any]]:
    if per_model == "all":
        return rows
    usable = [row for row in rows if not row.get("in_progress")] or rows
    if per_model == "max-cells":
        return [max(usable, key=lambda row: (len(row.get("cells") or []), row.get("started_at") or ""))]
    return [max(usable, key=lambda row: row.get("started_at") or "")]


def sort_key(rollup: Dict[str, Any]) -> Tuple[Any, ...]:
    quality = rollup.get("quality")
    suite = rollup.get("suite_s")
    return (
        TIER_ORDER.get(rollup.get("tier") or "", 9),
        -(quality if quality is not None else -1.0),
        suite if suite is not None else float("inf"),
        (rollup.get("display_name") or rollup.get("model_id") or "").lower(),
    )


def build_report(
    rows: List[Dict[str, Any]],
    harness: Any,
    per_model: str,
) -> List[Dict[str, Any]]:
    by_device: Dict[str, List[Dict[str, Any]]] = {}
    for row in rows:
        by_device.setdefault(device_key(row), []).append(row)

    devices: List[Dict[str, Any]] = []
    for key, device_rows in by_device.items():
        by_model: Dict[str, List[Dict[str, Any]]] = {}
        for row in device_rows:
            by_model.setdefault(row.get("model_id") or "?", []).append(row)

        models: List[Dict[str, Any]] = []
        for model_rows in by_model.values():
            selected = select_rows(model_rows, per_model)
            analysis = harness.analyze_row(harness.merge_rows(selected))
            models.append(
                {
                    "analysis": analysis,
                    "rollup": analysis["rollup"],
                    "rows": selected,
                },
            )
        models.sort(key=lambda entry: sort_key(entry["rollup"]))
        hardware = merged_hardware(device_rows)
        devices.append(
            {
                "key": key,
                "label": device_label(hardware),
                "hardware": hardware,
                "rows": len(device_rows),
                "models": models,
            },
        )
    devices.sort(key=lambda device: device["label"].lower())
    return devices


def _fmt(value: Optional[float], places: int = 2) -> str:
    return "-" if value is None else f"{value:.{places}f}"


def _escape(text: str) -> str:
    return text.replace("|", "\\|")


def _flags(rollup: Dict[str, Any]) -> List[str]:
    flags: List[str] = []
    if rollup.get("in_progress"):
        flags.append("in-progress")
    if rollup.get("timeouts"):
        flags.append(f"timeout×{rollup['timeouts']}")
    if rollup.get("errors"):
        flags.append(f"error×{rollup['errors']}")
    if rollup.get("sleeps"):
        flags.append(f"slept×{rollup['sleeps']}")
    return flags


def render_markdown(devices: List[Dict[str, Any]], details: bool, harness: Any) -> str:
    total_rows = sum(device["rows"] for device in devices)
    lines: List[str] = [
        "# On-device model evals",
        "",
        f"{total_rows} row(s) across {len(devices)} device(s), grouped by hardware and OS",
        "so a per-device recommendation is not diluted by a faster or slower machine.",
    ]
    if not devices:
        lines += ["", "No rows yet."]
        return "\n".join(lines) + "\n"

    for device in devices:
        lines += [
            "",
            f"## {device['label']}",
            "",
            f"Device key: `{device['key']}` · {device['rows']} row(s)",
            "",
            "| model | size | quality | tier | suite_s | ok | clean | json | est_gb | notes |",
            "|---|---|---|---|---|---|---|---|---|---|",
        ]
        for entry in device["models"]:
            rollup = entry["rollup"]
            notes = list(rollup.get("notes") or []) + _flags(rollup)
            lines.append(
                "| "
                + " | ".join(
                    [
                        _escape(rollup.get("display_name") or rollup.get("model_id") or "?"),
                        rollup.get("size_label") or "?",
                        _fmt(rollup.get("quality"), 1),
                        str(rollup.get("tier") or ""),
                        _fmt(rollup.get("suite_s")),
                        _fmt(rollup.get("ok_rate")),
                        _fmt(rollup.get("clean_rate")),
                        _fmt(rollup.get("json_ok_rate")),
                        _fmt(rollup.get("estimated_gb"), 1),
                        _escape("<br>".join(notes)),
                    ],
                )
                + " |",
            )

        if details:
            for entry in device["models"]:
                rollup = entry["rollup"]
                analyzed = entry["analysis"]
                model = analyzed["row"].get("model") or {}
                summary: List[str] = []
                if model.get("size_bytes"):
                    summary.append(f"Size: {model['size_bytes'] / 1024**3:.2f} GB")
                elif rollup.get("estimated_gb"):
                    summary.append(f"Est. size: {rollup['estimated_gb']:.1f} GB")
                if model.get("quantization"):
                    summary.append(f"Quant: {model['quantization']}")
                if model.get("parameter_size"):
                    summary.append(f"Params: {model['parameter_size']}")
                heading = rollup.get("display_name") or rollup.get("model_id") or "?"
                suffix = f" ({', '.join(summary)})" if summary else ""
                lines += ["", f"### {heading}{suffix}", ""]
                per_run = len({cell.get("run_id") for cell in analyzed["cells"] if cell.get("run_id")}) > 1
                columns = (["run"] if per_run else []) + [
                    "fixture",
                    "interaction",
                    "reps",
                    "ok",
                    "clean",
                    "json",
                    "arr",
                    "timeouts",
                    "mean",
                    "p50",
                    "p95",
                    "tps",
                ]
                lines.append("| " + " | ".join(columns) + " |")
                lines.append("|" + "---|" * len(columns))
                for group in harness._detail_groups(analyzed, per_run):
                    cells = ([group["run_id"] or "?"] if per_run else []) + [
                        group["fixture"],
                        group["interaction"],
                        str(group["reps"]),
                        _fmt(group["ok_rate"]),
                        _fmt(group["clean_rate"]),
                        _fmt(group["json_ok_rate"]),
                        _fmt(group["array_strings_rate"]),
                        str(group["timeouts"]),
                        _fmt(group["latency_mean_s"]),
                        _fmt(group["latency_p50_s"]),
                        _fmt(group["latency_p95_s"]),
                        _fmt(group.get("gen_tps_median"), 1),
                    ]
                    lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines) + "\n"


def to_json(devices: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "generated_by": "eval_report_by_device.py",
        "devices": [
            {
                "key": device["key"],
                "label": device["label"],
                "hardware": device["hardware"],
                "rows": device["rows"],
                "models": [entry["rollup"] for entry in device["models"]],
            }
            for device in devices
        ],
    }


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Group saved evaluation rows by hardware/OS and recommend a model per device.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "paths",
        nargs="*",
        default=[str(DEFAULT_ROWS_DIR)],
        help="Folders or individual row JSON files; defaults to docs/model-evals",
    )
    parser.add_argument(
        "--per-model",
        choices=["all", "latest", "max-cells"],
        default="all",
        help="Which of a model's rows feed its line: every run pooled, the newest, or the largest",
    )
    parser.add_argument(
        "--min-schema",
        type=int,
        default=None,
        help="Skip rows older than this schema (defaults to the harness's current schema)",
    )
    parser.add_argument(
        "--allow-fixtures",
        action="store_true",
        help="Include rows measured under a different prompt/fixture version",
    )
    parser.add_argument(
        "--device",
        dest="devices",
        action="append",
        default=None,
        metavar="TEXT",
        help="Only devices whose key or label contains TEXT (case-insensitive); repeatable",
    )
    parser.add_argument(
        "--details",
        action="store_true",
        help="Append the per-interaction table for every model",
    )
    parser.add_argument("--out", help="Write the markdown report here instead of stdout")
    parser.add_argument("--json-out", help="Also write the structured report here")
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    harness = import_harness()
    if harness is None:
        return 2

    rows, load_stats = harness.collect_rows(args.paths)
    min_schema = harness.SCHEMA if args.min_schema is None else args.min_schema
    rows, filter_stats = harness.filter_rows(
        rows,
        min_schema=min_schema,
        allow_fixtures=args.allow_fixtures,
    )
    if not rows:
        print(
            f"No usable rows in {', '.join(args.paths)} "
            f"({load_stats['files']} file(s) read, {filter_stats}).",
            file=sys.stderr,
        )
        return 1

    devices = build_report(rows, harness, args.per_model)
    if args.devices:
        patterns = [pattern.lower() for pattern in args.devices]
        devices = [
            device
            for device in devices
            if any(
                pattern in device["key"].lower() or pattern in device["label"].lower()
                for pattern in patterns
            )
        ]
        if not devices:
            print(f"No devices matched: {', '.join(args.devices)}", file=sys.stderr)
            return 1

    markdown = render_markdown(devices, args.details, harness)
    if args.out:
        out_path = Path(args.out).expanduser()
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(markdown, encoding="utf-8")
        print(f"Wrote {out_path}", file=sys.stderr)
    else:
        print(markdown, end="")

    if args.json_out:
        json_path = Path(args.json_out).expanduser()
        json_path.parent.mkdir(parents=True, exist_ok=True)
        json_path.write_text(json.dumps(to_json(devices), indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {json_path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
