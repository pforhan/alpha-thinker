package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The interaction-detail writer half of the app-wide activity log (ENG-DESIGN.md
 * schema item 4, write path): it decorates a [PlanningEngine] and gives each
 * interaction an ambient [LogScope] under the caller's activity id, so an
 * LLM-backed engine records its own requests — one `prompt:` row and one
 * terminal `response:`/`failed:` row each, the latter carrying the verbatim model
 * reply — joining the [alphainterplanetary.thinker.tasks.TaskRunner]'s `TaskRun`
 * lifecycle rows for the same activity.
 *
 * What it still files itself is the interaction's *own* outcome row, and only
 * when the delegate reported no request of its own
 * ([LogScope.fallback]): the hardcoded Lite engine never speaks to a model, so
 * there is nothing for it to file and its result would otherwise never reach the
 * log. An engine that did report gets exactly one row pair per request and no
 * second, duplicate summary.
 *
 * [LogCategory] is chosen per interaction and [LogSource] reflects whichever
 * engine actually ran. Other decorators need no change: a plain `suspend`
 * delegation (as [SlowDownPlanningEngine] does) carries the scope through.
 */
class LoggingPlanningEngine(
  private val delegate: PlanningEngine,
  private val log: ActivityLogger,
) : PlanningEngine {

  override val source: LogSource
    get() = delegate.source

  override suspend fun recommendTitle(synopsis: String, activityId: String): String =
    logged(
      activityId = activityId,
      category = LogCategory.TitleRecommendation,
      summary = { title -> title },
    ) {
      delegate.recommendTitle(synopsis, activityId)
    }

  override suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch =
    logged(
      activityId = activityId,
      category = LogCategory.QuestionGeneration,
      summary = { batch -> batch.summary() },
    ) {
      delegate.generateQuestions(title, synopsis, previousQuestions, roundId, phase, activityId)
    }

  /**
   * Runs one interaction with its [LogScope] installed, then files the
   * interaction's outcome row — through the scope, so it is skipped the moment a
   * request has filed one of its own.
   */
  private suspend fun <T> logged(
    activityId: String,
    category: LogCategory,
    summary: (T) -> String,
    block: suspend () -> T,
  ): T {
    val scope = LogScope { log.context(activityId, category, source) }
    return try {
      val result = withContext(scope) { block() }
      scope.fallback()?.response(summary(result))
      result
    } catch (e: CancellationException) {
      scope.fallback()?.closeCancelled()
      throw e
    } catch (e: Exception) {
      scope.fallback()?.closeFailed(e.message ?: e.toString())
      throw e
    }
  }
}
