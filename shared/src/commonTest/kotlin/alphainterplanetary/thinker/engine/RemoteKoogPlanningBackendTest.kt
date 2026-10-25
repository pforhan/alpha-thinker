package alphainterplanetary.thinker.engine

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * The backend is configured by one [RemoteConnection] snapshot: provider
 * dispatch, endpoint normalization, and the model's window all derive from the
 * connection it was constructed with. There is no in-place rebuild here — a
 * changed connection is a new backend (see `EngineRegistry.invalidate`), so
 * these tests build a backend per configuration rather than mutating one.
 */
class RemoteKoogPlanningBackendTest {

  private fun connection(
    provider: RemoteProvider = RemoteProvider.Ollama,
    baseUrl: String = provider.defaultBaseUrl,
    apiKey: String = "",
    model: String = provider.defaultModel,
    contextLength: Int = provider.defaultContextTokens,
  ) = RemoteConnection(
    provider = provider,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    contextLength = contextLength,
  )

  @Test
  fun `the default connection exposes the Ollama model over the native client`() {
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(connection(), factory)

    assertEquals("llama3.2:latest", backend.model.id)
    assertEquals(LLMProvider.Ollama, backend.model.provider)
    assertTrue(backend.model.supports(LLMCapability.Temperature))
    assertIs<MultiLLMPromptExecutor>(backend.executor)
  }

  @Test
  fun `a trailing v1 root is normalized to the server origin`() {
    val factory = RecordingFactory()
    val backend =
      RemoteKoogPlanningBackend(connection(baseUrl = "http://localhost:11434/v1"), factory)

    backend.executor

    assertEquals("http://localhost:11434", factory.creations.single().baseUrl)
  }

  @Test
  fun `one connection builds one client however often it is read`() {
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(connection(), factory)

    backend.executor
    backend.model
    backend.executor

    assertEquals(1, factory.creations.size)
  }

  @Test
  fun `the connection fields are what the model advertises`() {
    val backend = RemoteKoogPlanningBackend(
      connection(
        provider = RemoteProvider.OpenAiCompatible,
        model = "qwen2.5:7b",
        contextLength = 128_000,
      ),
      RecordingFactory(),
    )

    assertEquals("qwen2.5:7b", backend.model.id)
    assertEquals(128_000L, backend.model.contextLength)
  }

  /**
   * A model named in a connection field has no catalogue to read a window from,
   * so the declared number is the fact the app budgets a share of — and it has
   * to land on the model this backend advertises or the budget means nothing.
   */
  @Test
  fun `the model's context window is the one the connection declared`() {
    val backend = RemoteKoogPlanningBackend(connection(contextLength = 4_096), RecordingFactory())

    assertEquals(4_096L, backend.model.contextLength)
  }

  // ---------- provider dispatch ----------

  @Test
  fun `the ollama provider builds an unauthenticated native client at the origin`() {
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(
      connection(provider = RemoteProvider.Ollama, baseUrl = "http://localhost:11434/v1"),
      factory,
    )

    backend.executor

    val creation = factory.creations.single()
    assertEquals("http://localhost:11434", creation.baseUrl)
    assertTrue("Authorization" !in creation.headers)
    assertEquals(LLMProvider.Ollama, backend.model.provider)
  }

  @Test
  fun `the openai-compatible provider sends a Bearer token to its normalized origin`() {
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(
      connection(
        provider = RemoteProvider.OpenAiCompatible,
        apiKey = "secret",
      ),
      factory,
    )

    backend.executor

    val creation = factory.creations.single()
    assertEquals("http://localhost:11434", creation.baseUrl)
    assertEquals("Bearer secret", creation.authorization)
    assertEquals(LLMProvider.OpenAI, backend.model.provider)
  }

  @Test
  fun `the anthropic provider sends the api key header to its fixed origin`() {
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(
      connection(
        provider = RemoteProvider.Anthropic,
        // Stale from a previous provider: a fixed origin ignores the field.
        baseUrl = "http://localhost:11434",
        apiKey = "secret",
      ),
      factory,
    )

    backend.executor

    val creation = factory.creations.single()
    assertEquals("https://api.anthropic.com", creation.baseUrl)
    assertEquals("secret", creation.headers["x-api-key"])
    assertEquals(LLMProvider.Anthropic, backend.model.provider)
  }

  @Test
  fun `the anthropic provider advertises the catalogue model's window`() {
    val backend = RemoteKoogPlanningBackend(
      connection(provider = RemoteProvider.Anthropic, model = "claude-opus-4-7"),
      RecordingFactory(),
    )

    assertEquals(1_000_000L, backend.model.contextLength)
  }

  @Test
  fun `an unknown anthropic model carries the declared window`() {
    val backend = RemoteKoogPlanningBackend(
      connection(
        provider = RemoteProvider.Anthropic,
        model = "claude-custom-1",
        contextLength = 48_000,
      ),
      RecordingFactory(),
    )

    assertEquals("claude-custom-1", backend.model.id)
    assertEquals(48_000L, backend.model.contextLength)
  }

  // ---------- lifecycle ----------

  @Test
  fun `close releases the client the backend built`() {
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(connection(), factory)

    backend.executor
    backend.close()

    assertTrue(factory.creations.single().client.closed)
  }

  @Test
  fun `close is safe on a backend that never built a client`() {
    val factory = RecordingFactory()
    val backend = RemoteKoogPlanningBackend(connection(), factory)

    backend.close()

    assertEquals(0, factory.creations.size)
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
      val client = FakeClient(clientName)
      creations += Creation(baseUrl, headers, client)
      return client
    }

    data class Creation(
      val baseUrl: String,
      val headers: Map<String, String>,
      val client: FakeClient,
    ) {
      val authorization: String? get() = headers["Authorization"]
    }
  }

  /** A [KoogHttpClient] that is only ever constructed, never exercised. */
  private class FakeClient(
    override val clientName: String,
  ) : KoogHttpClient {
    var closed = false

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

    override fun close() {
      closed = true
    }
  }
}
