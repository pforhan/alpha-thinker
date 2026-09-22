package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.repository.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * A [PlanningEngine] that dynamically delegates to the currently active backend
 * based on the app's [SettingsRepository] (selected [EngineMode] and LLM toggle).
 *
 * This ensures that changing the engine mode in settings takes effect immediately
 * for the next interaction without requiring an app restart.
 */
class DynamicPlanningEngine(
  private val settingsRepository: SettingsRepository,
  private val liteEngine: PlanningEngine,
  private val koogEngine: PlanningEngine,
) : PlanningEngine {

  private fun currentEngine(): PlanningEngine {
    val mode = settingsRepository.engineMode.value
    val isLlmEnabled = settingsRepository.llmEnabled.value

    return if (isLlmEnabled && mode != EngineMode.Lite) {
      koogEngine
    } else {
      liteEngine
    }
  }

  override suspend fun recommendTitle(synopsis: String, activityId: String): String =
    currentEngine().recommendTitle(synopsis, activityId)

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch =
    currentEngine().generateInitialQuestions(editableTitle, synopsis, roundId, phase, activityId)

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch =
    currentEngine().generateFollowUpQuestions(synopsis, previousQuestions, roundId, phase, activityId)

  override suspend fun canProduceMoreInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Boolean =
    currentEngine().canProduceMoreInPhase(synopsis, previousQuestions, phase, activityId)
}
