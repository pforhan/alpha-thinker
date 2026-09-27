package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Instant

/**
 * How a round came to be — feeds dedup and the LLM interaction log.
 */
enum class RoundOrigin {
  /** The opening round of a phase: project start or a wrap-up advancing to a new phase. */
  Initial,

  /** Reserved for revisiting a completed phase. */
  FollowUp,

  /** The user tapped "Get more questions" in the current phase. */
  UserRequested,
}

/**
 * What one round's generation attempt produced, latched onto the round when its
 * batch lands. This is the app's only record of phase exhaustion: the newest
 * round in a phase carries that phase's latest answer (see
 * `Project.currentPhaseExhausted`), read from storage so it survives restarts,
 * phase changes, and engine swaps.
 */
enum class RoundOutcome {
  /** The batch hasn't landed yet — the round was opened ahead of generation. */
  Pending,

  /** The batch produced fresh questions and the engine can still produce more. */
  MoreAvailable,

  /** The engine reported `QuestionBatch.done`: this phase has nothing more. */
  Exhausted,

  /**
   * The attempt produced nothing usable: the engine threw, or replied with a
   * batch that was empty (or entirely repeats) while claiming more was
   * available. Deliberately *not* exhaustion — the phase stays open so the user
   * can retry, and [Round.outcomeDetail] carries the reason for the UI.
   */
  Failed,
}

/**
 * A set of questions surfaced together (a generation batch), belonging to one
 * [phase] (a stable [Phase] from the shared library).
 *
 * Rounds are the on-disk unit of wrap-up: resolving the round's questions and
 * wrapping it up closes this round ([complete]) and opens the first round of a
 * new phase. The stored [phase] is what the current planning phase is read from.
 */
data class Round(
  val id: String,
  val projectId: String,
  val phase: Phase,
  val roundNumber: Int,
  val origin: RoundOrigin,
  val startedAt: Instant,
  val completedAt: Instant? = null,
  val outcome: RoundOutcome = RoundOutcome.Pending,
  /** Why the attempt failed; only recorded for [RoundOutcome.Failed]. */
  val outcomeDetail: String? = null,
) {
  init {
    require(roundNumber >= 1) {
      "Round $id has roundNumber $roundNumber; round numbers are 1-based within a project"
    }
    require(outcomeDetail == null || outcome == RoundOutcome.Failed) {
      "Round $id carries an outcome detail for $outcome; details are recorded for Failed only"
    }
  }

  val isCompleted: Boolean
    get() = completedAt != null

  fun complete(at: Instant): Round = copy(completedAt = at)

  fun withPending(): Round = withOutcome(RoundOutcome.Pending)
  fun withMoreAvailable(): Round = withOutcome(RoundOutcome.MoreAvailable)
  fun withExhausted(): Round = withOutcome(RoundOutcome.Exhausted)
  fun withFailed(detail: String): Round = withOutcome(RoundOutcome.Failed, detail)

  private fun withOutcome(outcome: RoundOutcome, detail: String? = null): Round =
    copy(outcome = outcome, outcomeDetail = detail)
}