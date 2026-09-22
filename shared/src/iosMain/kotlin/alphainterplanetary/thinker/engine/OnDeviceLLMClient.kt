package alphainterplanetary.thinker.engine

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message.Assistant

/**
 * iOS actual for [OnDeviceLLMClient] using a bridge to Apple Foundation Models.
 */
actual class OnDeviceLLMClient : PlanningLLMClient {
  private val koogClient = object : LLMClient() {
    override suspend fun execute(
      prompt: Prompt,
      model: LLModel,
      tools: List<ToolDescriptor>,
    ): Assistant {
      // Bridge to Foundation Models SDK would go here
      throw NotImplementedError("Apple Foundation Models integration not yet wired to native SDK")
    }

    override suspend fun moderate(
      prompt: Prompt,
      model: LLModel,
    ): ModerationResult {
      throw NotImplementedError("Apple Foundation Models moderation not implemented")
    }

    override fun llmProvider(): LLMProvider = LLMProvider.Google // (maybe?)
    override fun close() {}
  }

  actual override val available: Boolean = true

  actual override fun getModel(): LLModel = LLModel(
    id = "apple-foundation-model",
    provider = LLMProvider.Google, // (Maybe?)
    capabilities = emptyList(),
    contextLength = 4096,
    maxOutputTokens = 1024
  )

  actual override fun asKoogClient(): LLMClient = koogClient
}
