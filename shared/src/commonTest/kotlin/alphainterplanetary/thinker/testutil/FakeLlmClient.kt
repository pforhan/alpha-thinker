package alphainterplanetary.thinker.testutil

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.time.Instant

/**
 * A Koog [LLMClient] for tests: answers each [execute] from a canned [responses]
 * queue (empty once the queue runs out), captures the last built [Prompt], and
 * throws [failure] instead when set.
 *
 * One stub covers the three clients the engine tests used to hand-roll —
 * reply-scripting, silent, and exploding — since they differed only in which of
 * these three knobs they turned.
 */
class FakeLlmClient(
  private val provider: LLMProvider,
  vararg responses: String,
) : LLMClient() {
  private val queue = ArrayDeque(responses.toList())

  /** The last prompt handed to [execute]. */
  var lastPrompt: Prompt = prompt("unset") { }
    private set

  /** When set, [execute] throws this instead of answering. */
  var failure: Throwable? = null

  override fun llmProvider(): LLMProvider = provider

  override suspend fun execute(
    prompt: Prompt,
    model: LLModel,
    tools: List<ToolDescriptor>,
  ): Message.Assistant {
    lastPrompt = prompt
    failure?.let { throw it }
    val text = queue.removeFirstOrNull() ?: ""
    return Message.Assistant(text, ResponseMetaInfo(Instant.fromEpochMilliseconds(0)))
  }

  override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
    ModerationResult(false, emptyMap())

  override fun close() = Unit
}
