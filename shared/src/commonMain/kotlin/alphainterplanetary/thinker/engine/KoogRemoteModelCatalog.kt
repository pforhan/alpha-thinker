package alphainterplanetary.thinker.engine

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.get
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.http.client.post
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.modelsById
import alphainterplanetary.thinker.engine.config.RemoteProvider
import alphainterplanetary.thinker.engine.config.normalizeRemoteBaseUrl
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The [RemoteModelCatalog] over the same HTTP transport the Koog clients use.
 *
 * Anthropic answers from Koog's bundled catalogue, so its list is instant and
 * complete.
 *
 * The generic OpenAI-compatible path asks the server's own `GET /v1/models`,
 * the one endpoint that is standard across that long tail (OpenAI itself,
 * OpenRouter, Groq, LM Studio, vLLM, and Ollama's `/v1` shim all serve it). It
 * returns ids and, unlike the other paths, usually no context windows — a bare
 * OpenAI listing carries none and there is no cross-vendor way to ask — so a
 * model picked from it keeps whatever window the user has declared unless the
 * server happened to report one (some, like vLLM, do).
 *
 * Ollama is asked over its management API: `GET /api/tags` names the installed
 * models and `POST /api/show` describes each one — its window, its size, its
 * quantization, and its capabilities. That real window is the reason
 * [RemoteProvider.Ollama] is a provider of its own, since the `/v1` shim
 * exposes neither it nor the `num_ctx` control the native client gets. Those
 * two calls are spoken directly rather than through Koog's `OllamaClient`
 * because its `/api/show` response types a model's `capabilities` as a closed
 * enum, so a server reporting one Koog does not know (Ollama's `decision`, and
 * whatever follows) fails the entire listing; here they are read as the plain
 * strings they are, so an unknown one is inert. The [KoogHttpClient] from
 * [httpClientFactory] is the transport underneath every Koog client, so a
 * decorator wrapped around it one day would cover these calls too.
 */
class KoogRemoteModelCatalog(
  private val httpClientFactory: KoogHttpClient.Factory = KtorKoogHttpClient.Factory(),
) : RemoteModelCatalog {
  override suspend fun list(
    provider: RemoteProvider,
    baseUrl: String,
    apiKey: String,
  ): List<RemoteModelInfo> = when (provider) {
    RemoteProvider.Anthropic -> AnthropicModels.models.map { model ->
      RemoteModelInfo(
        id = model.id,
        source = ModelMetadataSource.Anthropic,
        contextLength = model.contextLength?.toInt(),
        maxOutputTokens = model.maxOutputTokens?.toInt(),
        // Koog names a capability by its id, so the app reads the catalogue's
        // own vocabulary rather than inventing a second one.
        capabilities = model.capabilities.orEmpty().map { it.id.lowercase() }.toSet(),
      )
    }

    RemoteProvider.Ollama -> ollamaModels(baseUrl)

    RemoteProvider.OpenAiCompatible -> openAiCompatibleModels(baseUrl, apiKey)
  }

  override suspend fun contextLength(
    provider: RemoteProvider,
    baseUrl: String,
    model: String,
  ): Int? = when (provider) {
    RemoteProvider.Anthropic -> AnthropicModels.modelsById()[model]?.contextLength?.toInt()

    RemoteProvider.Ollama -> ollamaContextLength(baseUrl, model)

    // The `/v1` wire format has no per-model endpoint, so there is nothing to
    // ask: the caller's code-defined default is the most the app can know.
    RemoteProvider.OpenAiCompatible -> null
  }

  /** Asks a native Ollama server for [model]'s window; null when it will not say. */
  private suspend fun ollamaContextLength(baseUrl: String, model: String): Int? {
    val client = httpClientFactory.create(
      clientName = "OllamaModelCatalog",
      baseUrl = normalizeRemoteBaseUrl(baseUrl),
      json = catalogJson,
    )
    return try {
      show(client, model)?.contextLength()
    } finally {
      client.close()
    }
  }

  private suspend fun openAiCompatibleModels(
    baseUrl: String,
    apiKey: String,
  ): List<RemoteModelInfo> {
    val client = httpClientFactory.create(
      clientName = "OpenAiModelCatalog",
      baseUrl = normalizeRemoteBaseUrl(baseUrl),
      json = catalogJson,
    )
    return try {
      val listing = catalogJson.decodeFromString<OpenAiModels>(
        client.get<String>(path = OpenAiModelsPath, headers = bearerHeaders(apiKey)),
      )
      listing.data
        .filter { it.id.isNotBlank() }
        .map {
          RemoteModelInfo(
            id = it.id,
            source = ModelMetadataSource.OpenAiCompatible,
            contextLength = it.contextLength,
          )
        }
    } finally {
      client.close()
    }
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
      ).models.filter { it.id.isNotBlank() }
      // Manifest digests let an alias (`:latest`) be recognized as the same
      // model as its concrete tag (`:3b`) when both happen to be installed.
      val concreteByDigest = tags
        .filter { it.digest != null && it.id.substringAfter(':', "latest") != "latest" }
        .associateBy { it.digest!! }
      val resolved = tags.map { tag ->
        val siblingAlias =
          if (tag.digest != null && tag.id.substringAfter(':', "latest") == "latest") {
            concreteByDigest[tag.digest]?.id
          } else {
            null
          }
        ollamaModel(client, tag, siblingAlias)
      }
      // When a `:latest` alias resolves to a real tag that is also listed (or
      // to the same display name as a sibling), drop the alias entry so the
      // chip list doesn't show the same model twice.
      val seen = linkedMapOf<String, RemoteModelInfo>()
      for (model in resolved) {
        val tag = model.id.substringAfter(':', "latest")
        val existing = seen[model.displayName]
        if (existing == null || (existing.id.substringAfter(
            ':',
            "latest"
          ) == "latest" && tag != "latest")
        ) {
          seen[model.displayName] = model
        }
      }
      seen.values.toList()
    } finally {
      client.close()
    }
  }

  /**
   * One Ollama model, read from the listing and from the model's own details.
   *
   * The details are asked for because the listing cannot answer everything: a
   * model's capabilities and the size that resolves a `latest` alias are only
   * in `POST /api/show`, and the listing's own details stand in for a model
   * whose details cannot be read.
   */
  private suspend fun ollamaModel(
    client: KoogHttpClient,
    tag: OllamaTag,
    siblingAlias: String? = null,
  ): RemoteModelInfo {
    val show = show(client, tag.id)
    val details = show?.details ?: tag.details
    return RemoteModelInfo(
      id = tag.id,
      source = ModelMetadataSource.Ollama,
      // The alias is resolved for the eye only; the id stays the reference the
      // provider is asked for.
      // Prefer the tag-shaped size when the source actually declares one
      // ("3B"): a descriptive label such as "mini" is not a size, so fall back
      // to the numeric parameter size ("3.8B") instead of inventing "minib".
      displayName = siblingAlias
        ?: resolveLatestAlias(
          tag.id,
          if (parseSizeLabel(show?.sizeLabel()) != null) show?.sizeLabel() else details?.parameterSize,
        ),
      contextLength = show?.contextLength() ?: tag.contextLength,
      parameterSizeB = parseSizeLabel(details?.parameterSize)
        ?: sizeFromCount(show?.parameterCount()),
      sizeLabel = details?.parameterSize,
      parameterCount = show?.parameterCount(),
      sizeBytes = tag.sizeBytes,
      digest = tag.digest,
      quantization = details?.quantizationLevel,
      format = details?.format,
      capabilities = show?.capabilities
        .orEmpty()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet(),
    )
  }

  /**
   * The model's own details, or null when the server will not say. A failed
   * `/api/show` costs the model its metadata, not its place in the list: the
   * listing is the point and the details are a bonus.
   */
  private suspend fun show(client: KoogHttpClient, model: String): OllamaShow? = try {
    catalogJson.decodeFromString<OllamaShow>(
      client.post<OllamaShowRequest, String>(
        path = OllamaShowPath,
        requestBody = OllamaShowRequest(model),
        headers = JsonHeaders,
      ),
    )
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }
}

