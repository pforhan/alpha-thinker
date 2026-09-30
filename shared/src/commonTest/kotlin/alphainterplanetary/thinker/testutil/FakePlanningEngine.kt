package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.PlanningContext
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

  /** The window a budget is a share of; null means the engine composes no prompt. */
  override var contextWindowTokens: Int? = 8192

  /** What the engine reports it can do; drives whether the summarize choice is offered. */
  override var canSummarize: Boolean = false

  /** The summary returned for [summarizePriorAnswers], keyed by phase. */
  val summaries: MutableMap<Phase, String> = mutableMapOf()
  val summarizeCalls: MutableList<SummarizeCall> = mutableListOf()

  override var source: LogSource = LogSource.Lite

  override suspend fun recommendTitle(synopsis: String, activityId: String): String = recommendedTitle

  override suspend fun summarizePriorAnswers(
    title: String,
    synopsis: String,
    phase: Phase,
    transcript: String,
    activityId: String,
  ): String {
    summarizeCalls += SummarizeCall(title, synopsis, phase, transcript, activityId)
    return summaries[phase] ?: "Summary of ${phase.label}"
  }

  override suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
    priorSummaries: List<PlanningContext.PhaseSummary>,
  ): QuestionBatch {
    calls += Call(title, synopsis, previousQuestions, roundId, phase, priorSummaries)
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
    val priorSummaries: List<PlanningContext.PhaseSummary> = emptyList(),
  )

  /** One recorded summarize request, carrying the phase transcript it was asked about. */
  data class SummarizeCall(
    val editableTitle: String,
    val synopsis: String,
    val phase: Phase,
    val transcript: String,
    val activityId: String,
  )
}
