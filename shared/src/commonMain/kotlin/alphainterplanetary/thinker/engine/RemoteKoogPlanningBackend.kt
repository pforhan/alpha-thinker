package alphainterplanetary.thinker.engine

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.modelsById
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.ContextWindowStrategy
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.repository.SettingsRepository

/**
 * The [KoogPlanningBackend] for [EngineKind.Remote]: a Koog client pointed at
 * whichever provider, endpoint, API key, and model the user has configured in
 * [SettingsRepository], and rebuilt lazily whenever any of those settings change
 * so edits take effect from the next interaction without a restart. The previous
 * client is closed on rebuild. Rebuilds are best-effort: planning interactions
 * run single-flight, so an in-flight ask is never racing a rebuild because it
 * read the old client (which stays alive until the swap happens on the next
 * read).
 *
 * The [RemoteProvider] picks the client, because the wire formats differ:
 * OpenAI-compatible servers (and Ollama's `/v1` shim) get [OpenAILLMClient],
 * a native Ollama server gets [OllamaClient], and Anthropic gets
 * [AnthropicLLMClient]. The endpoint is normalized before being handed over —
 * the OpenAI client appends its own `v1/...` paths and Ollama's appends `api/...`,
 * so a user-supplied `.../v1` root (as Ollama's docs print) is stripped to avoid
 * a doubled segment. A provider that fixes its own origin
 * ([RemoteProvider.configurableBaseUrl] = false) ignores the field entirely.
 *
 * The model the executor is handed is the provider's to decide. Anthropic's
 * client rejects any [LLModel] outside its version map and its request path
 * requires the tools and completion capabilities, so a catalogue id is resolved
 * against [AnthropicModels] and an unknown one is built with those capabilities.
 * Ollama and the generic path take the user's model id as written, with the
 * declared window riding along as [LLModel.contextLength] — that is what lets
 * the app budget a share of the *real* window on this backend instead of asking
 * the user to convert a model name into a token count.
 */
class RemoteKoogPlanningBackend(
  private val connection: RemoteConnection,
  private val httpClientFactory: KoogHttpClient.Factory = KtorKoogHttpClient.Factory(),
) : KoogPlanningBackend {

  override val source: LogSource = LogSource.RemoteLLM

  private var current: State? = null

  override val executor: PromptExecutor
    get() = state().executor

  override val model: LLModel
    get() = state().model

  private fun state(): State {
    val target = Target(
      provider = connection.provider,
      baseUrl = connection.baseUrl,
      apiKey = connection.apiKey,
      model = connection.model,
      contextLength = connection.contextLength,
    )
    val existing = current
    if (existing != null && existing.target == target) {
      return existing
    }
    val previous = current
    val built = when (target.provider) {
      RemoteProvider.OpenAiCompatible -> openAiCompatible(target)
      RemoteProvider.Ollama -> ollama(target)
      RemoteProvider.Anthropic -> anthropic(target)
    }
    current = built
    previous?.client?.close()
    return built
  }

  private fun openAiCompatible(target: Target): State {
    val client = OpenAILLMClient(
      apiKey = target.apiKey,
      settings = OpenAIClientSettings(baseUrl = normalizeRemoteBaseUrl(target.baseUrl)),
      httpClientFactory = httpClientFactory,
    )
    return State(
      target = target,
      executor = MultiLLMPromptExecutor(client),
      // A user-configured OpenAI-compatible model: chat completions, plain text
      // completion, and temperature sampling.
      model = LLModel(
        provider = LLMProvider.OpenAI,
        id = target.model,
        capabilities = listOf(
          LLMCapability.Temperature,
          LLMCapability.Completion,
          LLMCapability.OpenAIEndpoint.Completions,
        ),
        contextLength = target.contextLength.toLong(),
      ),
      client = client,
    )
  }

  private fun ollama(target: Target): State {
    val client = OllamaClient(
      httpClientFactory = httpClientFactory,
      baseUrl = normalizeRemoteBaseUrl(target.baseUrl),
      // Send the declared window as `num_ctx` so Ollama truncates at the number
      // the app budgeted against, rather than its own default (often 2048) which
      // can be smaller than what the transcript was measured to fit.
      contextWindowStrategy = ContextWindowStrategy.Companion.Fixed(target.contextLength.toLong()),
    )
    return State(
      target = target,
      executor = MultiLLMPromptExecutor(client),
      model = LLModel(
        provider = LLMProvider.Ollama,
        id = target.model,
        capabilities = listOf(LLMCapability.Temperature, LLMCapability.Completion),
        contextLength = target.contextLength.toLong(),
      ),
      client = client,
    )
  }

  private fun anthropic(target: Target): State {
    // A catalogue id already carries the capabilities and window Anthropic
    // expects; anything else is a custom id the user typed, built with the same
    // capabilities so the request path does not reject it.
    val model = AnthropicModels.modelsById()[target.model]
      ?: LLModel(
        provider = LLMProvider.Anthropic,
        id = target.model,
        capabilities = AnthropicCapabilities,
        contextLength = target.contextLength.toLong(),
      )
    val client = AnthropicLLMClient(
      apiKey = target.apiKey,
      settings = AnthropicClientSettings(
        // The client refuses any model it cannot map to a version, so the
        // catalogue and a custom id are both registered. Catalogue ids map to
        // themselves, which the API accepts as the short alias.
        modelVersionsMap = AnthropicModelVersions + (model to model.id),
        baseUrl = RemoteProvider.Anthropic.defaultBaseUrl,
      ),
      httpClientFactory = httpClientFactory,
    )
    return State(
      target = target,
      executor = MultiLLMPromptExecutor(client),
      model = model,
      client = client,
    )
  }

  private data class Target(
    val provider: RemoteProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val contextLength: Int,
  )

  private class State(
    val target: Target,
    val executor: PromptExecutor,
    val model: LLModel,
    val client: LLMClient,
  )

  private companion object {
    /** What Anthropic's request path requires of any model it is handed. */
    val AnthropicCapabilities: List<LLMCapability> = listOf(
      LLMCapability.Temperature,
      LLMCapability.Tools,
      LLMCapability.ToolChoice,
      LLMCapability.Completion,
      LLMCapability.Schema.JSON.Basic,
      LLMCapability.Schema.JSON.Standard,
    )

    /** Catalogue models as the version map Anthropic's client looks them up in. */
    val AnthropicModelVersions: Map<LLModel, String> =
      AnthropicModels.models.associateWith { it.id }
  }
}

/**
 * Connection details for a remote LLM backend.
 */
data class RemoteConnection(
  val provider: RemoteProvider,
  val baseUrl: String,
  val apiKey: String,
  val model: String,
  val contextLength: Int,
)
