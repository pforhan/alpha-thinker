package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.phases.Phase

/**
 * What to do with a project's planning transcript when the next round is
 * requested, if it has outgrown the budget ([PlanningContext.nearLimit]).
 *
 * This is a user decision, not a policy: an over-long prompt is a slowdown
 * rather than a failure, and how much of the interview the user would rather
 * trade away for speed is theirs. The three are ordered by how much they give
 * up — nothing, the oldest answers, the oldest phases' answers through the
 * model — and [DropEarlierAnswers] is the default because it is the one that
 * needs no model and no waiting.
 *
 * None of it applies to an engine that composes no prompt (the built-in Lite
 * engine, which has no
 * [alphainterplanetary.thinker.engine.PlanningEngine.contextWindowTokens]):
 * there is no window to fill, so no choice is ever put to the user and none of
 * these are applied on its behalf.
 */
enum class ContextCompaction {
  /** Send the whole transcript, over budget or not. Slowest, and loses nothing. */
  KeepEverything,

  /**
   * Keep every question and drop answers from the earliest phases first, the
   * current phase's answers always intact ([PlanningContext.trim]). Loses the
   * wording of those answers, keeps the record of what was asked.
   */
  DropEarlierAnswers,

  /**
   * Ask the engine to condense each past phase, oldest first, until the
   * transcript fits — one [alphainterplanetary.thinker.engine.PlanningEngine.summarizePriorAnswers]
   * request per phase, and each phase's Q&A replaced by the model's own summary.
   * Keeps the substance of the earliest phases at the cost of one model call
   * each, and needs an engine that can write summaries
   * ([alphainterplanetary.thinker.engine.PlanningEngine.canSummarize]); without
   * one the choice is never offered.
   */
  SummarizeEarlierPhases,
}

/**
 * What the project looks like to the context budget right now, and what the
 * user would be choosing between — everything the near-limit dialog needs to
 * describe the trade, and everything the repository needs to know about whether
 * asking is warranted at all.
 *
 * A snapshot, not a promise: it is taken before the round is enqueued and
 * re-measured when the task runs, so a project that changed in between (or a
 * user who lowered the budget) is still measured on its own terms.
 */
data class ContextCheck(
  /** The whole transcript's estimated token cost, before any compaction. */
  val estimatedTokens: Int,
  /**
   * The selected model's context window, or null when the engine composes no
   * prompt and so has none. Null takes everything below with it: there is
   * nothing for a budget to protect.
   */
  val windowTokens: Int?,
  /** [windowTokens]'s allowed share, resolved; null when there is no window. */
  val budgetTokens: Int?,
  /**
   * Whether the transcript is in the band worth asking about. False means the
   * request proceeds with [ContextCompaction.DropEarlierAnswers] and nobody is
   * interrupted.
   */
  val nearLimit: Boolean,
  /** Answers that dropping the earliest phases would actually reclaim. */
  val droppableAnswers: Int,
  /**
   * The past phases that could be summarized, oldest first — the units
   * [ContextCompaction.SummarizeEarlierPhases] would work through.
   */
  val summarizablePhases: List<Phase>,
  /**
   * Whether [ContextCompaction.SummarizeEarlierPhases] is on the table: the
   * engine sends the transcript at all, can write summaries, and there is at
   * least one past phase to write one about.
   */
  val canSummarize: Boolean,
)
