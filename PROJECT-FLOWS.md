# Project Flows (draft research doc)

Status: **draft / living** — owned by the Phase 2.8 "Research & define stage
progression by project type" item in IMPLEMENTATION-PLAN.md. Everything here is a
hypothesis to validate, not the final model.

## Why flows?

Rounds and stages are generic, but different *kinds* of projects plan
differently. A game, a written report, and a home renovation don't share one
shape — they differ in how many rounds to expect, what each stage's questions
should focus on, and how planning "ends". A **flow** is a category-specific stage
progression that tells the generator (hardcoded pools today, the LLM later) what
to ask and when to report done.

## Mechanics (invariant, applies to every flow)

- Rounds are the on-disk units; wrap-up closes a round and opens the next.
- The planning stage is **derived**: `count of completed rounds + 1` (never stored).
- Wrap-up is **manual and intentional** — there is no automatic round completion.
- The selected flow decides stage count/depth and the per-stage question focus.

## Candidate flows (hypotheses)

Three flows to start with — enough to prove the mechanism without over-modeling:

1. **Game** — deep and iterative: concept, core-loop/MVP, production (with extra
   rounds per mechanic), playtest/tuning, then launch.
2. **Document / report** — short and linear: purpose/audience, outline/content,
   drafting, review/publish.
3. **Home improvement** — constraint-driven: scope/goals, budget/schedule/permits,
   materials/contractor, execution/QC.

### Game

```mermaid
flowchart TD
  G1["Stage 1<br/>Concept & player fantasy<br/>hook, who plays it"] --> GW1["Wrap up · stage → 2"]
  GW1 --> G2["Stage 2<br/>Core loop & MVP<br/>smallest fun build"]
  G2 --> GW2["Wrap up · stage → 3"]
  GW2 --> G3["Stage 3<br/>Mechanics & production<br/>tech, content, risks"]
  G3 -->|"extra rounds per mechanic"| G3
  G3 --> GW3["Wrap up · stage → 4"]
  GW3 --> G4["Stage 4<br/>Playtest & tuning<br/>feedback, balance"]
  G4 --> GW4["Wrap up · stage → 5"]
  GW4 --> G5["Stage 5<br/>Launch & distribution<br/>definition of done"]
  G5 --> GEND["Generator done<br/>deep, many rounds"]
```

### Document / report

```mermaid
flowchart TD
  D1["Stage 1<br/>Purpose & audience<br/>key message"] --> DW1["Wrap up · stage → 2"]
  DW1 --> D2["Stage 2<br/>Outline & contents<br/>research, structure"]
  D2 --> DW2["Wrap up · stage → 3"]
  DW2 --> D3["Stage 3<br/>Drafting<br/>sections in order"]
  D3 --> DW3["Wrap up · stage → 4"]
  DW3 --> D4["Stage 4<br/>Review & publish<br/>feedback, done"]
  D4 --> DEND["Generator done<br/>short and linear"]
```

### Home improvement

```mermaid
flowchart TD
  H1["Stage 1<br/>Scope & goals<br/>what & why"] --> HW1["Wrap up · stage → 2"]
  HW1 --> H2["Stage 2<br/>Budget, schedule, permits<br/>resources & constraints"]
  H2 --> HW2["Wrap up · stage → 3"]
  HW2 --> H3["Stage 3<br/>Materials & contractor<br/>who, what, when"]
  H3 --> HW3["Wrap up · stage → 4"]
  HW3 --> H4["Stage 4<br/>Execution & QC<br/>sequencing, inspection"]
  H4 --> HEND["Generator done<br/>constraint-driven"]
```

## Choosing a flow (how do we determine which one?)

Options, roughly increasing in complexity:

1. **Manual pick at project creation** — the user taps a flow chip (Game /
   Document / Home improvement / General). Simple, reliable, but adds friction at
   the moment of first use.
2. **Lightweight synopsis heuristics** — keyword/structure hints (e.g. "game",
   "quest", "level" → Game; "report", "thesis", "memo", "outline" → Document;
   "remodel", "renovate", "kitchen", "deck" → Home improvement). Anything that
   doesn't match falls back to a **General** flow. Cheap, offline, rough.
3. **Intelligent classification (Phase 3)** — the LLM/embedding picks or suggests
   the flow from the synopsis and can re-suggest as content evolves.

Recommended starting point: a **General default** plus a small keyword detector,
keeping the result user-overridable, until real use proves which calls are wrong.
That said, (1) forces a choice up front and (2) makes wrong calls to fix — the
point of this research task is to settle the tradeoff against the hardcoded pools
that already need per-stage partitioning.

## Open questions

- Which categories actually "matter" enough to justify their own flow? Start with
  three (above) and grow only on evidence.
- Where does the flow live — a persisted `Project.category` (editable) or derived
  each time? Persisted is likely needed: wrap-up enablement and pool selection
  need a stable answer.
- Can the user change a project's flow mid-journey? (Probably not once past stage
  1, or at least it should be expensive.)
- Does "done" differ per flow beyond pool exhaustion? Yes — see the charts:
  launch / publish / QC each have their own definition of done.
- How do per-category pools interact with the user's **global question pool**
  (Phase 4)? Globals likely interleave into whatever flow is active.