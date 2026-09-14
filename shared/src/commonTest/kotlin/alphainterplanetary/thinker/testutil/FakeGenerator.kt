package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.model.Question

class FakeGenerator : QuestionGenerator {
  var recommendedTitle: String = "Recommended"
  val initialQuestions: MutableList<Question> = mutableListOf()
  val followUpQuestions: MutableList<Question> = mutableListOf()
  var initialCalls: MutableList<InitialCall> = mutableListOf()
  var followUpCalls: MutableList<FollowUpCall> = mutableListOf()

  override suspend fun recommendTitle(synopsis: String): String = recommendedTitle

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
  ): List<Question> {
    initialCalls += InitialCall(editableTitle, synopsis, roundId)
    return initialQuestions
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
  ): List<Question> {
    followUpCalls += FollowUpCall(synopsis, previousQuestions, roundId)
    return followUpQuestions
  }

  data class InitialCall(
    val editableTitle: String,
    val synopsis: String,
    val roundId: String,
  )

  data class FollowUpCall(
    val synopsis: String,
    val previousQuestions: List<Question>,
    val roundId: String,
  )
}
