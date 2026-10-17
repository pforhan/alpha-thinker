package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.activitylog.LogSource

/**
 * Couples a [PromptExecutor] and [LLModel] for a specific inference backend,
 * along with the [LogSource] an engine built over it reports on the activity
 * log (local edge vs remote/cloud).
 */
interface KoogPlanningBackend {
  val executor: PromptExecutor
  val model: LLModel
  val source: LogSource

  /**
   * Releases whatever the backend owns — a remote client's connection pool,
   * say. A backend that holds nothing has nothing to release, so the default is
   * a no-op rather than a declaration every in-memory backend would have to
   * repeat.
   */
  fun close() {}
}
