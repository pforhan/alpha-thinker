package alphainterplanetary.thinker.engine

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

  override suspend fun recommendTitle(synopsis: String): String {
    println("SlowDownPlanningEngine.recommendTitle()")
    maybeDelay(EngineInteraction.RecommendTitle)
    return delegate.recommendTitle(synopsis)
  }

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    println("SlowDownPlanningEngine.generateInitialQuestions()")
    maybeDelay(EngineInteraction.InitialQuestions)
    return delegate.generateInitialQuestions(editableTitle, synopsis, roundId, phase)
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    println("SlowDownPlanningEngine.generateFollowUpQuestions()")
    maybeDelay(EngineInteraction.FollowUpQuestions)
    return delegate.generateFollowUpQuestions(synopsis, previousQuestions, roundId, phase)
  }

  override suspend fun remainingInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
  ): Int {
    println("SlowDownPlanningEngine.remainingInPhase()")
    maybeDelay(EngineInteraction.RemainingInPhase)
    return delegate.remainingInPhase(synopsis, previousQuestions, phase)
  }

  private suspend fun maybeDelay(interaction: EngineInteraction) {
    config.value.durationFor(interaction)?.let { duration ->
      delay(duration)
    }
  }
}