#!/usr/bin/env python3
"""
Extract Alpha Thinker planning data (projects, rounds, questions, answers)
from an alphathinker.db SQLite database, optionally pulling it from an
Android device over ADB, and optionally convert it to an importable
portable interchange archive (.thinker).

Usage examples:
  # Pull the current app DB from a connected device and export to JSON
  ./tools/extract_planning_data.py --pull --out recovered.json

  # Work with a DB file you already have locally
  ./tools/extract_planning_data.py --db /path/to/alphathinker.db --out recovered.json

  # Also emit SQL INSERT statements for the extracted rows
  ./tools/extract_planning_data.py --db old.db --out recovered.json --sql recovered.sql

  # Convert extracted data directly to an importable .thinker archive
  ./tools/extract_planning_data.py --db old.db --interchange recovered.thinker

  # Convert a previously exported JSON file to a .thinker archive
  ./tools/extract_planning_data.py --from-json recovered.json --interchange recovered.thinker

Notes:
  - Pulling uses `adb exec-out run-as <package> cat databases/<name>`, which
    requires a debuggable build of the app on the device. If that fails, copy
    the DB off the device by other means and pass it with --db.
  - Exports are schema-tolerant: each table is read with `SELECT *` so older
    database versions with different columns still export whatever they have.
  - WAL mode may leave recent pages in -wal/-shm files; when pulling from a
    device we also try to fetch them so the local copy can be opened
    consistently. For best results, fully stop the app before pulling.
  - Interchange output is a gzip-compressed ProjectInterchangeEnvelope JSON
    (.thinker), compatible with the app's import flow.
"""

from __future__ import annotations

import argparse
import gzip
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import time
from typing import Any

DEFAULT_PACKAGE = "alphainterplanetary.thinker"
DEFAULT_DB_NAME = "alphathinker.db"
TABLES = ["projects", "rounds", "questions", "answers"]

PROJECT_INTERCHANGE_SCHEMA_VERSION = 1
EXPORT_SNAPSHOT_SCHEMA_VERSION = 2

# Built-in phase metadata, mirrored from Phase.kt for stable interchange output.
# Unknown/custom phase keys are preserved with fallback label/order.
BUILTIN_PHASES: dict[str, tuple[str, int]] = {
  "scope-goals": ("Scope & Goals", 1),
  "research": ("Research", 2),
  "design": ("Design", 3),
  "execution-plan": ("Execution Plan", 4),
  "validation-plan": ("Validation Plan", 5),
  "definition-of-done": ("Definition of Done", 6),
}
NO_PHASE_KEY = "__unassigned__"


def run(cmd: list[str], **kwargs: Any) -> subprocess.CompletedProcess[bytes]:
  return subprocess.run(cmd, check=False, **kwargs)


def pull_file_via_run_as(package: str, remote_path: str, local_path: str) -> tuple[bool, str]:
  cmd = ["adb", "exec-out", "run-as", package, "cat", remote_path]
  proc = run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  if proc.returncode != 0:
    err = proc.stderr.decode("utf-8", errors="replace").strip()
    return False, err or f"adb exited with {proc.returncode}"
  data = proc.stdout
  if not data:
    return False, "pulled file is empty (does it exist on device?)"
  os.makedirs(os.path.dirname(local_path) or ".", exist_ok=True)
  with open(local_path, "wb") as f:
    f.write(data)
  return True, ""


def pull_database(package: str, db_name: str, dest_db: str) -> None:
  db_remote = f"databases/{db_name}"
  ok, err = pull_file_via_run_as(package, db_remote, dest_db)
  if not ok:
    raise SystemExit(
      "Failed to pull database over ADB (run-as). "
      f"Package={package}, db={db_name}.\n"
      f"adb/run-as error: {err}\n\n"
      "Tips: ensure a device is connected (`adb devices`), the app is a "
      "debuggable build of this package, and the DB exists. Otherwise copy "
      "the DB to your machine and rerun with --db <path>."
    )
  print(f"Pulled {dest_db} ({os.path.getsize(dest_db)} bytes)")

  # Try to fetch WAL/SHM sidecars so SQLite can see uncheckpointed pages.
  for suffix in ("-wal", "-shm"):
    side_remote = db_remote + suffix
    side_local = dest_db + suffix
    ok, _ = pull_file_via_run_as(package, side_remote, side_local)
    if ok and os.path.getsize(side_local) > 0:
      print(f"Pulled sidecar {side_local} ({os.path.getsize(side_local)} bytes)")
    elif os.path.exists(side_local) and os.path.getsize(side_local) == 0:
      os.remove(side_local)


