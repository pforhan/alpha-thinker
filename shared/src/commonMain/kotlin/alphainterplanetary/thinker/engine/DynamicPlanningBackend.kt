package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.activitylog.LogCategory

/**
 * A [PlanningBackend] bound to a single [EngineMode]: it serves the backend
 * wired for that mode and throws when the mode has none, so a misconfigured
 * engine fails loudly instead of silently borrowing a different backend.
 *
 * Being mode-bound (rather than re-reading the app's live settings) is what
 * lets a generation task keep the engine it was enqueued under: one of these
 * is built per selectable mode and handed to a [KoogPlanningEngine], and the
 * outer selection ([PlanningEngineSelector]) freezes which one a task runs.
 * The bound [EngineMode] also drives the [logCategory] an engine over this backend
 * reports on the activity log.
 */
class DynamicPlanningBackend(
  private val mode: EngineMode,
  private val backends: Map<EngineMode, PlanningBackend>,
) : PlanningBackend {

  override val logCategory: LogCategory
    get() = mode.logCategory

  override val executor: PromptExecutor
    get() = backend().executor

  override val model: LLModel
    get() = backend().model

  private fun backend(): PlanningBackend =
    backends[mode] ?: throw IllegalStateException("No backend bound for engine mode $mode")
}