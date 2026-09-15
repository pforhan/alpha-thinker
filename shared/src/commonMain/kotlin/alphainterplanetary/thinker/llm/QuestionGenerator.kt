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

  class AnalysisFailure(override val message: String) : Exception(message)
}
