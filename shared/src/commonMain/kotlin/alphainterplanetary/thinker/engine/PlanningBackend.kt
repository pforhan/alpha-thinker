package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Couples a [PromptExecutor] and [LLModel] for a specific inference backend.
 */
interface PlanningBackend {
  val executor: PromptExecutor
  val model: LLModel
}
