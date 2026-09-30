package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.PlanningContext.PhaseSummary
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlin.coroutines.cancellation.CancellationException

/**
 * The result of one question-generation interaction: the produced [questions]
 * plus the engine's explicit [QuestionBatch.done] "stop condition" — "I'm done
 * with this phase". Carrying [done] openly means exhaustion is never inferred
 * from an empty question list (an `emptyList()` is indistinguishable from
 * "nothing surfaced yet" or a soft failure); the hardcoded engine reports done
 * once its phase pool is exhausted, a remote LLM backend reports it when it
 * decides it has nothing more to produce.
 */
data class QuestionBatch(
  val questions: List<Question>,
  val done: Boolean,
)

/**
 * The log line for a produced batch: `N questions, done=<flag>`, then one line
 * per question. Both writers of that row phrase it here — the request that
 * generated it, and the [LoggingPlanningEngine] fallback for an engine that
 * generated nothing — and the activity's collapsed headline parses the count
 * off that same text, so the phrasing is one contract rather than two.
 */
fun QuestionBatch.summary(): String = buildString {
  val count = questions.size
  append(if (count == 1) "1 question" else "$count questions")
  append(", done=$done")
  questions.forEach { question ->
    append("\n• ")
    append(question.text)
  }
}

/**
 * The unified contract for the engine that produces planning content — question
 * rounds and recommended titles. A [PlanningEngine] is invoked statelessly with
 * the project context it needs, so the same call shape works across every
 * backend: a local edge LLM, a remote HTTP/cloud model, or the hardcoded Lite
 * fallback ([HardcodedPlanningEngine]).
 *
 * There is one question-generation call rather than one per kind of round, so
 * every round is generated the same way and an engine never has to be told
 * which round it is filling. That works because the context already says it:
 * [generateQuestions]'s [previousQuestions] is empty only for a brand-new
 * project, and populated for both a "Get more questions" round and the opening
 * round of a later phase (a wrap-up advance hands the engine the whole
 * transcript). The round's own history lives in `RoundOrigin` and
 * `RoundOutcome`, not in the engine contract.
 *
 * Phase exhaustion is not asked of the engine: it is answered by the explicit
 * [QuestionBatch.done] each batch already carries, which the repository latches
 * onto the round it generated (see `RoundOutcome`). A speculative capability
 * probe could only ever say "yes" for an LLM backend, and a wrong "no" would
 * strand a phase, so there is no such call here.
 *
 * Each interaction is intended to be recorded on the engine activity log (see
 * ENG-DESIGN.md "Core Data Schema") so the System/Debug workspace can show what
 * ran, through which engine, and (for inference) any nested tool calls it made.
 * An LLM-backed engine does that itself, one row pair per request, by reporting
 * into the ambient [LogScope] its caller installed — it has no logger and no
 * activity id of its own, and the seam is absent when it is called directly.
 *
 * [activityId] threads a generation task's id into every call so the
 * `LoggingPlanningEngine` decorator's interaction-detail rows group under the
 * same activity as the task-runner lifecycle rows. It is required — every
 * interaction today originates inside a task, so an untagged call is a bug
 * (it would otherwise spawn an orphan activity in the log).
 *
 * [summarizePriorAnswers] is the one optional interaction: only a
 * model-backed engine can compress a phase's answers, and
 * [PlanningEngine.canSummarize] says so before the user is offered the choice.
 * It is a separate call rather than a flag on [generateQuestions] because it is
 * a different job on a different unit — one finished phase, not the next round
 * — and because a project may need several of them, one per phase, in the same
 * task.
 *
 * [contextWindowTokens] is the other thing an engine has to say about itself,
 * and it comes first: an engine that draws its output from a fixed library
 * rather than a model ([HardcodedPlanningEngine]) never puts the interview in a
 * prompt, so nothing the user has written is ever a payload, there is no window
 * to overrun, and the whole context-budget apparatus — the measurement, the
 * near-limit question, the compaction, the phase summaries — is skipped for it
 * rather than performed and thrown away. It is required rather than defaulted
 * so no engine can be added without answering it.
 *
 * [canSummarize] is the narrower second fact, and only has meaning once there
 * is a window: nothing to condense without one.
 */
interface PlanningEngine {
  /** The producer this engine reports on the activity log (e.g. [LogSource.RemoteLLM] for a Koog-backed mode, [LogSource.Lite] for the built-in fallback). */
  val source: LogSource

  /**
   * The generation prompt's context window in tokens, or null when the engine
   * has none to overrun.
   *
   * A model's is a fact about the model, not a preference, and it is read from
   * the model rather than asked of the user: the app's budget is a *share* of
   * this, so the same setting means "half the window" on a 4k edge model and on
   * a 200k hosted one. Koog carries it on the model it is handed, so a
   * registry-known model reports it; a user-configured OpenAI-compatible model
   * is a bare id and cannot, which is why the remote connection fields carry a
   * window of their own and feed this.
   *
   * False-by-default is not an option here — the null case is the Lite engine
   * saying it composes no prompt at all, and defaulting would let an engine
   * forget to say so and have its answers trimmed for a window that does not
   * exist.
   */
  val contextWindowTokens: Int?

  /**
   * Whether this engine can write a [PhaseSummary] for a past phase
   * ([summarizePriorAnswers]). False by default — an engine that can't would
   * otherwise be offered a choice it can only fail at. Only meaningful when
   * [contextWindowTokens] is set: an engine with no window has nothing to
   * condense.
   */
  val canSummarize: Boolean
    get() = false

  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun recommendTitle(synopsis: String, activityId: String): String

  /**
   * A batch of questions for [roundId]'s phase, grounded in the project so far.
   * [previousQuestions] is every question the project has already asked,
   * carrying its answer state — empty only for a project's very first round.
   * [priorSummaries] are the phases' answers already replaced by model-written
   * summaries (see [PlanningContext.summarizablePhases]); they are rendered
   * ahead of the transcript, and the questions they cover read as summarized
   * rather than omitted.
   */
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
    priorSummaries: List<PhaseSummary> = emptyList(),
  ): QuestionBatch

  /**
   * [transcript] — one past phase's rendered Q&A (see
   * [PlanningContext.PhaseTranscript]) — condensed to the handful of decisions
   * and findings a later round needs in order to avoid re-asking what is
   * already settled. Returns the summary text; the caller pairs it with the
   * phase and the question ids it covers as a [PhaseSummary].
   *
   * Defaults to refusing, so an engine that hasn't taught to write summaries
   * (or can't) fails loudly if it is asked rather than silently sending back
   * something the model never said.
   */
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun summarizePriorAnswers(
    title: String,
    synopsis: String,
    phase: Phase,
    transcript: String,
    activityId: String,
  ): String = throw AnalysisFailure("This engine can't summarize earlier phases")

  class AnalysisFailure(override val message: String) : Exception(message)
}
