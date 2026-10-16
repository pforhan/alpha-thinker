package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.testutil.FakeStorage
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class RemoteKoogPlanningBackendTest {

  private fun TestScope.repository(storage: FakeStorage = FakeStorage()): SettingsRepository =
    SettingsRepository(storage, CoroutineScope(coroutineContext))

  @Test
  fun `defaults expose the Ollama model over the native Ollama client`() = runTest {
    val backend = RemoteKoogPlanningBackend(repository(), RecordingFactory())

    assertEquals("llama3.2:latest", backend.model.id)
    assertEquals(LLMProvider.Ollama, backend.model.provider)
    assertTrue(backend.model.supports(LLMCapability.Temperature))
    assertIs<MultiLLMPromptExecutor>(backend.executor)
  }

  @Test
  fun `a trailing v1 root is normalized to the server origin`() = runTest {
    val repo = repository()
    repo.setRemoteLlmBaseUrl("http://localhost:11434/v1")
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(repo, factory)

    backend.executor

    assertEquals("http://localhost:11434", factory.creations.single().baseUrl)
  }

  @Test
  fun `the same connection settings reuse the built client`() = runTest {
    val repo = repository()
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(repo, factory)

    backend.executor
    backend.model
    backend.executor

    assertEquals(1, factory.creations.size)
  }

  @Test
  fun `changing the api key rebuilds the client with the new Bearer token`() = runTest {
    val repo = repository()
    repo.setRemoteLlmProvider(RemoteProvider.OpenAiCompatible)
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(repo, factory)

    backend.executor
    assertEquals(1, factory.creations.size)

    repo.setRemoteLlmApiKey("secret")
    backend.executor

    assertEquals(2, factory.creations.size)
    assertEquals("Bearer secret", factory.creations.last().authorization)
  }

  @Test
  fun `changing the model advertises the new model id`() = runTest {
    val repo = repository()
    val backend = RemoteKoogPlanningBackend(repo, RecordingFactory())

    assertEquals("llama3.2:latest", backend.model.id)

    repo.setRemoteLlmModel("qwen2.5:7b")

    assertEquals("qwen2.5:7b", backend.model.id)
  }

  /**
   * A model named in a settings field has no catalogue to read a window from, so
   * this is where the user's declared number becomes the fact the app budgets a
   * share of — and where it has to land for the budget to mean anything at all
   * on this backend.
   */
  @Test
  fun `the model's context window is the one the user declared`() = runTest {
    val repo = repository()
    val backend = RemoteKoogPlanningBackend(repo, RecordingFactory())

    assertEquals(
      SettingsRepository.DefaultRemoteLlmContextTokens.toLong(),
      backend.model.contextLength,
    )

    repo.setRemoteLlmContextTokens(128_000)

    assertEquals(128_000L, backend.model.contextLength)
  }

  @Test
  fun `changing the base url rebuilds the client at the new origin`() = runTest {
    val repo = repository()
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(repo, factory)

    backend.executor

    repo.setRemoteLlmBaseUrl("https://api.openai.com")
    backend.executor

    assertEquals(2, factory.creations.size)
    assertEquals("https://api.openai.com", factory.creations.last().baseUrl)
  }

  // ---------- provider dispatch ----------

  @Test
  fun `the ollama provider builds an unauthenticated native client at the origin`() = runTest {
    val repo = repository()
    repo.setRemoteLlmProvider(RemoteProvider.Ollama)
    repo.setRemoteLlmBaseUrl("http://localhost:11434/v1")
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(repo, factory)

    backend.executor

    val creation = factory.creations.single()
    assertEquals("http://localhost:11434", creation.baseUrl)
    assertTrue("Authorization" !in creation.headers)
    assertEquals(LLMProvider.Ollama, backend.model.provider)
  }

  @Test
  fun `the ollama provider rides the declared window`() = runTest {
    val repo = repository()
    repo.setRemoteLlmProvider(RemoteProvider.Ollama)
    repo.setRemoteLlmContextTokens(4_096)
    val backend = RemoteKoogPlanningBackend(repo, RecordingFactory())

    assertEquals(4_096L, backend.model.contextLength)
  }

  @Test
  fun `the anthropic provider sends the api key header to its fixed origin`() = runTest {
    val repo = repository()
    repo.setRemoteLlmProvider(RemoteProvider.Anthropic)
    repo.setRemoteLlmApiKey("secret")
    repo.setRemoteLlmModel(RemoteProvider.Anthropic.defaultModel)
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(repo, factory)

    backend.executor

    val creation = factory.creations.single()
    assertEquals("https://api.anthropic.com", creation.baseUrl)
    assertEquals("secret", creation.headers["x-api-key"])
    assertEquals(LLMProvider.Anthropic, backend.model.provider)
  }

  @Test
  fun `the anthropic provider advertises the catalogue models window`() = runTest {
    val repo = repository()
    repo.setRemoteLlmProvider(RemoteProvider.Anthropic)
    repo.setRemoteLlmModel("claude-opus-4-7")
    val backend = RemoteKoogPlanningBackend(repo, RecordingFactory())

    assertEquals(1_000_000L, backend.model.contextLength)
  }

  @Test
  fun `an unknown anthropic model carries the declared window`() = runTest {
    val repo = repository()
    repo.setRemoteLlmProvider(RemoteProvider.Anthropic)
    repo.setRemoteLlmModel("claude-custom-1")
    repo.setRemoteLlmContextTokens(48_000)
    val backend = RemoteKoogPlanningBackend(repo, RecordingFactory())

    assertEquals("claude-custom-1", backend.model.id)
    assertEquals(48_000L, backend.model.contextLength)
  }

  /** Records each configured endpoint the backend requests from [KoogHttpClient.Factory]. */
  private class RecordingFactory : KoogHttpClient.Factory {
    val creations = mutableListOf<Creation>()

    override fun create(
      clientName: String,
      baseUrl: String,
      headers: Map<String, String>,
      queryParameters: Map<String, String>,
      requestTimeoutMillis: Long,
      connectTimeoutMillis: Long,
      socketTimeoutMillis: Long,
      json: Json,
    ): KoogHttpClient {
      creations += Creation(baseUrl, headers)
      return FakeClient(clientName)
    }

    data class Creation(
      val baseUrl: String,
      val headers: Map<String, String>,
    ) {
      val authorization: String? get() = headers["Authorization"]
    }
  }

  /** A [KoogHttpClient] that is only ever constructed, never exercised. */
  private class FakeClient(
    override val clientName: String,
  ) : KoogHttpClient {
    override suspend fun <R : Any> get(
      path: String,
      responseType: KClass<R>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): R = error("not exercised in tests")

    override suspend fun <T : Any, R : Any> post(
      path: String,
      requestBody: T,
      requestBodyType: KClass<T>,
      responseType: KClass<R>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): R = error("not exercised in tests")

    override fun <T : Any, R : Any, O : Any> sse(
      path: String,
      requestBody: T,
      requestBodyType: KClass<T>,
      dataFilter: (String?) -> Boolean,
      decodeStreamingResponse: (String) -> R,
      processStreamingChunk: (R) -> O?,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): Flow<O> = error("not exercised in tests")

    override fun <T : Any> lines(
      path: String,
      requestBody: T,
      requestBodyType: KClass<T>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): Flow<String> = error("not exercised in tests")

    override fun close() = Unit
  }
}
