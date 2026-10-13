package alphainterplanetary.thinker.engine

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.get
import ai.koog.http.client.post
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * One model a remote provider offers: the id to ask for and, when the provider
 * can tell us, the context window it serves. The window is what lets the app
 * budget a real share of the model's own limit instead of falling back to the
 * number declared in the settings field.
 */
data class RemoteModelInfo(
  val id: String,
  val contextLength: Int?,
)

/**
 * The models reachable from a [RemoteProvider]. An interface so the browser UI
 * and its tests can fake the provider's answer.
 */
fun interface RemoteModelCatalog {
  suspend fun list(provider: RemoteProvider, baseUrl: String): List<RemoteModelInfo>
}

/**
 * The [RemoteModelCatalog] over the same HTTP transport the Koog clients use.
 *
 * Anthropic answers from Koog's bundled catalogue, so its list is instant and
 * complete. The generic OpenAI-compatible path has no reliable catalogue —
 * `/v1/models` is optional across the long tail of servers — so it returns
 * nothing and the user keeps typing the id, as before.
 *
 * Ollama is asked over its management API, which is the whole reason
 * [RemoteProvider.Ollama] is a provider of its own: `GET /api/tags` names the
 * installed models and `POST /api/show` carries each one's `context_length`.
 * Those two calls are spoken directly rather than through Koog's `OllamaClient`
 * because its `/api/show` response types a model's `capabilities` as a closed
 * enum, so a server reporting one Koog does not know (Ollama's `decision`, and
 * whatever follows) fails the entire listing. The [KoogHttpClient] from
 * [httpClientFactory] is the transport underneath every Koog client, so a
 * decorator wrapped around it one day would cover these calls too.
 */
class KoogRemoteModelCatalog(
  private val httpClientFactory: KoogHttpClient.Factory = KtorKoogHttpClient.Factory(),
) : RemoteModelCatalog {
  override suspend fun list(provider: RemoteProvider, baseUrl: String): List<RemoteModelInfo> =
    when (provider) {
      RemoteProvider.Anthropic -> AnthropicModels.models.map { model ->
        RemoteModelInfo(id = model.id, contextLength = model.contextLength?.toInt())
      }

      RemoteProvider.Ollama -> ollamaModels(baseUrl)

      RemoteProvider.OpenAiCompatible -> emptyList()
    }

  private suspend fun ollamaModels(baseUrl: String): List<RemoteModelInfo> {
    val client = httpClientFactory.create(
      clientName = "OllamaModelCatalog",
      baseUrl = normalizeRemoteBaseUrl(baseUrl),
      json = catalogJson,
    )
    return try {
      val tags = catalogJson.decodeFromString<OllamaTags>(
        client.get<String>(path = OllamaTagsPath),
      )
      tags.models
        .filter { it.id.isNotBlank() }
        .map { tag ->
          RemoteModelInfo(
            id = tag.id,
            // Newer servers put the window right in the listing, which spares a
            // `/api/show` per model; only the ones that do not, ask.
            contextLength = tag.contextLength ?: contextLength(client, tag.id),
          )
        }
    } finally {
      client.close()
    }
  }

  /**
   * The window reported for [model], or null when the server cannot say. A model
   * whose details cannot be read is still a model the server serves — the list
   * is the point and the window is a bonus — so a failed `/api/show` drops only
   * the window rather than the whole listing.
   */
  private suspend fun contextLength(client: KoogHttpClient, model: String): Int? = try {
    catalogJson.decodeFromString<OllamaShow>(
      client.post<OllamaShowRequest, String>(
        path = OllamaShowPath,
        requestBody = OllamaShowRequest(model),
        headers = JsonHeaders,
      ),
    ).contextLength()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }
}

private const val OllamaTagsPath = "api/tags"
private const val OllamaShowPath = "api/show"

private val JsonHeaders = mapOf("Content-Type" to "application/json")

/**
 * Lenient on purpose: Ollama's own fields grow between versions, and only the
 * names and the one context-length key below are read, so anything else — a
 * capability the app has never heard of, a detail it does not use — is ignored
 * rather than allowed to fail the parse.
 */
private val catalogJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

@Serializable
private data class OllamaTags(val models: List<OllamaTag> = emptyList())

@Serializable
private data class OllamaTag(
  val name: String = "",
  val model: String = "",
  val details: OllamaTagDetails? = null,
) {
  /** Ollama prints both; either serves as the id to ask for. */
  val id: String get() = name.ifBlank { model }

  /** Present from servers recent enough to fold the window into the listing. */
  val contextLength: Int? get() = details?.contextLength
}

@Serializable
private data class OllamaTagDetails(
  @SerialName("context_length") val contextLength: Int? = null,
)

@Serializable
private data class OllamaShowRequest(val model: String)

@Serializable
private data class OllamaShow(
  @SerialName("model_info") val modelInfo: JsonObject? = null,
) {
  /**
   * The window `model_info` carries, keyed by the model's architecture
   * (`llama.context_length`, `qwen2.context_length`, …). The architecture names
   * the key, so it is read first and the rest is a scan for any such key, which
   * covers a server that reports one without naming the architecture.
   */
  fun contextLength(): Int? {
    val info = modelInfo ?: return null
    val architecture = (info["general.architecture"] as? JsonPrimitive)?.contentOrNull
    val declared = architecture?.let { info["$it.context_length"] }
    val value = declared
      ?: info.entries.firstOrNull { it.key.endsWith(".context_length") }?.value
    return (value as? JsonPrimitive)?.longOrNull?.toInt()
  }
}

/**
 * What the model browse affordance in the Remote card is showing. A sealed
 * interface rather than a list plus a flag, so "loading" and "found nothing"
 * cannot be confused — an empty Ollama server is a real answer, not a spinner.
 */
sealed interface RemoteModelListState {
  /** Nothing requested yet; no list is shown. */
  data object Idle : RemoteModelListState

  /** A request is in flight; the browse control shows a spinner. */
  data object Loading : RemoteModelListState

  /** The provider's models, possibly empty. */
  data class Loaded(val models: List<RemoteModelInfo>) : RemoteModelListState

  /** The probe failed; [message] is shown in place of the list. */
  data class Failed(val message: String) : RemoteModelListState
}
