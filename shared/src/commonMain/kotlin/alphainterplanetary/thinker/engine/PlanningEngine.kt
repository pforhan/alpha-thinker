package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogSource
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
 *
 * [activityId] threads a generation task's id into every call so the
 * `LoggingPlanningEngine` decorator's interaction-detail rows group under the
 * same activity as the task-runner lifecycle rows. It is required — every
 * interaction today originates inside a task, so an untagged call is a bug
 * (it would otherwise spawn an orphan activity in the log).
 */
interface PlanningEngine {
  /** The producer this engine reports on the activity log (e.g. [LogSource.RemoteLLM] for a Koog-backed mode, [LogSource.Lite] for the built-in fallback). */
  val source: LogSource

  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun recommendTitle(synopsis: String, activityId: String): String

  /**
   * A batch of questions for [roundId]'s phase, grounded in the project so far.
   * [previousQuestions] is every question the project has already asked,
   * carrying its answer state — empty only for a project's very first round.
   */
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch

  class AnalysisFailure(override val message: String) : Exception(message)
}

/**
 * Engines that can render the exact prompt text they send the backend for each
 * planning interaction. The `LoggingPlanningEngine` decorator asks its delegate
 * for this text and records it (prefixing the whole row with `prompt:`) on the
 * detail input row, so the Activity Log viewer can show the full prompt that
 * produced a result (or produced nothing, "why has it stopped"); engines that
 * don't render prompts (the hardcoded Lite engine) simply skip it.
 */
interface PromptRenderer {
  fun titlePrompt(synopsis: String): String

  fun questionsPrompt(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
  ): String
}