package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question

class FallbackQuestionGenerator(
  private val primary: QuestionGenerator,
  private val fallback: QuestionGenerator = SeedQuestionsGenerator(),
) : QuestionGenerator {
  override suspend fun generateInitialQuestions(synopsis: String, contextId: String): List<Question> {
    return try {
      primary.generateInitialQuestions(synopsis, contextId)
    } catch (e: Exception) {
      fallback.generateInitialQuestions(synopsis, contextId)
    }
  }

  override suspend fun generateFollowUpQuestions(synopsis: String, contextId: String): List<Question> {
    return try {
      primary.generateFollowUpQuestions(synopsis, contextId)
    } catch (e: Exception) {
      fallback.generateFollowUpQuestions(synopsis, contextId)
    }
  }
}
