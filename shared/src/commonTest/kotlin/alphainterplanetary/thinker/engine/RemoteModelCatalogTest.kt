package alphainterplanetary.thinker.engine

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteModelCatalogTest {

  @Test
  fun `anthropic answers from koogs bundled catalogue without a request`() = runTest {
    val catalog = KoogRemoteModelCatalog(ThrowingFactory())

    val models = catalog.list(RemoteProvider.Anthropic, "https://api.anthropic.com")

    assertEquals(AnthropicModels.models.map { it.id }, models.map { it.id })
    assertEquals(
      AnthropicModels.models.mapNotNull { it.contextLength?.toInt() },
      models.mapNotNull { it.contextLength },
    )
  }

  @Test
  fun `the generic openai path offers no catalogue`() = runTest {
    // `/v1/models` is optional across the long tail of OpenAI-compatible servers,
    // so the browser is empty rather than a probe that mostly fails.
    val catalog = KoogRemoteModelCatalog(ThrowingFactory())

    assertTrue(catalog.list(RemoteProvider.OpenAiCompatible, "http://localhost:11434/v1").isEmpty())
  }

  @Test
  fun `ollama lists the servers models with their real windows`() = runTest {
    val client = FakeOllamaHttpClient(
      tags = """{"models":[
        {"name":"llama3.2:latest","model":"llama3.2:latest","size":2019393189,"modified_at":"2024-01-01T00:00:00Z","digest":"a"},
        {"name":"qwen2.5:7b","model":"qwen2.5:7b","size":4683087332,"modified_at":"2024-01-01T00:00:00Z","digest":"b"}
      ]}""",
      shows = listOf(
        showPayload(architecture = "llama", contextLength = 131072, capabilities = "completion"),
        showPayload(architecture = "qwen2", contextLength = 32768, capabilities = "completion,tools"),
      ),
    )
    val factory = RecordingFactory(client)

    val models = KoogRemoteModelCatalog(factory).list(RemoteProvider.Ollama, "http://localhost:11434/v1")

    assertEquals(
      listOf(
        RemoteModelInfo(id = "llama3.2:latest", contextLength = 131072),
        RemoteModelInfo(id = "qwen2.5:7b", contextLength = 32768),
      ),
      models,
    )
    // The `/v1` root a user may have left from the generic provider is stripped
    // so the native `api/...` paths are not doubled.
    assertEquals("http://localhost:11434", factory.baseUrls.single())
    assertEquals(1, client.getCalls)
    assertEquals(2, client.postCalls)
  }

  @Test
  fun `a capability koog does not know does not fail the listing`() = runTest {
    // Ollama grew a `decision` capability after Koog 1.3.0 typed this field as a
    // closed enum, which made every `POST /api/show` fail. The listing reads the
    // window out of `model_info` and never parses `capabilities`, so an unknown
    // one is inert.
    val client = FakeOllamaHttpClient(
      tags = """{"models":[{"name":"gemma3:4b","model":"gemma3:4b","size":1}]}""",
      shows = listOf(
        showPayload(
          architecture = "gemma3",
          contextLength = 131072,
          capabilities = "completion,tools,thinking,decision,something-new",
        ),
      ),
    )

    val models = KoogRemoteModelCatalog(RecordingFactory(client))
      .list(RemoteProvider.Ollama, "http://localhost:11434")

    assertEquals(listOf(RemoteModelInfo(id = "gemma3:4b", contextLength = 131072)), models)
  }

  @Test
  fun `a window reported in the listing is taken without asking for details`() = runTest {
    // Newer servers fold `context_length` into `/api/tags`, so no `/api/show`
    // round-trip is needed for those models.
    val client = FakeOllamaHttpClient(
      tags = """{"models":[{"name":"c:3b","model":"c:3b","details":{"family":"c","context_length":65536}}]}""",
      shows = emptyList(),
    )

    val models = KoogRemoteModelCatalog(RecordingFactory(client))
      .list(RemoteProvider.Ollama, "http://localhost:11434")

    assertEquals(listOf(RemoteModelInfo(id = "c:3b", contextLength = 65536)), models)
    assertEquals(0, client.postCalls)
  }

  @Test
  fun `a models show failure drops only its window`() = runTest {
    val client = FakeOllamaHttpClient(
      tags = """{"models":[{"name":"a:1b","model":"a:1b"},{"name":"b:2b","model":"b:2b"}]}""",
      shows = listOf(
        null,
        showPayload(architecture = "b", contextLength = 8192, capabilities = "completion"),
      ),
    )

    val models = KoogRemoteModelCatalog(RecordingFactory(client))
      .list(RemoteProvider.Ollama, "http://localhost:11434")

    assertEquals(
      listOf(
        RemoteModelInfo(id = "a:1b", contextLength = null),
        RemoteModelInfo(id = "b:2b", contextLength = 8192),
      ),
      models,
    )
  }

  private fun showPayload(architecture: String, contextLength: Int, capabilities: String): String {
    val encoded = capabilities.split(",").joinToString(",") { "\"$it\"" }
    return """{"details":{"format":"gguf","family":"$architecture","parameter_size":"7B"},
        "capabilities":[$encoded],
        "model_info":{"general.architecture":"$architecture",
                      "$architecture.context_length":$contextLength,
                      "$architecture.embedding_length":3072}}"""
  }

  /**
   * A factory that must never be asked to build a client: the providers that
   * answer locally ([RemoteProvider.Anthropic], [RemoteProvider.OpenAiCompatible])
   * should not touch the network at all.
   */
  private class ThrowingFactory : KoogHttpClient.Factory {
    override fun create(
      clientName: String,
      baseUrl: String,
      headers: Map<String, String>,
      queryParameters: Map<String, String>,
      requestTimeoutMillis: Long,
      connectTimeoutMillis: Long,
      socketTimeoutMillis: Long,
      json: Json,
    ): KoogHttpClient = error("$clientName should not need an http client")
  }

  /** Records the endpoint it is asked for and hands back one scripted client. */
  private class RecordingFactory(
    private val client: KoogHttpClient,
  ) : KoogHttpClient.Factory {
    val baseUrls = mutableListOf<String>()

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
      baseUrls += baseUrl
      return client
    }
  }

  /**
   * Answers Ollama's two management calls: `api/tags` for the model list and
   * `api/show` for each model's details, in the order they are requested. A null
   * show entry throws, standing in for a model whose details cannot be read.
   */
  private class FakeOllamaHttpClient(
    private val tags: String,
    private val shows: List<String?>,
  ) : KoogHttpClient {
    override val clientName: String = "fake-ollama"
    var getCalls = 0
    var postCalls = 0
    private var showIndex = 0

    override suspend fun <R : Any> get(
      path: String,
      responseType: KClass<R>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): R {
      check(path == "api/tags") { "unexpected GET $path" }
      getCalls++
      @Suppress("UNCHECKED_CAST")
      return tags as R
    }

    override suspend fun <T : Any, R : Any> post(
      path: String,
      requestBody: T,
      requestBodyType: KClass<T>,
      responseType: KClass<R>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): R {
      check(path == "api/show") { "unexpected POST $path" }
      val body = shows[showIndex++]
      postCalls++
      return body?.let {
        @Suppress("UNCHECKED_CAST")
        it as R
      } ?: error("show failed")
    }

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
