package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question

class FallbackQuestionGenerator(
  private val primary: QuestionGenerator,
  private val fallback: QuestionGenerator = SeedQuestionsGenerator(),
) : QuestionGenerator {
  override suspend fun recommendTitle(synopsis: String): String {
    return try {
      primary.recommendTitle(synopsis)
    } catch (e: Exception) {
      fallback.recommendTitle(synopsis)
    }
  }

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    contextId: String,
  ): List<Question> {
    return try {
      primary.generateInitialQuestions(editableTitle, synopsis, contextId)
    } catch (e: Exception) {
      fallback.generateInitialQuestions(editableTitle, synopsis, contextId)
    }
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    contextId: String,
  ): List<Question> {
    return try {
      primary.generateFollowUpQuestions(synopsis, previousQuestions, contextId)
    } catch (e: Exception) {
      fallback.generateFollowUpQuestions(synopsis, previousQuestions, contextId)
    }
  }
}
