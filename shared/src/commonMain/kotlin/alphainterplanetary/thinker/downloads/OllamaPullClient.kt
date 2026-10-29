package alphainterplanetary.thinker.downloads

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.http.client.lines
import alphainterplanetary.thinker.engine.normalizeRemoteBaseUrl
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for streaming model pulls from a native Ollama instance.
 */
interface OllamaPullClient {
  fun pull(
    baseUrl: String,
    modelId: String,
  ): Flow<OllamaPullEvent>
}

data class OllamaPullEvent(
  val status: String? = null,
  val digest: String? = null,
  val total: Long? = null,
  val completed: Long? = null,
  val error: String? = null,
)

/**
 * Default implementation using Koog HTTP client to stream NDJSON from
 * `POST /api/pull` with `{"model": ..., "stream": true}`.
 *
 * The response is consumed line by line as it arrives: a pull runs for minutes
 * and reports per-layer byte counts throughout, so buffering the body until the
 * server closes it would leave the UI with nothing but an indeterminate
 * spinner and a final jump to 100%.
 */
class KoogOllamaPullClient(
  private val httpClientFactory: KoogHttpClient.Factory = KtorKoogHttpClient.Factory(),
) : OllamaPullClient {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  override fun pull(baseUrl: String, modelId: String): Flow<OllamaPullEvent> = flow {
    val client = httpClientFactory.create(
      clientName = "OllamaPullClient",
      baseUrl = normalizeRemoteBaseUrl(baseUrl),
      json = json,
    )
    try {
      client.lines(
        path = OllamaPullPath,
        requestBody = OllamaPullRequest(model = modelId, stream = true),
        headers = JsonHeaders,
      ).collect { line ->
        // A line that is not an event (a stray log line, a proxy banner) is
        // skipped rather than failing a pull that is otherwise fine.
        val event = runCatching { json.decodeFromString<OllamaPullWire>(line) }
          .getOrNull()
          ?: return@collect
        val out = OllamaPullEvent(
          status = event.status,
          digest = event.digest,
          total = event.total,
          completed = event.completed,
          error = event.error,
        )
        emit(out)
        if (out.error != null) {
          throw Exception(out.error)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } finally {
      client.close()
    }
  }
}

private const val OllamaPullPath = "api/pull"
private val JsonHeaders = mapOf("Content-Type" to "application/json")

@Serializable
private data class OllamaPullRequest(
  val model: String,
  val stream: Boolean = true,
)

@Serializable
private data class OllamaPullWire(
  val status: String? = null,
  val digest: String? = null,
  val total: Long? = null,
  val completed: Long? = null,
  val error: String? = null,
)
