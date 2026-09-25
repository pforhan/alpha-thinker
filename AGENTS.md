# AGENTS.md

## Project Overview

Alpha Thinker is an edge-LLM powered project planning app with iterative
question-and-answer synthesis. It helps users plan projects through
structured analysis of their project ideas.

See [README.md](README.md) [PRD.md](PRD.md) and [ENG-DESIGN.md](ENG-DESIGN.md) for
architecture details and the full design specification.

## Validating changes

Confirm code and test changes with `./gradlew :shared:allTests`. Always prefer
this shared test target over platform-specific targets. When working specifically
in a target module such as `androidApp`, `desktopApp`, or `iosApp`, use that
module's appropriate test target instead.

Phase-color palettes (in `PhaseTheme.kt`) can be validated without a build via
`tools/palette.py`: `verify` checks every palette against the same WCAG AA and
surface-distance rules as `PhaseThemeTest`, while `check` and `darken` help
shape new colors before editing the source (run `tools/palette.py` for usage).

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
- **Function style:** Prefer expression functions when the function is short
  and its result is clear.
- **Reuse types:** Avoid introducing a new class when an existing class is an
  obvious close match; reuse or extend the existing type when appropriate.

When in doubt, match the surrounding code styles.

## Design System (Material Theme)

All UI is driven by a single shared Material 3 theme in
`shared/src/commonMain/.../ui/theme/`. `AlphaThinkerTheme` wraps
`MaterialTheme` with light + dark `ColorScheme`s (generated from the app
seed color via material-color-utilities), `Typography`, `Shapes`, a
`Dimens` object for the spacing/icon-size scale, and an `ExtendedColors`
composition local for colors that don't map to a standard Material role
(e.g. swipe-action backgrounds).

Rules:

- Never hardcode colors, text styles, font sizes, shapes, or spacing /
  padding values in UI code. Pull them from `MaterialTheme.colorScheme`,
  `MaterialTheme.typography`, `MaterialTheme.shapes`, `LocalExtendedColors`,
  or `Dimens` instead.
- If the UI needs a value the theme doesn't provide, add it to the theme —
  as a named `ExtendedColors` role, a `Dimens` constant, or a `Typography` /
  `Shapes` slot — and reference it. Don't inline literals.
- Keep the theme's palette and scale cohesive: the rest of the app should
  only ever read the theme, never redefine it.

## Roadmap

IMPLEMENTATION-PLAN.md contains the project roadmap.  Always confirm before acting on an item that's not next in the roadmap.
