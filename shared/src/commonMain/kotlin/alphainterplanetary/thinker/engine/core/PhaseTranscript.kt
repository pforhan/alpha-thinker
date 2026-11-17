package alphainterplanetary.thinker.engine.core

import alphainterplanetary.thinker.model.PhaseQuestions
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.TokenEstimator

/**
 * One phase's rendered, summarizable content: the questions covered, the
 * transcript to hand the model, and its estimated token cost.
 *
 * Serves as the shared input unit for prompt compaction and export/background
 * summarization; pair with [toSummarizedPhase] to produce a prompt/context-ready
 * [SummarizedPhase].
 */
data class PhaseTranscript(
  val phase: Phase,
  val questions: List<Question>,
  val transcript: String,
  val estimatedTokens: Int,
) {
  /** The questions a summary of this phase would cover. */
  val coveredQuestionIds: Set<String>
    get() = questions.mapTo(mutableSetOf()) { it.id }

  /**
   * Stable fingerprint of this transcript's covered content, used as a cache-key
   * component so a cached summary is reused only when the summarized content is unchanged.
   */
  val coveredContentHash: String
    get() = hashCoveredContent(transcript)

  /** Builds a [SummarizedPhase] for [summary] covering this transcript. */
  fun toSummarizedPhase(summary: String): SummarizedPhase =
    SummarizedPhase(
      phase = phase,
      summary = summary,
      coversQuestionIds = coveredQuestionIds,
    )

  companion object {
    /**
     * Renders [content]'s summarizable questions into a [PhaseTranscript], or
     * null when there is no summarizable content or the rendered transcript is blank.
     */
    fun from(
      content: PhaseQuestions,
      render: (List<Question>) -> String,
    ): PhaseTranscript? {
      val questions = content.summarizableQuestions
      if (questions.isEmpty()) return null
      val transcript = render(questions)
      if (transcript.isBlank()) return null
      return PhaseTranscript(
        phase = content.phase,
        questions = questions,
        transcript = transcript,
        estimatedTokens = TokenEstimator.estimateTokens(transcript),
      )
    }

    /** Stable FNV-1a 64-bit fingerprint of rendered phase transcript content. */
    fun hashCoveredContent(text: String): String {
      var hash = -0x340d631b7bdddcdbL // 14695981039346656037UL offset basis
      for (ch in text) {
        hash = hash xor ch.code.toLong()
        hash *= 0x100000001b3L // 1099511628211 prime
      }
      return hash.toULong().toString(16).padStart(16, '0')
    }
  }
}

/**
 * The project's phases with summarizable content, oldest first, as rendered
 * [PhaseTranscript]s. [includeCurrentPhase] controls whether the phase currently
 * in progress is included.
 */
fun Project.phaseTranscripts(
  includeCurrentPhase: Boolean = true,
  render: (List<Question>) -> String = PlanningContext::render,
): List<PhaseTranscript> {
  val current = currentPhase
  return allPhaseContent()
    .sortedBy { it.phase.order }
    .asSequence()
    .filter { includeCurrentPhase || it.phase != current }
    .mapNotNull { PhaseTranscript.from(it, render) }
    .toList()
}
