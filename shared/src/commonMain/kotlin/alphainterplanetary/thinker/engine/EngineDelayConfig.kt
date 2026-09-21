package alphainterplanetary.thinker.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One [PlanningEngine] interaction that the artificial testing delay can target. */
enum class EngineInteraction(val label: String) {
  RecommendTitle("Recommend title"),
  InitialQuestions("Initial questions"),
  FollowUpQuestions("Follow-up questions"),
  RemainingInPhase("Remaining in phase"),
}

/**
 * Artificial slow-down applied to [PlanningEngine] interactions while
 * [enabled] (the Testing setting). Each interaction carries its own hold time,
 * picked from [DelayOptionsSeconds], so the Task Manager's tasks can be made
 * to linger by differing amounts.
 */
data class EngineDelayConfig(
  val enabled: Boolean = false,
  val secondsByInteraction: Map<EngineInteraction, Int> = emptyMap(),
) {

  /** The hold time for [interaction], or null when slowing is off, the delay is
   *  unset, or the interaction has been set to the 0s (off) option. */
  fun durationFor(interaction: EngineInteraction): Duration? {
    val seconds = if (enabled) secondsByInteraction[interaction] else null
    return if (seconds == null || seconds == 0) null else seconds.seconds
  }

  companion object {
    /** The selectable slow-down durations, in seconds; 0 turns an interaction's delay off. */
    val DelayOptionsSeconds: List<Int> = listOf(2, 5, 30, 0)

    /** A fresh install: slowing off, every interaction set to the first option. */
    val Default: EngineDelayConfig = EngineDelayConfig(
      enabled = false,
      secondsByInteraction = EngineInteraction.entries.associateWith {
        DelayOptionsSeconds.first()
      },
    )
  }
}