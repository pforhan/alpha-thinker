package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Instant

/**
 * How a round came to be — feeds dedup and the LLM interaction log.
 */
enum class RoundOrigin {
  /** The opening round of a phase: project start or a wrap-up advancing to a new phase. */
  Initial,

  /**
   * A batch generated automatically — currently used by the interim
   * all-answered auto follow-up in the repository. Once the manual wrap-up
   * replaces that (Phase 2.8), this origin is reserved for revisiting a
   * completed phase.
   */
  FollowUp,

  /** The user tapped "Get more questions" in the current phase. */
  UserRequested,
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
) {
  init {
    require(roundNumber >= 1) {
      "Round $id has roundNumber $roundNumber; round numbers are 1-based within a project"
    }
  }

  val isCompleted: Boolean
    get() = completedAt != null

  fun complete(at: Instant): Round = copy(completedAt = at)
}