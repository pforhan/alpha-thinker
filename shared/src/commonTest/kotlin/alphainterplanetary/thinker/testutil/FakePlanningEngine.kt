package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.QuestionBatch
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase

class FakePlanningEngine : PlanningEngine {
  var recommendedTitle: String = "Recommended"
  var canProduceMore: Boolean = false
  var initialDone: Boolean = false
  var followUpDone: Boolean = false
  val initialQuestions: MutableList<Question> = mutableListOf()
  val followUpQuestions: MutableList<Question> = mutableListOf()
  var initialCalls: MutableList<InitialCall> = mutableListOf()
  var followUpCalls: MutableList<FollowUpCall> = mutableListOf()
  var canProduceMoreCalls: MutableList<CanProduceMoreCall> = mutableListOf()

  override var source: LogSource = LogSource.Lite

  override suspend fun recommendTitle(synopsis: String, activityId: String): String = recommendedTitle

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    initialCalls += InitialCall(editableTitle, synopsis, roundId, phase)
    return QuestionBatch(initialQuestions, initialDone)
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    followUpCalls += FollowUpCall(synopsis, previousQuestions, roundId, phase)
    return QuestionBatch(followUpQuestions, followUpDone)
  }

  override suspend fun canProduceMoreInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Boolean {
    canProduceMoreCalls += CanProduceMoreCall(synopsis, previousQuestions, phase)
    return canProduceMore
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

  data class CanProduceMoreCall(
    val synopsis: String,
    val previousQuestions: List<Question>,
    val phase: Phase,
  )
}
