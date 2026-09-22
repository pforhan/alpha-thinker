package alphainterplanetary.thinker.engine

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message.Assistant

/**
 * Android actual for [OnDeviceLLMClient] using ML Kit GenAI / AICore.
 */
actual class OnDeviceLLMClient : PlanningLLMClient {
  private val koogClient = object : LLMClient() {
    override suspend fun execute(
      prompt: Prompt,
      model: LLModel,
      tools: List<ToolDescriptor>,
    ): Assistant {
      // Bridge to AICore / Gemini Nano SDK would go here
      throw NotImplementedError("AICore integration not yet wired to native SDK")
    }

    override suspend fun moderate(
      prompt: Prompt,
      model: LLModel,
    ): ModerationResult {
      throw NotImplementedError("AICore moderation not implemented")
    }

    override fun llmProvider(): LLMProvider = LLMProvider.Google
    override fun close() {}
  }

  actual override val available: Boolean = true

  actual override fun getModel(): LLModel = LLModel(
    id = "gemini-nano",
    provider = LLMProvider.Google,
    capabilities = emptyList<LLMCapability>(),
    contextLength = 4096,
    maxOutputTokens = 1024
  )

  actual override fun asKoogClient(): LLMClient = koogClient
}

