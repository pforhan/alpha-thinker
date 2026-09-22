package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlin.coroutines.cancellation.CancellationException

/**
 * The unified contract for the engine that produces planning content — initial
 * and follow-up question rounds, recommended titles, and phase-availability
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
  ): List<Question>

  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): List<Question>

  /**
   * How many questions the engine could still produce for [phase] without
   * repeating [previousQuestions] (which spans the whole project). A return of
   * zero means the phase is exhausted — "Get more questions" affordances should
   * disable themselves instead of firing a no-op generation round.
   */
  @Throws(AnalysisFailure::class, CancellationException::class)
  suspend fun remainingInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Int

  class AnalysisFailure(override val message: String) : Exception(message)
}