package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.activitylog.LogSource

/**
 * Couples a [PromptExecutor] and [LLModel] for a specific inference backend,
 * along with the [LogSource] an engine built over it reports on the activity
 * log (local edge vs remote/cloud).
 */
interface PlanningBackend {
  val executor: PromptExecutor
  val model: LLModel
  val source: LogSource
}
