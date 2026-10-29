package alphainterplanetary.thinker.downloads

import ai.koog.http.client.KoogHttpClient
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class OllamaPullClientTest {

  @Test
  fun `pull streams ndjson events from the native ollama endpoint`() = runTest {
    val client = FakePullHttpClient(
      body = """
        {"status":"pulling manifest"}
        {"status":"pulling abc","digest":"sha256:abc","total":100,"completed":25}
        {"status":"success"}
      """.trimIndent(),
    )
    val factory = RecordingFactory(client)

    val events = KoogOllamaPullClient(factory)
      .pull("http://localhost:11434/v1", "qwen2.5:7b")
      .toList()

    // The native `api/pull` path must not have a user-supplied `/v1` root
    // doubled ahead of it.
    assertEquals("http://localhost:11434", factory.baseUrls.single())
    assertEquals("api/pull", client.path)
    assertEquals("application/json", client.headers["Content-Type"])
    // The request object is serialized by Koog's `lines`, so the fake sees the
    // object itself rather than the wire body.
    val request = checkNotNull(client.requestBody)
    assertTrue("model=qwen2.5:7b" in request)
    assertTrue("stream=true" in request)
    assertEquals(
      listOf(
        OllamaPullEvent(status = "pulling manifest"),
        OllamaPullEvent(
          status = "pulling abc",
          digest = "sha256:abc",
          total = 100,
          completed = 25,
        ),
        OllamaPullEvent(status = "success"),
      ),
      events,
    )
    assertTrue(client.closed, "the client should be closed after the stream")
  }

  @Test
  fun `pull emits each event as soon as the server sends its line`() = runTest {
    // A pull runs for minutes; buffering the body would leave the caller with
    // no progress until the end. The gate only opens once the first event has
    // reached the collector, so a buffered implementation stalls here.
    val gate = CompletableDeferred<Unit>()
    val client = FakePullHttpClient(
      lines = flow {
        emit("""{"status":"pulling"}""")
        gate.await()
        emit("""{"status":"success"}""")
      },
    )
    val events = mutableListOf<OllamaPullEvent>()
    val job = launch {
      KoogOllamaPullClient(RecordingFactory(client))
        .pull("http://localhost:11434", "qwen2.5:7b")
        .collect { events += it }
    }

    testScheduler.runCurrent()
    assertEquals(listOf(OllamaPullEvent(status = "pulling")), events)

    gate.complete(Unit)
    job.join()
    assertEquals(listOf("pulling", "success"), events.map { it.status })
  }

  @Test
  fun `pull skips blank lines and lines that are not events`() = runTest {
    val client = FakePullHttpClient(body = "\nnot json\n{\"status\":\"success\"}\n")

    val events = KoogOllamaPullClient(RecordingFactory(client))
      .pull("http://localhost:11434", "qwen2.5:7b")
      .toList()

    assertEquals(listOf(OllamaPullEvent(status = "success")), events)
  }

  @Test
  fun `a server error event is emitted and then fails the pull`() = runTest {
    val client = FakePullHttpClient(body = """{"error":"model not found"}""")
    val events = mutableListOf<OllamaPullEvent>()

    val failure = runCatching {
      KoogOllamaPullClient(RecordingFactory(client))
        .pull("http://localhost:11434", "missing:1b")
        .collect { events += it }
    }.exceptionOrNull()

    assertEquals("model not found", failure?.message)
    assertEquals(listOf(OllamaPullEvent(error = "model not found")), events)
    assertTrue(client.closed, "the client should be closed even when the pull fails")
  }

  @Test
  fun `a transport failure closes the client`() = runTest {
    val client = FakePullHttpClient(
      body = "",
      failWith = IllegalStateException("connection refused"),
    )

    val failure = runCatching {
      KoogOllamaPullClient(RecordingFactory(client))
        .pull("http://localhost:11434", "qwen2.5:7b")
        .toList()
    }.exceptionOrNull()

    assertEquals("connection refused", failure?.message)
    assertTrue(client.closed, "the client should be closed when the request fails")
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

  /** Answers `POST api/pull` with one scripted sequence of lines. */
  private class FakePullHttpClient(
    private val lines: Flow<String>,
    private val failWith: Exception? = null,
  ) : KoogHttpClient {
    constructor(body: String, failWith: Exception? = null) : this(
      lines = body.lineSequence().filter { it.isNotBlank() }.asFlow(),
      failWith = failWith,
    )

    override val clientName: String = "fake-pull"
    var path: String? = null
    var requestBody: String? = null
    var headers: Map<String, String> = emptyMap()
    var closed = false

    override suspend fun <R : Any> get(
      path: String,
      responseType: KClass<R>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): R = error("not exercised by the pull client")

    override suspend fun <T : Any, R : Any> post(
      path: String,
      requestBody: T,
      requestBodyType: KClass<T>,
      responseType: KClass<R>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): R = error("the pull client must stream, not buffer a post response")

    override fun <T : Any, R : Any, O : Any> sse(
      path: String,
      requestBody: T,
      requestBodyType: KClass<T>,
      dataFilter: (String?) -> Boolean,
      decodeStreamingResponse: (String) -> R,
      processStreamingChunk: (R) -> O?,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): Flow<O> = error("not exercised by the pull client")

    override fun <T : Any> lines(
      path: String,
      requestBody: T,
      requestBodyType: KClass<T>,
      parameters: Map<String, String>,
      headers: Map<String, String>,
    ): Flow<String> {
      this.path = path
      this.requestBody = requestBody.toString()
      this.headers = headers
      return flow {
        failWith?.let { throw it }
        emitAll(lines)
      }
    }

    override fun close() {
      closed = true
    }
  }
}
