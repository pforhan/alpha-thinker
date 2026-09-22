package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * [PlanningBackend] implementation that uses the system's on-device LLM.
 */
class OnDevicePlanningBackend(
    client: OnDeviceLLMClient
) : PlanningBackend {
    override val model: LLModel = client.getModel()
    override val executor: PromptExecutor = MultiLLMPromptExecutor(client.asKoogClient())
}
