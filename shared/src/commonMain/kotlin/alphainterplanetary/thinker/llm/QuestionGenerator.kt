package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question

interface QuestionGenerator {
  @Throws(AnalysisFailure::class)
  suspend fun recommendTitle(synopsis: String): String

  @Throws(AnalysisFailure::class)
  suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    contextId: String,
  ): List<Question>

  @Throws(AnalysisFailure::class)
  suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    contextId: String,
  ): List<Question>

  class AnalysisFailure(override val message: String) : Exception(message)
}
