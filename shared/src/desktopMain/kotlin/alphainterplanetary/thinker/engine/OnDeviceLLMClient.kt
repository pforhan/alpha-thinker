package alphainterplanetary.thinker.engine

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message.Assistant

/**
 * No-op on-device LLM client for platforms without system-provided models.
 */
actual class OnDeviceLLMClient : PlanningLLMClient {
  private val koogClient = object : LLMClient() {
    override suspend fun execute(
      prompt: Prompt,
      model: LLModel,
      tools: List<ToolDescriptor>,
    ): Assistant {
      throw NotImplementedError("On-device LLM not available on this platform")
    }

    override suspend fun moderate(
      prompt: Prompt,
      model: LLModel,
    ): ModerationResult {
      throw NotImplementedError("On-device LLM not available on this platform")
    }

    override fun llmProvider(): LLMProvider = LLMProvider.Ollama // a lie, just a noop
    override fun close() {}
  }

  actual override val available: Boolean = false

  actual override fun getModel(): LLModel = LLModel(
    id = "no-op",
    provider = LLMProvider.Ollama, // a lie, just a noop
    capabilities = emptyList(),
    contextLength = 0,
    maxOutputTokens = 0
  )

  actual override fun asKoogClient(): LLMClient = koogClient
}