def list_tables(conn: sqlite3.Connection) -> list[str]:
  rows = conn.execute(
    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
  ).fetchall()
  return [r[0] for r in rows]


def table_columns(conn: sqlite3.Connection, table: str) -> list[str]:
  rows = conn.execute(f'PRAGMA table_info("{table}")').fetchall()
  return [r[1] for r in rows]


def export_table(conn: sqlite3.Connection, table: str) -> list[dict[str, Any]]:
  conn.row_factory = sqlite3.Row
  rows = conn.execute(f'SELECT * FROM "{table}"').fetchall()
  return [dict(r) for r in rows]


def quote_ident(name: str) -> str:
  return '"' + name.replace('"', '""') + '"'


def sql_literal(value: Any) -> str:
  if value is None:
    return "NULL"
  if isinstance(value, bool):
    return "1" if value else "0"
  if isinstance(value, (int, float)):
    return str(value)
  if isinstance(value, bytes):
    return "X'" + value.hex() + "'"
  s = str(value)
  return "'" + s.replace("'", "''") + "'"


def write_sql(path: str, data: dict[str, list[dict[str, Any]]]) -> None:
  lines: list[str] = [
    "-- Extracted Alpha Thinker planning data",
    "BEGIN TRANSACTION;",
    "",
  ]
  for table in TABLES:
    rows = data.get(table, [])
    if not rows:
      continue
    cols = list(rows[0].keys())
    col_sql = ", ".join(quote_ident(c) for c in cols)
    lines.append(f"-- {table}: {len(rows)} row(s)")
    for row in rows:
      vals = ", ".join(sql_literal(row.get(c)) for c in cols)
      lines.append(
        f"INSERT OR REPLACE INTO {quote_ident(table)} ({col_sql}) VALUES ({vals});"
      )
    lines.append("")
  lines.append("COMMIT;")
  with open(path, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))


def _phase_meta(phase_key: str) -> tuple[str, int]:
  if phase_key in BUILTIN_PHASES:
    return BUILTIN_PHASES[phase_key]
  if phase_key == NO_PHASE_KEY:
    return ("Unassigned", 2_147_483_647)
  # Unknown/custom: preserve key, give it a stable fallback label/order.
  return (phase_key, 9999)


def _as_int(value: Any, default: int = 0) -> int:
  if value is None:
    return default
  if isinstance(value, bool):
    return int(value)
  if isinstance(value, (int, float)):
    return int(value)
  try:
    return int(str(value))
  except Exception:
    return default


def _as_opt_int(value: Any) -> int | None:
  if value is None:
    return None
  if isinstance(value, str) and value.strip() == "":
    return None
  return _as_int(value, 0)


def _as_str(value: Any, default: str = "") -> str:
  if value is None:
    return default
  return str(value)


def _as_opt_str(value: Any) -> str | None:
  if value is None:
    return None
  s = str(value)
  return s