private const val OllamaTagsPath = "api/tags"
private const val OllamaShowPath = "api/show"
private const val OpenAiModelsPath = "v1/models"

private val JsonHeaders = mapOf("Content-Type" to "application/json")

/**
 * The `Authorization` header a server that gates its listing needs; empty for
 * one that does not (a local server, or an endpoint that asks for no key). The
 * OpenAI-compatible path is the only one that can need a key, so it is the only
 * caller.
 */
private fun bearerHeaders(apiKey: String): Map<String, String> =
  apiKey.trim().takeIf { it.isNotEmpty() }
    ?.let { mapOf("Authorization" to "Bearer $it") }
    ?: emptyMap()

/**
 * Lenient on purpose: Ollama's own fields grow between versions, and only the
 * names read below are looked for, so anything else — a capability the app has
 * never heard of, a detail it does not use — is ignored rather than allowed to
 * fail the parse.
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
  /** The on-disk size of the weights, in bytes. */
  @SerialName("size") val sizeBytes: Long? = null,
  /** The model's manifest digest (Ollama SHA). */
  @SerialName("digest") val digest: String? = null,
  val details: OllamaTagDetails? = null,
) {
  /** Ollama prints both; either serves as the id to ask for. */
  val id: String get() = name.ifBlank { model }

  /** Present from servers recent enough to fold the window into the listing. */
  val contextLength: Int? get() = details?.contextLength
}

