#!/usr/bin/env python3
"""
Extract Alpha Thinker planning data (projects, rounds, questions, answers)
from an alphathinker.db SQLite database, optionally pulling it from an
Android device over ADB.

Usage examples:
  # Pull the current app DB from a connected device and export to JSON
  ./tools/extract_planning_data.py --pull --out recovered.json

  # Work with a DB file you already have locally
  ./tools/extract_planning_data.py --db /path/to/alphathinker.db --out recovered.json

  # Also emit SQL INSERT statements for the extracted rows
  ./tools/extract_planning_data.py --db old.db --out recovered.json --sql recovered.sql

Notes:
  - Pulling uses `adb exec-out run-as <package> cat databases/<name>`, which
    requires a debuggable build of the app on the device. If that fails, copy
    the DB off the device by other means and pass it with --db.
  - Exports are schema-tolerant: each table is read with `SELECT *` so older
    database versions with different columns still export whatever they have.
  - WAL mode may leave recent pages in -wal/-shm files; when pulling from a
    device we also try to fetch them so the local copy can be opened
    consistently. For best results, fully stop the app before pulling.
"""

from __future__ import annotations

import argparse
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
from typing import Any

DEFAULT_PACKAGE = "alphainterplanetary.thinker"
DEFAULT_DB_NAME = "alphathinker.db"
TABLES = ["projects", "rounds", "questions", "answers"]


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


def main() -> int:
  ap = argparse.ArgumentParser(description=__doc__)
  src = ap.add_mutually_exclusive_group()
  src.add_argument("--pull", action="store_true", help="Pull DB from a connected Android device via adb run-as.")
  src.add_argument("--db", metavar="PATH", help="Path to a local alphathinker.db file.")
  ap.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Android package name (default: {DEFAULT_PACKAGE}).")
  ap.add_argument("--db-name", default=DEFAULT_DB_NAME, help=f"Database filename (default: {DEFAULT_DB_NAME}).")
  ap.add_argument("--out", metavar="PATH", default="alphathinker-extract.json", help="Output JSON path (default: alphathinker-extract.json).")
  ap.add_argument("--sql", metavar="PATH", help="Optional output path for SQL INSERT statements.")
  ap.add_argument("--keep-db", metavar="PATH", help="When pulling, save the pulled DB to this path instead of a temp file.")
  ap.add_argument("--list-tables", action="store_true", help="Print tables/columns found and exit without writing exports.")
  args = ap.parse_args()

  if not args.pull and not args.db:
    ap.error("specify --pull to fetch from a device, or --db <path> to use a local database file")

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

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
      json.dump(
        {
          "source_db": os.path.abspath(db_path) if args.db else f"adb://{args.package}/{args.db_name}",
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
