package alphainterplanetary.thinker.engine

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
 * The unified contract for the engine that produces planning content — initial
 * and follow-up question rounds, recommended titles, and phase-exhaustion
 * checks. A [PlanningEngine] is invoked statelessly with the project context
 * it needs, so the same call shape works across every backend: a local edge
 * LLM, a remote HTTP/cloud model, or the hardcoded Lite fallback
 * ([HardcodedPlanningEngine]).
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
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun recommendTitle(synopsis: String, activityId: String): String

  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch

  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch

  /**
   * Whether the engine could still produce questions for [phase] without
   * repeating [previousQuestions] (which spans the whole project). A boolean
   * capability answer, deliberately not a count: a remote LLM backend cannot
   * number its unasked pool precisely, so it answers the question it *can*
   * answer truthfully ("could you still produce a fresh question for this
   * phase?") — and that same answer always permits "yes". A `false` answer
   * means the phase is exhausted, so "Get more questions" affordances disable
   * themselves instead of firing a no-op generation round.
   */
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun canProduceMoreInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Boolean

  class AnalysisFailure(override val message: String) : Exception(message)
}