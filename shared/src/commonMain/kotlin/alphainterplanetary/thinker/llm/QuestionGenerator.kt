package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlin.coroutines.cancellation.CancellationException

interface QuestionGenerator {
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun recommendTitle(synopsis: String): String

  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
  ): List<Question>

  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
  ): List<Question>

  /**
   * How many questions the generator could still produce for [phase] without
   * repeating [previousQuestions] (which spans the whole project). A return of
   * zero means the phase is exhausted — "Get more questions" affordances should
   * disable themselves instead of firing a no-op generation round.
   */
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun remainingInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
  ): Int

  class AnalysisFailure(override val message: String) : Exception(message)
}
