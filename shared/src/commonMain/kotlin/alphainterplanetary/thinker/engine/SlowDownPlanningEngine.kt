package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Decorates a [PlanningEngine] with an optional artificial delay applied
 * before each interaction, driven by [config] (the Testing setting). While the
 * config's [EngineDelayConfig.enabled] is on, each interaction is held for
 * its own chosen [EngineDelayConfig.durationFor] so generation tasks linger
 * on the Task Manager long enough to exercise and inspect its UI and code paths.
 */
class SlowDownPlanningEngine(
  private val delegate: PlanningEngine,
  private val config: StateFlow<EngineDelayConfig>,
) : PlanningEngine {

  override val source: LogSource
    get() = delegate.source

  /** The delegate's answer; delaying an engine doesn't change its window. */
  override val contextWindowTokens: Int?
    get() = delegate.contextWindowTokens

  /** The delegate's answer; delaying an engine adds no capability to it. */
  override val canSummarize: Boolean
    get() = delegate.canSummarize

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    maybeDelay(EngineInteraction.RecommendTitle)
    return delegate.recommendTitle(synopsis, activityId)
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
    maybeDelay(EngineInteraction.QuestionGeneration)
    return delegate.generateQuestions(
      title, synopsis, previousQuestions, roundId, phase, activityId, priorSummaries
    )
  }

  /**
   * The summarize request is delayed with the question generation it feeds, and
   * under the same interaction: it is the same model call the user is waiting
   * on, and a separate setting would only be a second dial for one dial's job.
   */
  override suspend fun summarizePriorAnswers(
    title: String,
    synopsis: String,
    phase: Phase,
    transcript: String,
    activityId: String,
  ): String {
    maybeDelay(EngineInteraction.QuestionGeneration)
    return delegate.summarizePriorAnswers(title, synopsis, phase, transcript, activityId)
  }

  private suspend fun maybeDelay(interaction: EngineInteraction) {
    config.value.durationFor(interaction)?.let { duration ->
      delay(duration)
    }
  }
}