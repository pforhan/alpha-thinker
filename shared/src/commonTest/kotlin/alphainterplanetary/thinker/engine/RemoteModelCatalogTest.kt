package alphainterplanetary.thinker.engine

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.modelsById
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class RemoteModelCatalogTest {

  @Test
  fun `anthropic answers from koogs bundled catalogue without a request`() = runTest {
    val catalog = KoogRemoteModelCatalog(ThrowingFactory())

    val models = catalog.list(RemoteProvider.Anthropic, "https://api.anthropic.com", "")

    assertEquals(AnthropicModels.models.map { it.id }, models.map { it.id })
    assertEquals(
      AnthropicModels.models.mapNotNull { it.contextLength?.toInt() },
      models.mapNotNull { it.contextLength },
    )
  }

  @Test
  fun `the generic openai path lists the servers v1 models`() = runTest {
    // `/v1/models` is the one listing endpoint standard across the long tail —
    // OpenAI itself, OpenRouter, Groq, LM Studio, vLLM, and Ollama's `/v1` shim
    // all serve it — so the browser uses it and keeps the ids the server returns.
    val client = FakeOpenAiHttpClient(
      body = """{"object":"list","data":[
        {"id":"gpt-4o-mini","object":"model","created":1,"owned_by":"openai"},
        {"id":"llama-3.1-8b","object":"model","created":2,"owned_by":"vllm",
         "max_model_len":131072}
      ]}""",
    )
    val factory = RecordingFactory(client)

    val models = KoogRemoteModelCatalog(factory)
      .list(RemoteProvider.OpenAiCompatible, "http://localhost:8000/v1", "sk-secret")

    assertEquals(
      listOf(
        RemoteModelInfo(id = "gpt-4o-mini", contextLength = null),
        RemoteModelInfo(id = "llama-3.1-8b", contextLength = 131072),
      ),
      models,
    )
    // The `/v1` root is stripped and the client's own `v1/...` path supplied, so
    // the segment is not doubled.
    assertEquals("http://localhost:8000", factory.baseUrls.single())
    assertEquals("Bearer sk-secret", client.lastGetHeaders["Authorization"])
  }

  @Test
  fun `an openai compatible listing without a key sends no authorization header`() = runTest {
    val client = FakeOpenAiHttpClient(
      body = """{"object":"list","data":[{"id":"local-model","object":"model"}]}""",
    )

    val models = KoogRemoteModelCatalog(RecordingFactory(client))
      .list(RemoteProvider.OpenAiCompatible, "http://localhost:8000", "")

    assertEquals(listOf(RemoteModelInfo(id = "local-model", contextLength = null)), models)
    assertTrue(client.lastGetHeaders.isEmpty())
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

    val models = KoogRemoteModelCatalog(factory).list(RemoteProvider.Ollama, "http://localhost:11434/v1", "")

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
      .list(RemoteProvider.Ollama, "http://localhost:11434", "")

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
      .list(RemoteProvider.Ollama, "http://localhost:11434", "")

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
      .list(RemoteProvider.Ollama, "http://localhost:11434", "")

    assertEquals(
      listOf(
        RemoteModelInfo(id = "a:1b", contextLength = null),
        RemoteModelInfo(id = "b:2b", contextLength = 8192),
      ),
      models,
    )
  }

  @Test
  fun `anthropic answers a single models window from koogs catalogue without a request`() = runTest {
    val catalog = KoogRemoteModelCatalog(ThrowingFactory())

    val window = catalog.contextLength(
      RemoteProvider.Anthropic,
      "https://api.anthropic.com",
      RemoteProvider.Anthropic.defaultModel,
    )

    assertEquals(
      AnthropicModels.modelsById().getValue(RemoteProvider.Anthropic.defaultModel).contextLength?.toInt(),
      window,
    )
  }

  @Test
  fun `anthropic cannot name a window for a model outside its catalogue`() = runTest {
    val catalog = KoogRemoteModelCatalog(ThrowingFactory())

    val window = catalog.contextLength(RemoteProvider.Anthropic, "https://api.anthropic.com", "not-a-claude")

    assertEquals(null, window)
  }

  @Test
  fun `ollama answers a single models window without listing the server`() = runTest {
    // The targeted lookup posts one `/api/show`; it never reads `/api/tags`, so a
    // caller that already knows the id does not sweep the whole server.
    val client = FakeOllamaHttpClient(
      tags = "",
      shows = listOf(showPayload(architecture = "qwen2", contextLength = 32768, capabilities = "completion")),
    )
    val factory = RecordingFactory(client)

    val window = KoogRemoteModelCatalog(factory)
      .contextLength(RemoteProvider.Ollama, "http://localhost:11434/v1", "qwen2.5:7b")

    assertEquals(32768, window)
    assertEquals(0, client.getCalls)
    assertEquals(1, client.postCalls)
    // The `/v1` root is stripped so the native `api/...` path is not doubled.
    assertEquals("http://localhost:11434", factory.baseUrls.single())
  }

  @Test
  fun `ollama leaves a window it cannot read as nothing`() = runTest {
    val client = FakeOllamaHttpClient(tags = "", shows = listOf(null))

    val window = KoogRemoteModelCatalog(RecordingFactory(client))
      .contextLength(RemoteProvider.Ollama, "http://localhost:11434", "missing:1b")

    assertEquals(null, window)
  }

  @Test
  fun `the openai compatible path cannot name a window and asks no one`() = runTest {
    // There is no standard per-model window in the `/v1` wire format, so the
    // lookup answers null without a request — the caller's code default stands.
    val window = KoogRemoteModelCatalog(ThrowingFactory())
      .contextLength(RemoteProvider.OpenAiCompatible, "http://localhost:8000/v1", "gpt-4o-mini")

    assertEquals(null, window)
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
   * A factory that must never be asked to build a client: the provider that
   * answers locally ([RemoteProvider.Anthropic]) should not touch the network
   * at all.
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
   * Answers the generic OpenAI-compatible `GET /v1/models` with one scripted
   * body, and records the headers it was sent so a test can see the credential.
   */
  private class FakeOpenAiHttpClient(
    private val body: String,
  ) : KoogHttpClient {
    override val clientName: String = "fake-openai"
    var lastGetHeaders: Map<String, String> = emptyMap()

    override suspend fun <R : Any> get(
      path: String,
      responseType: KClass<R>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): R {
      check(path == "v1/models") { "unexpected GET $path" }
      lastGetHeaders = headers
      @Suppress("UNCHECKED_CAST")
      return body as R
    }

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
