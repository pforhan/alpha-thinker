package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogContext
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlin.coroutines.cancellation.CancellationException

/**
 * The interaction-detail writer half of the app-wide activity log (ENG-DESIGN.md
 * schema item 4, write path): it decorates a [PlanningEngine] and, through a
 * [LogContext] scoped to the caller's activity id, appends an input row (the
 * rendered prompt, or a compact `input:` summary when the delegate doesn't
 * render prompts), then a terminal `response:`/`failed:`/`cancelled` row with
 * the produced payload — joining the [TaskRunner]'s `TaskRun` lifecycle rows for
 * the same activity. The [LogCategory] is chosen per interaction and [LogSource]
 * reflects whichever engine actually ran.
 *
 * The terminal row also carries the delegate's verbatim model reply as its
 * [LogEntry.raw] (captured through [capturingRawResponses]), so a reply that
 * couldn't be parsed survives the failure that it caused — see
 * [RawResponseCapture] for why the text has to travel that way.
 */
class LoggingPlanningEngine(
  private val delegate: PlanningEngine,
  private val log: ActivityLogger,
) : PlanningEngine {

  override val source: LogSource
    get() = delegate.source

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    val context = log.context(activityId, LogCategory.TitleRecommendation, source)
    filePrompt(context, (delegate as? PromptRenderer)?.titlePrompt(synopsis), "synopsis=$synopsis")
    val raw = mutableListOf<String>()
    return try {
      val title = capturingRawResponses({ raw += it }) {
        delegate.recommendTitle(synopsis, activityId)
      }
      context.response(title, raw.joinedRawResponse())
      title
    } catch (e: CancellationException) {
      context.closeCancelled()
      throw e
    } catch (e: Exception) {
      context.closeFailed(e.message ?: e.toString(), raw.joinedRawResponse())
      throw e
    }
  }

  override suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    val context = log.context(activityId, LogCategory.QuestionGeneration, source)
    filePrompt(
      context,
      (delegate as? PromptRenderer)?.questionsPrompt(title, synopsis, previousQuestions, phase),
      "phase=$phase, synopsis=$synopsis, previous questions=${previousQuestions.size}",
    )
    val raw = mutableListOf<String>()
    return try {
      val batch = capturingRawResponses({ raw += it }) {
        delegate.generateQuestions(
          title, synopsis, previousQuestions, roundId, phase, activityId
        )
      }
      context.response(batchResponse(batch), raw.joinedRawResponse())
      batch
    } catch (e: CancellationException) {
      context.closeCancelled()
      throw e
    } catch (e: Exception) {
      context.closeFailed(e.message ?: e.toString(), raw.joinedRawResponse())
      throw e
    }
  }

  /** A rendered prompt rows as `prompt:` verbatim; otherwise a compact `input:` summary. */
  private suspend fun filePrompt(context: LogContext, prompt: String?, fallback: String) {
    if (prompt != null) context.prompt(prompt) else context.input(fallback)
  }

  private fun batchResponse(batch: QuestionBatch): String = buildString {
    val count = batch.questions.size
    append(if (count == 1) "1 question" else "$count questions")
    append(", done=${batch.done}")
    batch.questions.forEach { question ->
      append("\n• ")
      append(question.text)
    }
  }
}