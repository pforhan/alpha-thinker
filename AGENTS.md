# AGENTS.md

## Project Overview

Alpha Thinker is an edge-LLM powered project planning app with iterative
question-and-answer synthesis. It helps users plan projects through
structured analysis of their project ideas.

See [README.md](README.md) [PRD.md](PRD.md) and [ENG-DESIGN.md](ENG-DESIGN.md) for
architecture details and the full design specification.

## Code Style (Kotlin / Compose)

The Kotlin codebase follows these formatting conventions:

- **Indentation:** Two spaces. No tabs.
- **Imports:**
  - No wildcard (`*`) imports — always import explicitly.
  - Grouped and alphabetically sorted: project packages
    (`alphainterplanetary.*`) first, then external libraries
    (`androidx.*`, `kotlinx.*`, etc.), with a blank line only between
    the package declaration and the first group.
- **Trailing commas:** Include trailing commas in multi-line parameter
  lists, function calls, and data class properties (e.g. `val questions:
  List<QuestionEntity>,`).
- **Line wrapping:** Break and indent continuation lines to align with
  the opening expression (see the SQL migration chaining in
  `AppDatabase.kt`).
- **Newlines:** End files with a trailing newline.

When in doubt, match the surrounding code styles.

## Deferred / Roadmap

IMPLEMENTATION-PLAN.md contains the project roadmap.  Always confirm before acting on an item that's not next in the roadmap.

- **Lookup / Web Search (Edge):** Optional agentic research for the LLM via
  Koog so it can fetch facts and web results as needed. Gated by an app-wide
  setting shared with the LLM on/off toggle. See ENG-DESIGN.md research and
  IMPLEMENTATION-PLAN.md Phase 3.

