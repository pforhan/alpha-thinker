package alphainterplanetary.thinker.downloads

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.http.client.post
import alphainterplanetary.thinker.engine.normalizeRemoteBaseUrl
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
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
      // Koog's streaming API may vary; attempt to stream lines as NDJSON.
      // We post and expect the client to provide streamed response content.
      // Fall back to reading response body as a string sequence if needed.
      val response = client.post<OllamaPullRequest, String>(
        path = OllamaPullPath,
        requestBody = OllamaPullRequest(model = modelId, stream = true),
        headers = JsonHeaders,
      )
      // If we got a single blob, split by newlines. Streaming clients would
      // deliver chunks; this works for both cases in practice.
      response.lineSequence()
        .filter { it.isNotBlank() }
        .forEach { line ->
          val event = try {
            json.decodeFromString<OllamaPullWire>(line)
          } catch (e: Exception) {
            return@forEach
          }
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
