#!/usr/bin/env python3
"""
Receive Alpha Thinker evaluation rows from a phone over the local network.

The app's Testing sheet can POST every row it has recorded to a receiver on the
same Wi-Fi network. This script is that receiver: it accepts the same
self-describing row objects the app writes to disk, validates them, and files
them into `docs/model-evals` next to the rows the Python harness produced. From
there `eval_report_by_device.py` (or `eval_ollama_models.py report`) reads them
like any other row.

One row per `(model, run)` means a resend is idempotent: the app flushes the
same row after every cell, so a later share simply replaces an earlier partial
version of that row.

Usage:

    python tools/eval_receiver.py
    # then in the app: Testing -> Model eval -> Share, enter this computer's
    # LAN address, e.g. 192.168.1.20:8765

    python tools/eval_receiver.py --port 9000 --rows-dir docs/model-evals/tmp

Endpoints:

    POST /evals     one row, an array of rows, or {"rows": [...]}
    GET  /health    {"ok": true, "rows_dir": "...", "schema": 1}
    GET  /          a short usage note
"""
from __future__ import annotations

import argparse
import json
import re
import socket
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

TOOLS_DIR = Path(__file__).parent
ROOT_DIR = TOOLS_DIR.parent
DEFAULT_ROWS_DIR = ROOT_DIR / "docs" / "model-evals"

SCHEMA = 1
FIXTURES_VERSION = 1
DEFAULT_PORT = 8765
MAX_BODY_BYTES = 64 * 1024 * 1024


def slugify(model_id: str) -> str:
    """The Python harness's file-name slug; the app's store uses the same rule."""
    return re.sub(r"[^A-Za-z0-9._-]+", "_", model_id) or "model"


def hardware_key(row: Dict[str, Any]) -> str:
    """The row's device key, or one derived from its hardware fields when absent."""
    key = row.get("device_key")
    if isinstance(key, str) and key.strip():
        return key.strip()
    hardware = (row.get("run") or {}).get("hardware") or {}
    parts: List[str] = []
    for field in ("platform", "os", "os_version", "device", "cpu"):
        value = hardware.get(field)
        if isinstance(value, str) and value.strip():
            parts.append(value.strip())
    ram = hardware.get("ram_gb")
    if isinstance(ram, (int, float)):
        parts.append(f"{ram}GB")
    return "|".join(parts) or "unknown"


def validate_row(row: Any) -> Optional[str]:
    """Why [row] cannot be filed, or None when it is structurally a row."""
    if not isinstance(row, dict):
        return "row is not an object"
    model_id = row.get("model_id")
    if not isinstance(model_id, str) or not model_id.strip():
        return "row has no model_id"
    run = row.get("run")
    if not isinstance(run, dict):
        return f"{model_id}: row has no run metadata"
    run_id = run.get("id")
    if not isinstance(run_id, str) or not run_id.strip():
        return f"{model_id}: row has no run.id"
    cells = row.get("cells", [])
    if not isinstance(cells, list):
        return f"{model_id}/{run_id}: cells is not an array"
    if not all(isinstance(cell, dict) for cell in cells):
        return f"{model_id}/{run_id}: a cell is not an object"
    return None


def row_file_name(row: Dict[str, Any]) -> str:
    return f"{slugify(row['model_id'])}-{slugify(row['run']['id'])}.json"


def write_row(rows_dir: Path, row: Dict[str, Any]) -> Tuple[str, Path]:
    """Write one row atomically. Returns ("written" | "unchanged", path)."""
    path = (rows_dir / row_file_name(row)).resolve()
    if rows_dir.resolve() not in path.parents:
        raise ValueError("row file escapes the rows directory")
    text = json.dumps(row, indent=2, ensure_ascii=False) + "\n"
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return "unchanged", path
    rows_dir.mkdir(parents=True, exist_ok=True)
    partial = path.with_name(path.name + ".part")
    partial.write_text(text, encoding="utf-8")
    partial.replace(path)
    return "written", path


def rows_in(body: Any) -> List[Any]:
    if isinstance(body, list):
        return body
    if isinstance(body, dict) and isinstance(body.get("rows"), list):
        return body["rows"]
    return [body]


def describe_row(row: Dict[str, Any]) -> str:
    model = row.get("model") or {}
    name = model.get("display_name") or row.get("model_id")
    cells = row.get("cells") or []
    state = "in progress" if row.get("in_progress") else "finished"
    device = hardware_key(row).replace("|", " · ")
    return f"{name} on {device} ({len(cells)} cells, {state})"


