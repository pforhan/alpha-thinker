package alphainterplanetary.thinker.llm

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One [QuestionGenerator] interaction that the artificial testing delay can target. */
enum class GeneratorInteraction(val label: String) {
  RecommendTitle("Recommend title"),
  InitialQuestions("Initial questions"),
  FollowUpQuestions("Follow-up questions"),
  RemainingInPhase("Remaining in phase"),
}

/**
 * Artificial slow-down applied to [QuestionGenerator] interactions while
 * [enabled] (the Testing setting). Each interaction carries its own hold time,
 * picked from [DelayOptionsSeconds], so the Task Manager's tasks can be made
 * to linger by differing amounts.
 */
data class GeneratorDelayConfig(
  val enabled: Boolean = false,
  val secondsByInteraction: Map<GeneratorInteraction, Int> = emptyMap(),
) {

  /** The hold time for [interaction], or null when slowing is off or the delay is unset. */
  fun durationFor(interaction: GeneratorInteraction): Duration? =
    if (enabled) secondsByInteraction[interaction]?.seconds else null

  companion object {
    /** The selectable slow-down durations, in seconds (2s, 5s, or 30s). */
    val DelayOptionsSeconds: List<Int> = listOf(2, 5, 30)

    /** A fresh install: slowing off, every interaction set to the first option. */
    val Default: GeneratorDelayConfig = GeneratorDelayConfig(
      enabled = false,
      secondsByInteraction = GeneratorInteraction.entries.associateWith {
        DelayOptionsSeconds.first()
      },
    )
  }
}