package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel

/**
 * System-provided on-device LLM client.
 * Actuals provide bridges to AICore (Android) and Foundation Models (iOS).
 */
expect class OnDeviceLLMClient() : PlanningLLMClient {
  override val available: Boolean
  override fun getModel(): LLModel
  override fun asKoogClient(): LLMClient
}

