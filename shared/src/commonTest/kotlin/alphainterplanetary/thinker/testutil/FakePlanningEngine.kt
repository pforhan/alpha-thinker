package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase

class FakePlanningEngine : PlanningEngine {
  var recommendedTitle: String = "Recommended"
  var remaining: Int = 0
  val initialQuestions: MutableList<Question> = mutableListOf()
  val followUpQuestions: MutableList<Question> = mutableListOf()
  var initialCalls: MutableList<InitialCall> = mutableListOf()
  var followUpCalls: MutableList<FollowUpCall> = mutableListOf()
  var remainingCalls: MutableList<RemainingCall> = mutableListOf()

  override suspend fun recommendTitle(synopsis: String): String = recommendedTitle

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    initialCalls += InitialCall(editableTitle, synopsis, roundId, phase)
    return initialQuestions
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    followUpCalls += FollowUpCall(synopsis, previousQuestions, roundId, phase)
    return followUpQuestions
  }

  override suspend fun remainingInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
  ): Int {
    remainingCalls += RemainingCall(synopsis, previousQuestions, phase)
    return remaining
  }

  data class InitialCall(
    val editableTitle: String,
    val synopsis: String,
    val roundId: String,
    val phase: Phase,
  )

  data class FollowUpCall(
    val synopsis: String,
    val previousQuestions: List<Question>,
    val roundId: String,
    val phase: Phase,
  )

  data class RemainingCall(
    val synopsis: String,
    val previousQuestions: List<Question>,
    val phase: Phase,
  )
}
