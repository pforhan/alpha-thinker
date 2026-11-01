package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.activitylog.LogSource

/**
 * The [KoogPlanningBackend] for a model running in process.
 *
 * The platform runtime builds the client and the executor — only Android has a
 * LiteRT implementation today — while this common holder supplies the parts
 * every local backend shares: the model it runs, the local log source, and a
 * [close] that releases whatever the platform client owns.
 */
class LocalKoogPlanningBackend(
  override val executor: PromptExecutor,
  override val model: LLModel,
  private val onClose: () -> Unit = {},
) : KoogPlanningBackend {

  override val source: LogSource = LogSource.LocalLLM

  override fun close() = onClose()
}
