package com.pforhan.alphathinker.llm

import com.pforhan.alphathinker.model.Question

class FallbackLLMIntegration(
  private val primary: LLMIntegration,
  private val fallback: LLMIntegration = SeedQuestionsLLMIntegration(),
) : LLMIntegration {
  override suspend fun generateInitialQuestions(synopsis: String): List<Question> {
    return try {
      primary.generateInitialQuestions(synopsis)
    } catch (e: Exception) {
      fallback.generateInitialQuestions(synopsis)
    }
  }

  override suspend fun generateFollowUpQuestions(synopsis: String): List<Question> {
    return try {
      primary.generateFollowUpQuestions(synopsis)
    } catch (e: Exception) {
      fallback.generateFollowUpQuestions(synopsis)
    }
  }
}