def build_export_snapshot(
  project: dict[str, Any],
  rounds: list[dict[str, Any]],
  questions: list[dict[str, Any]],
  answers_by_question: dict[str, list[dict[str, Any]]],
) -> dict[str, Any]:
  project_id = _as_str(project.get("id"))
  rounds_sorted = sorted(
    rounds,
    key=lambda r: (_as_int(r.get("roundNumber"), 0), _as_int(r.get("startedAt"), 0), _as_str(r.get("id"))),
  )

  round_by_id = { _as_str(r.get("id")): r for r in rounds_sorted if _as_str(r.get("id")) }
  phase_by_round_id = { rid: _as_str(r.get("phase"), NO_PHASE_KEY) for rid, r in round_by_id.items() }

  # Fallback phase: current in-progress round's phase, else first built-in, else NO_PHASE.
  in_progress = [r for r in rounds_sorted if r.get("completedAt") is None]
  if in_progress:
    fallback_phase = _as_str(in_progress[-1].get("phase"), "scope-goals")
  elif rounds_sorted:
    fallback_phase = _as_str(rounds_sorted[-1].get("phase"), "scope-goals")
  else:
    fallback_phase = "scope-goals"

  def phase_for_question(q: dict[str, Any]) -> str:
    rid = _as_str(q.get("roundId"))
    return phase_by_round_id.get(rid, fallback_phase)

  questions_sorted = sorted(
    questions,
    key=lambda q: (_as_int(q.get("createdAt"), 0), _as_int(q.get("sortOrder"), 0), _as_str(q.get("id"))),
  )

  questions_by_phase: dict[str, list[dict[str, Any]]] = {}
  for q in questions_sorted:
    pk = phase_for_question(q)
    questions_by_phase.setdefault(pk, []).append(q)

  # Include phases evidenced by rounds, even if no questions.
  for r in rounds_sorted:
    pk = _as_str(r.get("phase"), NO_PHASE_KEY)
    questions_by_phase.setdefault(pk, [])

  # Stable phase order: built-in order first, then unknown by key, NO_PHASE last-ish by meta.
  def phase_order(pk: str) -> tuple[int, str]:
    _, order = _phase_meta(pk)
    return (order, pk)

  traversed_phase_keys = sorted(questions_by_phase.keys(), key=phase_order)

  phases_snapshot = []
  for pk in traversed_phase_keys:
    label, order = _phase_meta(pk)
    phase_questions = []
    for q in questions_by_phase.get(pk, []):
      qid = _as_str(q.get("id"))
      q_answers = sorted(
        answers_by_question.get(qid, []),
        key=lambda a: (_as_int(a.get("createdAt"), 0), _as_str(a.get("id"))),
      )
      answers_snapshot = []
      for a in q_answers:
        answers_snapshot.append({
          "id": _as_str(a.get("id")),
          "questionId": _as_str(a.get("questionId"), qid),
          "text": _as_str(a.get("text")),
          "createdAtEpochMs": _as_int(a.get("createdAt"), 0),
        })
      selected_answer_id = q.get("answerId")
      selected_answer_id = _as_str(selected_answer_id) if selected_answer_id is not None else None
      if selected_answer_id is not None and selected_answer_id == "":
        selected_answer_id = None
      # Keep only selected answer that exists, mirroring import/export semantics.
      if selected_answer_id is not None and not any(a["id"] == selected_answer_id for a in answers_snapshot):
        selected_answer_id = None

      draft_text = q.get("draftText")
      draft_text = _as_str(draft_text) if draft_text is not None else None
      if draft_text == "":
        draft_text = None
      draft_updated = _as_opt_int(q.get("draftUpdatedAt"))
      # Enforce mutual exclusion like domain model: committed wins.
      if selected_answer_id is not None:
        draft_text = None
        draft_updated = None

      phase_questions.append({
        "id": qid,
        "text": _as_str(q.get("text")),
        "timestampEpochMs": _as_int(q.get("createdAt"), 0),
        "roundId": _as_str(q.get("roundId")),
        "ignoredAtEpochMs": _as_opt_int(q.get("ignoredAt")),
        "compacted": False,
        "answerId": selected_answer_id,
        "draftText": draft_text,
        "draftUpdatedAtEpochMs": draft_updated,
        "answers": answers_snapshot,
      })
    phases_snapshot.append({
      "phaseKey": pk,
      "phaseLabel": label,
      "phaseOrder": order,
      "questions": phase_questions,
    })

  rounds_snapshot = []
  for r in rounds_sorted:
    rounds_snapshot.append({
      "id": _as_str(r.get("id")),
      "projectId": _as_str(r.get("projectId"), project_id),
      "phaseKey": _as_str(r.get("phase"), NO_PHASE_KEY),
      "roundNumber": max(1, _as_int(r.get("roundNumber"), 1)),
      "origin": _as_str(r.get("origin"), "Initial"),
      "startedAtEpochMs": _as_int(r.get("startedAt"), 0),
      "completedAtEpochMs": _as_opt_int(r.get("completedAt")),
      "outcome": _as_str(r.get("outcome"), "Pending"),
      "outcomeDetail": r.get("outcomeDetail") if r.get("outcomeDetail") is not None else None,
    })

  return {
    "schemaVersion": EXPORT_SNAPSHOT_SCHEMA_VERSION,
    "projectId": project_id,
    "title": _as_str(project.get("title")),
    "synopsis": _as_str(project.get("synopsis")),
    "status": _as_str(project.get("status"), "Draft") or "Draft",
    "createdAtEpochMs": _as_int(project.get("createdAt"), 0),
    "revisionUpdatedAtEpochMs": _as_int(project.get("updatedAt"), 0),
    "phases": phases_snapshot,
    "rounds": rounds_snapshot,
  }


