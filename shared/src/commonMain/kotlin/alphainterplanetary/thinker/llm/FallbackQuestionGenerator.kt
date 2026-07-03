package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question

class FallbackQuestionGenerator(
  private val primary: QuestionGenerator,
  private val fallback: QuestionGenerator = SeedQuestionsGenerator(),
) : QuestionGenerator {
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
