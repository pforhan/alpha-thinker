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

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    println("SlowDownPlanningEngine.recommendTitle()")
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
  ): QuestionBatch {
    println("SlowDownPlanningEngine.generateQuestions()")
    maybeDelay(EngineInteraction.QuestionGeneration)
    return delegate.generateQuestions(
      title, synopsis, previousQuestions, roundId, phase, activityId
    )
  }

  private suspend fun maybeDelay(interaction: EngineInteraction) {
    config.value.durationFor(interaction)?.let { duration ->
      delay(duration)
    }
  }
}