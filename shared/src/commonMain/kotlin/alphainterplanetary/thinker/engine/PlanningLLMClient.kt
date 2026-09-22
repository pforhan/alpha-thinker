package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel

/**
 * An abstraction for an LLM client used by the planning engine.
 * This allows us to wrap platform-specific system clients (AICore, Foundation Models)
 * into a consistent interface that the [PlanningBackend] can use.
 */
interface PlanningLLMClient {
  val available: Boolean
  fun getModel(): LLModel
  fun asKoogClient(): LLMClient
}
