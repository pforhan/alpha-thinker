package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.EngineActivityEvent
import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.activitylog.EngineActivityLog
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.util.now
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The interaction-detail writer half of the engine activity log (ENG-DESIGN.md
 * schema item 4, write path): it decorates a [PlanningEngine] and records a
 * `Created` event with the call's inputs, then a terminal success/failure event
 * with the produced payload and duration — all grouped under the caller's
 * [PlanningEngine activityId] (a generation task id), so they join the
 * `TaskRunner`'s lifecycle rows for the same activity.
 *
 * This is the DI seam future tool-calling engines extend too: a child tool call
 * is recorded as a separate `Lookup` activity whose [EngineActivityEvent.parentActivityId]
 * points back at the requesting inference's id.
 */
class LoggingPlanningEngine(
  private val delegate: PlanningEngine,
  private val log: EngineActivityLog,
) : PlanningEngine {

  override val logCategory: LogCategory
    get() = delegate.logCategory

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    val created = baseEvent(
      kind = TaskKind.TitleRecommendation,
      activityId = activityId,
      parameters = "synopsis=$synopsis",
      promptUsed = (delegate as? PromptRenderer)?.titlePrompt(synopsis),
    )
    log.append(created)
    val start = TimeSource.Monotonic.markNow()
    return try {
      val title = delegate.recommendTitle(synopsis, activityId)
      finish(created, start, generationPayload = title)
      title
    } catch (e: CancellationException) {
      finish(created, start, error = "cancelled")
      throw e
    } catch (e: Exception) {
      finish(created, start, error = e.message ?: e.toString())
      throw e
    }
  }

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    val created = baseEvent(
      kind = TaskKind.InitialQuestions,
      activityId = activityId,
      roundId = roundId,
      parameters = "title=$editableTitle, phase=$phase, synopsis=$synopsis",
      promptUsed = (delegate as? PromptRenderer)?.initialQuestionsPrompt(editableTitle, synopsis, phase),
    )
    log.append(created)
    val start = TimeSource.Monotonic.markNow()
    return try {
      val batch = delegate.generateInitialQuestions(editableTitle, synopsis, roundId, phase, activityId)
      finish(created, start, suggestedQuestions = texts(batch.questions), generationPayload = "done=${batch.done}")
      batch
    } catch (e: CancellationException) {
      finish(created, start, error = "cancelled")
      throw e
    } catch (e: Exception) {
      finish(created, start, error = e.message ?: e.toString())
      throw e
    }
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    val created = baseEvent(
      kind = TaskKind.FollowUpQuestions,
      activityId = activityId,
      roundId = roundId,
      parameters = "phase=$phase, previousQuestions=${previousQuestions.size}",
      promptUsed = (delegate as? PromptRenderer)?.followUpQuestionsPrompt(synopsis, previousQuestions, phase),
    )
    log.append(created)
    val start = TimeSource.Monotonic.markNow()
    return try {
      val batch = delegate.generateFollowUpQuestions(
        synopsis, previousQuestions, roundId, phase, activityId
      )
      finish(created, start, suggestedQuestions = texts(batch.questions), generationPayload = "done=${batch.done}")
      batch
    } catch (e: CancellationException) {
      finish(created, start, error = "cancelled")
      throw e
    } catch (e: Exception) {
      finish(created, start, error = e.message ?: e.toString())
      throw e
    }
  }

  override suspend fun canProduceMoreInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Boolean {
    val created = baseEvent(
      kind = TaskKind.RemainingInPhase,
      activityId = activityId,
      parameters = "phase=$phase, previousQuestions=${previousQuestions.size}",
    )
    log.append(created)
    val start = TimeSource.Monotonic.markNow()
    return try {
      val can = delegate.canProduceMoreInPhase(synopsis, previousQuestions, phase, activityId)
      finish(created, start, generationPayload = can.toString())
      can
    } catch (e: CancellationException) {
      finish(created, start, error = "cancelled")
      throw e
    } catch (e: Exception) {
      finish(created, start, error = e.message ?: e.toString())
      throw e
    }
  }

  private fun baseEvent(
    kind: TaskKind,
    activityId: String,
    roundId: String? = null,
    parameters: String,
    promptUsed: String? = null,
  ): EngineActivityEvent = EngineActivityEvent(
    activityId = activityId,
    roundId = roundId,
    kind = kind,
    logCategory = this.logCategory,
    eventType = EngineActivityEventType.Created,
    parameters = parameters,
    promptUsed = promptUsed,
    timestamp = now(),
  )

  private suspend fun finish(
    created: EngineActivityEvent,
    start: TimeMark,
    error: String? = null,
    generationPayload: String? = null,
    suggestedQuestions: String? = null,
  ) {
    log.append(
      created.copy(
        eventId = null,
        eventType = if (error == null) {
          EngineActivityEventType.Succeeded
        } else {
          EngineActivityEventType.Failed
        },
        error = error,
        generationPayload = generationPayload,
        suggestedQuestions = suggestedQuestions,
        durationMs = start.elapsedNow().inWholeMilliseconds,
        timestamp = now(),
      )
    )
  }

  private fun texts(questions: List<Question>): String = Json.encodeToString(questions.map { it.text })
}