def build_interchange_envelope(
  data: dict[str, list[dict[str, Any]]],
  app_version: str | None = None,
) -> dict[str, Any]:
  projects = data.get("projects", [])
  rounds = data.get("rounds", [])
  questions = data.get("questions", [])
  answers = data.get("answers", [])

  rounds_by_project: dict[str, list[dict[str, Any]]] = {}
  for r in rounds:
    rounds_by_project.setdefault(_as_str(r.get("projectId")), []).append(r)

  questions_by_project: dict[str, list[dict[str, Any]]] = {}
  for q in questions:
    questions_by_project.setdefault(_as_str(q.get("projectId")), []).append(q)

  answers_by_question: dict[str, list[dict[str, Any]]] = {}
  for a in answers:
    answers_by_question.setdefault(_as_str(a.get("questionId")), []).append(a)

  snapshots = []
  for p in projects:
    pid = _as_str(p.get("id"))
    snapshot = build_export_snapshot(
      project=p,
      rounds=rounds_by_project.get(pid, []),
      questions=questions_by_project.get(pid, []),
      answers_by_question=answers_by_question,
    )
    snapshots.append({"snapshot": snapshot})

  return {
    "schemaVersion": PROJECT_INTERCHANGE_SCHEMA_VERSION,
    "exportedAtEpochMs": int(time.time() * 1000),
    "appId": "alpha-thinker",
    "appVersion": app_version,
    "projects": snapshots,
  }


def write_interchange(path: str, data: dict[str, list[dict[str, Any]]], app_version: str | None = None) -> dict[str, Any]:
  envelope = build_interchange_envelope(data, app_version=app_version)
  if not envelope["projects"]:
    raise ValueError("No projects found to convert into an interchange archive")
  payload = json.dumps(envelope, indent=2, ensure_ascii=False, sort_keys=False).encode("utf-8")
  compressed = gzip.compress(payload)
  os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
  with open(path, "wb") as f:
    f.write(compressed)
  return envelope


def load_extract_json(path: str) -> dict[str, list[dict[str, Any]]]:
  with open(path, "r", encoding="utf-8") as f:
    obj = json.load(f)
  if isinstance(obj, dict) and "tables" in obj and isinstance(obj["tables"], dict):
    tables = obj["tables"]
  elif isinstance(obj, dict) and all(k in obj for k in ("projects", "rounds", "questions", "answers")):
    # Allow a raw tables dict.
    tables = obj
  else:
    raise ValueError(
      "Unsupported JSON format: expected extract_planning_data.py output with a 'tables' object"
    )
  data: dict[str, list[dict[str, Any]]] = {}
  for table in TABLES:
    rows = tables.get(table, [])
    if not isinstance(rows, list):
      raise ValueError(f"Table '{table}' must be a list")
    data[table] = rows
  return data


def write_outputs(args: argparse.Namespace, data: dict[str, list[dict[str, Any]]], source_db: str) -> None:
  if args.out:
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
      json.dump(
        {
          "source_db": source_db,
          "tables": data,
        },
        f,
        indent=2,
        ensure_ascii=False,
        sort_keys=False,
      )
      f.write("\n")
    print(f"Wrote JSON export: {args.out}")

  if args.sql:
    write_sql(args.sql, data)
    print(f"Wrote SQL export: {args.sql}")

  if args.interchange:
    try:
      envelope = write_interchange(args.interchange, data, app_version=getattr(args, "app_version", None))
    except Exception as e:
      raise SystemExit(f"Failed to write interchange archive: {e}")
    count = len(envelope.get("projects", []))
    print(f"Wrote interchange archive: {args.interchange} ({count} project(s))")


