package alphainterplanetary.thinker.engine

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import alphainterplanetary.thinker.activitylog.LogCategory
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DynamicPlanningBackendTest {

  private val provider = LLMProvider("fake", "Fake")
  private val remoteModel = LLModel(provider, "remote-model")

  private fun backend(model: LLModel, kind: LogCategory = LogCategory.LocalInference): PlanningBackend =
    object : PlanningBackend {
      override val executor = MultiLLMPromptExecutor(StaticClient(provider))
      override val model: LLModel = model
      override val logCategory: LogCategory = kind
    }

  @Test
  fun `executor and model resolve from the backend bound to the mode`() {
    val remote = backend(remoteModel)
    val dynamic = DynamicPlanningBackend(
      mode = EngineMode.Remote,
      backends = mapOf(EngineMode.Remote to remote),
    )

    assertSame(remote.executor, dynamic.executor)
    assertSame(remoteModel, dynamic.model)
  }

  @Test
  fun `kind reflects the bound mode`() {
    val remote = backend(remoteModel)
    val dynamic = DynamicPlanningBackend(
      mode = EngineMode.Remote,
      backends = mapOf(EngineMode.Remote to remote),
    )

    assertEquals(LogCategory.RemoteInference, dynamic.logCategory)
  }

  @Test
  fun `a missing backend for the mode throws instead of borrowing another`() {
    val remote = backend(remoteModel)
    val dynamic = DynamicPlanningBackend(
      mode = EngineMode.OnDevice,
      backends = mapOf(EngineMode.Remote to remote),
    )

    val error = assertFailsWith<IllegalStateException> { dynamic.executor }
    assertTrue(error.message.orEmpty().contains("OnDevice"), "the error names the unbound mode")
  }

  private class StaticClient(
    private val provider: LLMProvider,
  ) : LLMClient() {
    override fun llmProvider(): LLMProvider = provider

    override suspend fun execute(
      prompt: Prompt,
      model: LLModel,
      tools: List<ToolDescriptor>,
    ): Message.Assistant = Message.Assistant("", ResponseMetaInfo(Instant.fromEpochMilliseconds(0)))

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
      ModerationResult(false, emptyMap())

    override fun close() = Unit
  }
}