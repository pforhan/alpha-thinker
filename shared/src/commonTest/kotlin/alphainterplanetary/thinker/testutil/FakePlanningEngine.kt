package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.QuestionBatch
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase

class FakePlanningEngine : PlanningEngine {
  var recommendedTitle: String = "Recommended"
  var done: Boolean = false
  /** Thrown from question generation when set (a [kotlinx.coroutines.CancellationException] to simulate cancellation). */
  var generationFailure: Throwable? = null
  val questions: MutableList<Question> = mutableListOf()
  var calls: MutableList<Call> = mutableListOf()

  override var source: LogSource = LogSource.Lite

  override suspend fun recommendTitle(synopsis: String, activityId: String): String = recommendedTitle

  override suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    calls += Call(title, synopsis, previousQuestions, roundId, phase)
    generationFailure?.let { throw it }
    return QuestionBatch(questions, done)
  }

  /** One recorded call, carrying the whole context the engine was handed. */
  data class Call(
    val editableTitle: String,
    val synopsis: String,
    val previousQuestions: List<Question>,
    val roundId: String,
    val phase: Phase,
  )
}