class EvalReceiver(BaseHTTPRequestHandler):
    server_version = "AlphaThinkerEvalReceiver/1"

    # Set by make_server.
    rows_dir: Path = DEFAULT_ROWS_DIR

    def _send(self, code: int, text: str) -> None:
        payload = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def _send_json(self, code: int, value: Any) -> None:
        payload = (json.dumps(value, indent=2) + "\n").encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self) -> None:  # noqa: N802 - http.server's spelling
        if self.path in ("/health", "/health/"):
            self._send_json(
                200,
                {
                    "ok": True,
                    "schema": SCHEMA,
                    "fixtures_version": FIXTURES_VERSION,
                    "rows_dir": str(self.rows_dir),
                },
            )
        elif self.path in ("/", ""):
            self._send(
                200,
                "Alpha Thinker eval receiver.\n"
                "POST rows to /evals; GET /health for status.\n"
                f"Rows are filed in {self.rows_dir}.\n",
            )
        else:
            self._send(404, f"No handler for GET {self.path}\n")

    def do_POST(self) -> None:  # noqa: N802 - http.server's spelling
        if self.path.split("?", 1)[0] not in ("/evals", "/eval"):
            self._send(404, f"No handler for POST {self.path}\n")
            return
        length_header = self.headers.get("Content-Length")
        if length_header is None:
            self._send(411, "Content-Length is required.\n")
            return
        try:
            length = int(length_header)
        except ValueError:
            self._send(400, "Content-Length is not a number.\n")
            return
        if length <= 0:
            self._send(400, "The request body is empty.\n")
            return
        if length > MAX_BODY_BYTES:
            self._send(413, f"Body larger than {MAX_BODY_BYTES} bytes.\n")
            return
        try:
            body = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            self._send(400, f"Body is not JSON: {error}\n")
            return

        candidates = rows_in(body)
        if not candidates:
            self._send(400, "No rows in the request.\n")
            return

        written: List[Path] = []
        unchanged: List[Path] = []
        rejected: List[str] = []
        for candidate in candidates:
            problem = validate_row(candidate)
            if problem is not None:
                rejected.append(problem)
                continue
            try:
                outcome, path = write_row(self.rows_dir, candidate)
            except (OSError, ValueError) as error:
                rejected.append(f"{candidate.get('model_id')}: {error}")
                continue
            if outcome == "written":
                written.append(path)
                print(f"  wrote {path.name}: {describe_row(candidate)}")
            else:
                unchanged.append(path)
        total = len(written) + len(unchanged)
        if total:
            # Counts requests that filed at least one row (written or
            # unchanged), which is what --once waits for.
            self.server.received = getattr(self.server, "received", 0) + total
        if total == 0:
            self._send(
                400,
                "No rows could be stored:\n" + "\n".join(f"- {p}" for p in rejected) + "\n",
            )
            return

        summary = (
            f"Stored {len(written)} row(s) in {self.rows_dir}"
            f"{f' ({len(unchanged)} already up to date)' if unchanged else ''}."
        )
        if not written:
            summary = f"All {len(unchanged)} row(s) already up to date in {self.rows_dir}."
        if rejected:
            summary += f" Rejected {len(rejected)}: " + "; ".join(rejected)
        self._send(200, summary + "\n")
        done = getattr(self.server, "done", None)
        if done is not None:
            # Set after the response is written so --once never closes the
            # socket under a client that is still reading it.
            done.set()

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[{self.log_date_time_string()}] {fmt % args}")


def lan_addresses() -> List[str]:
    """Best-effort LAN IPs, so startup can print an address to type into the app."""
    addresses: List[str] = []
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            address = info[4][0]
            if not address.startswith("127.") and address not in addresses:
                addresses.append(address)
    except OSError:
        pass
    if not addresses:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
                probe.connect(("8.8.8.8", 80))
                address = probe.getsockname()[0]
                if not address.startswith("127."):
                    addresses.append(address)
        except OSError:
            pass
    return addresses


def make_server(host: str, port: int, rows_dir: Path) -> ThreadingHTTPServer:
    handler = type("BoundEvalReceiver", (EvalReceiver,), {"rows_dir": rows_dir})
    server = ThreadingHTTPServer((host, port), handler)
    server.daemon_threads = True
    return server


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Receive Alpha Thinker evaluation rows over the LAN.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--host",
        default="0.0.0.0",
        help="Interface to bind; 0.0.0.0 accepts connections from the local network",
    )
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Port to listen on")
    parser.add_argument(
        "--rows-dir",
        default=str(DEFAULT_ROWS_DIR),
        help="Directory the received row files are written to",
    )
    parser.add_argument(
        "--once",
        action="store_true",
        help="Exit after the first request that files at least one row",
    )
    args = parser.parse_args(argv)

    rows_dir = Path(args.rows_dir).expanduser()
    rows_dir.mkdir(parents=True, exist_ok=True)
    server = make_server(args.host, args.port, rows_dir)

    print(f"Listening on {args.host}:{args.port}")
    print(f"Filing rows in {rows_dir}")
    addresses = lan_addresses() if args.host in ("0.0.0.0", "::") else [args.host]
    for address in addresses:
        print(f"  In the app, share to: {address}:{args.port}")
    print("Press Ctrl-C to stop.")
    try:
        if args.once:
            # serve_forever runs on its own thread because a handler reply is
            # written after its worker returns; watching a flag would race the
            # response, so the handler sets this event once it has replied.
            server.done = threading.Event()
            threading.Thread(target=server.serve_forever, daemon=True).start()
            server.done.wait()
            server.shutdown()
        else:
            server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
