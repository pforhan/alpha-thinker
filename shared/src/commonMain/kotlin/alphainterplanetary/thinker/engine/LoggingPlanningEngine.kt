package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.ActivityLog
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogEntry
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.now
import kotlin.coroutines.cancellation.CancellationException

/**
 * The interaction-detail writer half of the app-wide activity log (ENG-DESIGN.md
 * schema item 4, write path): it decorates a [PlanningEngine] and appends an
 * input row (the rendered prompt, or a compact `input:` summary when the
 * delegate doesn't render prompts), then a terminal `response:`/`error:`/
 * `cancelled` row with the produced payload — both grouped under the caller's
 * [PlanningEngine activityId] (a generation task id), where they join the
 * `TaskRunner`'s `TaskRun` rows for the same activity. The [LogCategory] is
 * chosen per interaction and [LogSource] reflects whichever engine actually ran.
 */
class LoggingPlanningEngine(
  private val delegate: PlanningEngine,
  private val log: ActivityLog,
) : PlanningEngine {

  override val source: LogSource
    get() = delegate.source

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    append(
      category = LogCategory.TitleRecommendation,
      activityId = activityId,
      text = input((delegate as? PromptRenderer)?.titlePrompt(synopsis)) { "input: synopsis=$synopsis" },
    )
    return try {
      val title = delegate.recommendTitle(synopsis, activityId)
      append(category = LogCategory.TitleRecommendation, activityId = activityId, text = "response: $title")
      title
    } catch (e: CancellationException) {
      append(category = LogCategory.TitleRecommendation, activityId = activityId, text = "cancelled")
      throw e
    } catch (e: Exception) {
      append(category = LogCategory.TitleRecommendation, activityId = activityId, text = error(e))
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
    append(
      category = LogCategory.QuestionGeneration,
      activityId = activityId,
      text = input((delegate as? PromptRenderer)?.initialQuestionsPrompt(editableTitle, synopsis, phase)) {
        "input: phase=$phase, synopsis=$synopsis"
      },
    )
    return try {
      val batch = delegate.generateInitialQuestions(editableTitle, synopsis, roundId, phase, activityId)
      append(
        category = LogCategory.QuestionGeneration,
        activityId = activityId,
        text = "response: ${batchResponse(batch)}",
      )
      batch
    } catch (e: CancellationException) {
      append(category = LogCategory.QuestionGeneration, activityId = activityId, text = "cancelled")
      throw e
    } catch (e: Exception) {
      append(category = LogCategory.QuestionGeneration, activityId = activityId, text = error(e))
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
    append(
      category = LogCategory.QuestionGeneration,
      activityId = activityId,
      text = input((delegate as? PromptRenderer)?.followUpQuestionsPrompt(synopsis, previousQuestions, phase)) {
        "input: phase=$phase, previous questions=${previousQuestions.size}"
      },
    )
    return try {
      val batch = delegate.generateFollowUpQuestions(
        synopsis, previousQuestions, roundId, phase, activityId
      )
      append(
        category = LogCategory.QuestionGeneration,
        activityId = activityId,
        text = "response: ${batchResponse(batch)}",
      )
      batch
    } catch (e: CancellationException) {
      append(category = LogCategory.QuestionGeneration, activityId = activityId, text = "cancelled")
      throw e
    } catch (e: Exception) {
      append(category = LogCategory.QuestionGeneration, activityId = activityId, text = error(e))
      throw e
    }
  }

  override suspend fun canProduceMoreInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Boolean {
    append(
      category = LogCategory.CapabilityCheck,
      activityId = activityId,
      text = "input: phase=$phase, previous questions=${previousQuestions.size}",
    )
    return try {
      val can = delegate.canProduceMoreInPhase(synopsis, previousQuestions, phase, activityId)
      append(category = LogCategory.CapabilityCheck, activityId = activityId, text = "response: canProduceMore=$can")
      can
    } catch (e: CancellationException) {
      append(category = LogCategory.CapabilityCheck, activityId = activityId, text = "cancelled")
      throw e
    } catch (e: Exception) {
      append(category = LogCategory.CapabilityCheck, activityId = activityId, text = error(e))
      throw e
    }
  }

  private fun input(prompt: String?, fallback: () -> String): String =
    if (prompt != null) "prompt: $prompt" else fallback()

  private fun error(e: Exception): String = "error: ${e.message ?: e.toString()}"

  private fun batchResponse(batch: QuestionBatch): String = buildString {
    val count = batch.questions.size
    append(if (count == 1) "1 question" else "$count questions")
    append(", done=${batch.done}")
    batch.questions.forEach { question ->
      append("\n• ")
      append(question.text)
    }
  }

  private suspend fun append(
    category: LogCategory,
    activityId: String,
    text: String,
  ) {
    log.append(
      LogEntry(
        projectId = null,
        activityId = activityId,
        category = category,
        source = this.source,
        log = text,
        timestamp = now(),
      )
    )
  }
}