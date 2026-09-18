package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A phase's planning footprint on a project: how many of its questions were
 * resolved (answered or ignored) out of the total asked, and how long the
 * project spent in the phase.
 */
data class PhaseStats(
  val phase: Phase,
  val resolved: Int,
  val total: Int,
  val spent: Duration,
)

/**
 * Per-phase aggregates over the project's visited phases (those with at least
 * one round), ordered by library order (the level-up timeline reads left to
 * right). [spent] sums each phase's round durations — `(completedAt ?: now) -
 * startedAt` — so an in-progress phase counts its elapsed time up to [now].
 */
fun Project.phaseStats(now: Instant): List<PhaseStats> {
  val roundsByPhase = rounds.groupBy { it.phase }
  return roundsByPhase.keys
    .sortedBy { it.order }
    .map { phase ->
      val phaseQuestions = questions.filter { phaseForQuestion(it) == phase }
      PhaseStats(
        phase = phase,
        resolved = phaseQuestions.count { it.isAnswered || it.isIgnored },
        total = phaseQuestions.size,
        spent = roundsByPhase.getValue(phase).fold(Duration.ZERO) { acc, round ->
          acc + ((round.completedAt ?: now) - round.startedAt).coerceAtLeast(Duration.ZERO)
        },
      )
    }
}