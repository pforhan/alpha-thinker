package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.phases.Phase
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * A phase's content and planning footprint on a project: the questions asked in
 * that phase, the rounds spent in it, and derived stats (answered/ignored/total/
 * resolved and time spent).
 *
 * [spent] is computed relative to the instant the instance was built for (see
 * [Project.allPhaseContent]) so an in-progress phase counts its elapsed time up
 * to that instant.
 */
data class PhaseQuestions(
  val phase: Phase,
  val questions: List<Question>,
  val rounds: List<Round>,
  val spent: Duration,
) {
  val answered: Int
    get() = questions.count { it.isAnswered }

  val ignored: Int
    get() = questions.count { it.isIgnored }

  val total: Int
    get() = questions.size

  /** Count of answered and ignored. */
  val resolved: Int
    get() = answered + ignored

  /** Resolved questions as a whole percent, rounded to the nearest percent (0 if no questions). */
  val completionPercent: Int
    get() = if (total == 0) 0 else (resolved * 100.0 / total).roundToInt()
}
