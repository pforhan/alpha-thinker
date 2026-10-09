package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.QuestionBatch
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase

/**
 * The general-purpose [PlanningEngine] double, and the base every hand-rolled
 * double in the suite extends: it owns the three self-describing properties the
 * engine contract forces everyone to declare — [source],
 * [contextWindowTokens], and [canSummarize] — as constructor parameters, so a
 * subclass states only the interaction it actually scripts.
 *
 * A test scripts behavior through the vars below: [recommendedTitle], [done],
 * [questions], [batchFor], [summaries], and [generationFailure]; it reads back
 * [calls], [titleCalls], and [summarizeCalls].
 */
open class FakePlanningEngine(
  override var source: LogSource = LogSource.Lite,
  /** The window a budget is a share of; null means the engine composes no prompt. */
  override var contextWindowTokens: Int? = 8192,
  /** What the engine reports it can do; drives whether the summarize choice is offered. */
  override var canSummarize: Boolean = false,
) : PlanningEngine {
  var recommendedTitle: String = "Recommended"
  var done: Boolean = false

  /** Thrown from question generation when set (a [kotlinx.coroutines.CancellationException] to simulate cancellation). */
  var generationFailure: Throwable? = null
  val questions: MutableList<Question> = mutableListOf()
  var calls: MutableList<Call> = mutableListOf()

  /** How many times [recommendTitle] was invoked. */
  var titleCalls: Int = 0
    private set

  /**
   * Supplies a call's batch instead of [questions] when set, which is what a
   * caller generating more than one round needs: a fixed [questions] list is
   * deduped away by the second round and fails as a dead end, whereas a caller
   * that varies by call gets fresh text every round.
   *
   * Handed the call's index (counting from zero) and its [Call.roundId] — the
   * latter because a question's round is what resolves its phase, and a batch
   * built without it lands in no phase at all.
   */
  var batchFor: ((callIndex: Int, roundId: String) -> List<Question>)? = null

  /** The summary returned for [summarizePriorAnswers], keyed by phase. */
  val summaries: MutableMap<Phase, String> = mutableMapOf()
  val summarizeCalls: MutableList<SummarizeCall> = mutableListOf()

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    titleCalls += 1
    return recommendedTitle
  }

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
    val call = Call(title, synopsis, previousQuestions, roundId, phase, priorSummaries)
    calls += call
    generationFailure?.let { throw it }
    val batch = batchFor?.invoke(calls.size - 1, roundId) ?: questions
    return QuestionBatch(batch, done)
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
