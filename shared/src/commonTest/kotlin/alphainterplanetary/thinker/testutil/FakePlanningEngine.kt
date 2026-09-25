package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.QuestionBatch
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase

class FakePlanningEngine : PlanningEngine {
  var recommendedTitle: String = "Recommended"
  var initialDone: Boolean = false
  var followUpDone: Boolean = false
  /** Thrown from question generation when set (a [kotlinx.coroutines.CancellationException] to simulate cancellation). */
  var generationFailure: Throwable? = null
  val initialQuestions: MutableList<Question> = mutableListOf()
  val followUpQuestions: MutableList<Question> = mutableListOf()
  var initialCalls: MutableList<InitialCall> = mutableListOf()
  var followUpCalls: MutableList<FollowUpCall> = mutableListOf()

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
    generationFailure?.let { throw it }
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
    generationFailure?.let { throw it }
    return QuestionBatch(followUpQuestions, followUpDone)
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
}
