package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.repository.SettingsRepository

/**
 * A proxy that delegates to the active [PlanningBackend] based on current settings.
 */
class DynamicPlanningBackend(
  private val settings: SettingsRepository,
  private val backends: Map<EngineMode, PlanningBackend>,
) : PlanningBackend {
  override val executor: PromptExecutor
    get() = currentBackend.executor

  override val model: LLModel
    get() = currentBackend.model

  private val currentBackend: PlanningBackend
    get() = backends[settings.engineMode.value] 
      ?: backends.values.first()
}