def main() -> int:
  ap = argparse.ArgumentParser(description=__doc__)
  src = ap.add_mutually_exclusive_group()
  src.add_argument("--pull", action="store_true", help="Pull DB from a connected Android device via adb run-as.")
  src.add_argument("--db", metavar="PATH", help="Path to a local alphathinker.db file.")
  src.add_argument("--from-json", metavar="PATH", help="Path to a previously exported extract JSON file to convert.")
  ap.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Android package name (default: {DEFAULT_PACKAGE}).")
  ap.add_argument("--db-name", default=DEFAULT_DB_NAME, help=f"Database filename (default: {DEFAULT_DB_NAME}).")
  ap.add_argument("--out", metavar="PATH", default=None, help="Output JSON path. Defaults to alphathinker-extract.json when no --interchange output is requested.")
  ap.add_argument("--sql", metavar="PATH", help="Optional output path for SQL INSERT statements.")
  ap.add_argument(
    "--interchange",
    metavar="PATH",
    help="Output path for an importable portable interchange archive (.thinker, gzip-compressed).",
  )
  ap.add_argument("--app-version", metavar="VERSION", help="Optional app version to record in interchange envelope metadata.")
  ap.add_argument("--keep-db", metavar="PATH", help="When pulling, save the pulled DB to this path instead of a temp file.")
  ap.add_argument("--list-tables", action="store_true", help="Print tables/columns found and exit without writing exports.")
  args = ap.parse_args()

  if not args.pull and not args.db and not args.from_json:
    ap.error("specify --pull to fetch from a device, --db <path> to use a local database file, or --from-json <path> to convert an extract")

  # Default JSON output only when no other primary output was requested, preserving
  # the old behavior for DB/pull extraction workflows.
  if args.out is None and not args.interchange and not args.sql and not args.list_tables and not args.from_json:
    args.out = "alphathinker-extract.json"

  if args.from_json and not args.out and not args.interchange and not args.sql and not args.list_tables:
    ap.error("with --from-json, specify --interchange, --out, and/or --sql")

  # --from-json: load previously extracted data, no DB needed.
  if args.from_json:
    if not os.path.isfile(args.from_json):
      print(f"JSON file not found: {args.from_json}", file=sys.stderr)
      return 2
    try:
      data = load_extract_json(args.from_json)
    except Exception as e:
      print(f"Failed to read extract JSON: {e}", file=sys.stderr)
      return 2
    print(f"Loaded extract JSON: {args.from_json}")
    for table in TABLES:
      rows = data.get(table, [])
      cols = list(rows[0].keys()) if rows else []
      print(f"  - {table}: {len(rows)} row(s){', columns: ' + ', '.join(cols) if cols else ''}")

    if args.list_tables:
      return 0

    source_db = os.path.abspath(args.from_json)
    write_outputs(args, data, source_db)
    return 0

  cleanup = False
  if args.db:
    db_path = args.db
    if not os.path.isfile(db_path):
      print(f"Database file not found: {db_path}", file=sys.stderr)
      return 2
  else:
    if args.keep_db:
      db_path = args.keep_db
    else:
      fd, db_path = tempfile.mkstemp(prefix="alphathinker-", suffix=".db")
      os.close(fd)
      cleanup = True
    try:
      pull_database(args.package, args.db_name, db_path)
      if args.keep_db:
        print(f"Kept pulled database at: {db_path}")
    except SystemExit:
      if cleanup and os.path.exists(db_path):
        os.remove(db_path)
      raise

  try:
    # Open read-only. Avoid immutable=1 here so SQLite can still consult
    # pulled -wal/-shm sidecars when present.
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
  except sqlite3.Error:
    conn = sqlite3.connect(db_path)

  try:
    present = list_tables(conn)
    print(f"Tables in DB: {', '.join(present) if present else '(none)'}")

    if args.list_tables:
      for t in present:
        cols = table_columns(conn, t)
        print(f"  - {t}: {', '.join(cols)}")
      return 0

    data: dict[str, list[dict[str, Any]]] = {}
    for table in TABLES:
      if table not in present:
        print(f"  - {table}: not present (skipped)")
        data[table] = []
        continue
      rows = export_table(conn, table)
      cols = table_columns(conn, table)
      data[table] = rows
      print(f"  - {table}: {len(rows)} row(s), columns: {', '.join(cols)}")

    source_db = os.path.abspath(db_path) if args.db else f"adb://{args.package}/{args.db_name}"
    write_outputs(args, data, source_db)
  finally:
    conn.close()
    if cleanup and not args.keep_db:
      for path in (db_path, db_path + "-wal", db_path + "-shm"):
        try:
          os.remove(path)
        except FileNotFoundError:
          pass

  return 0


if __name__ == "__main__":
  raise SystemExit(main())
