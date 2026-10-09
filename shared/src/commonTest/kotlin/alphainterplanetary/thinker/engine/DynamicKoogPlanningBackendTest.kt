package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.testutil.FakeLlmClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DynamicKoogPlanningBackendTest {

  private val provider = LLMProvider("fake", "Fake")
  private val remoteModel = LLModel(provider, "remote-model")

  private fun backend(model: LLModel, kind: LogSource = LogSource.LocalLLM): KoogPlanningBackend =
    object : KoogPlanningBackend {
      override val executor = MultiLLMPromptExecutor(FakeLlmClient(provider))
      override val model: LLModel = model
      override val source: LogSource = kind
    }

  @Test
  fun `executor and model resolve from the backend bound to the mode`() {
    val remote = backend(remoteModel)
    val dynamic = DynamicKoogPlanningBackend(
      mode = EngineMode.Remote,
      backends = mapOf(EngineMode.Remote to remote),
    )

    assertSame(remote.executor, dynamic.executor)
    assertSame(remoteModel, dynamic.model)
  }

  @Test
  fun `source reflects the bound mode`() {
    val remote = backend(remoteModel)
    val dynamic = DynamicKoogPlanningBackend(
      mode = EngineMode.Remote,
      backends = mapOf(EngineMode.Remote to remote),
    )

    assertEquals(LogSource.RemoteLLM, dynamic.source)
  }

  @Test
  fun `a missing backend for the mode throws instead of borrowing another`() {
    val remote = backend(remoteModel)
    val dynamic = DynamicKoogPlanningBackend(
      mode = EngineMode.OnDevice,
      backends = mapOf(EngineMode.Remote to remote),
    )

    val error = assertFailsWith<IllegalStateException> { dynamic.executor }
    assertTrue(error.message.orEmpty().contains("OnDevice"), "the error names the unbound mode")
  }
}
