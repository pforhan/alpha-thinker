package com.pforhan.alphathinker.llm

import com.pforhan.alphathinker.model.Question

interface LLMIntegration {
  @Throws(AnalysisFailure::class)
  suspend fun generateInitialQuestions(synopsis: String): List<Question>

  @Throws(AnalysisFailure::class)
  suspend fun generateFollowUpQuestions(synopsis: String): List<Question>

  class AnalysisFailure(override val message: String) : Exception(message)
}