/**
 * What a source says about a model beyond its name: how big it is, how it is
 * quantized, and the format its weights are stored in. The listing and the
 * details call report the same shape, so both are read into this one.
 */
@Serializable
private data class OllamaTagDetails(
  @SerialName("context_length") val contextLength: Int? = null,
  @SerialName("parameter_size") val parameterSize: String? = null,
  @SerialName("quantization_level") val quantizationLevel: String? = null,
  val format: String? = null,
)

@Serializable
private data class OllamaShowRequest(val model: String)

@Serializable
private data class OllamaShow(
  @SerialName("model_info") val modelInfo: JsonObject? = null,
  val details: OllamaTagDetails? = null,
  val capabilities: List<String> = emptyList(),
) {
  /**
   * The window `model_info` carries, keyed by the model's architecture
   * (`llama.context_length`, `qwen2.context_length`, …). The architecture names
   * the key, so it is read first and the rest is a scan for any such key, which
   * covers a server that reports one without naming the architecture.
   */
  fun contextLength(): Int? {
    val info = modelInfo
    if (info != null) {
      val architecture = (info["general.architecture"] as? JsonPrimitive)?.contentOrNull
      val declared = architecture?.let { info["$it.context_length"] }
      val value = declared
        ?: info.entries.firstOrNull { it.key.endsWith(".context_length") }?.value
      val parsed = (value as? JsonPrimitive)?.longOrNull?.toInt()
      if (parsed != null) return parsed
    }
    return details?.contextLength
  }

  /**
   * The size the model declares for itself ("3B"). The details report the same
   * figure in another unit ("3.2B"), so the declared label is the one that says
   * what the author meant.
   */
  fun sizeLabel(): String? = (modelInfo?.get("general.size_label") as? JsonPrimitive)?.contentOrNull

  /** The model's exact parameter count, where it declares one. */
  fun parameterCount(): Long? =
    (modelInfo?.get("general.parameter_count") as? JsonPrimitive)?.longOrNull
}

@Serializable
private data class OpenAiModels(val data: List<OpenAiModel> = emptyList())

/**
 * One entry of an OpenAI-compatible `/v1/models` listing. [id] is the standard
 * field and the only one the wire format guarantees; the window keys are read
 * opportunistically, since a server that reports one (vLLM's `max_model_len`
 * being the common case) spares the user from declaring it.
 */
@Serializable
private data class OpenAiModel(
  val id: String = "",
  @SerialName("max_model_len") val maxModelLen: Int? = null,
  @SerialName("context_length") val declaredContextLength: Int? = null,
  @SerialName("context_window") val contextWindow: Int? = null,
) {
  val contextLength: Int? get() = maxModelLen ?: declaredContextLength ?: contextWindow
}
