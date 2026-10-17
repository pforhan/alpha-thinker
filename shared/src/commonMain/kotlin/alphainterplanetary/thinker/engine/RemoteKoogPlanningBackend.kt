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

/**
 * The [KoogPlanningBackend] for one Remote connection: it holds the
 * [RemoteConnection] it was built with and builds the Koog client that talks to
 * it on first use. The connection is a snapshot, so the client is built once and
 * kept for this backend's lifetime — a change to the connection is a new backend
 * (the [EngineRegistry] builds one after `invalidate(ref)`), never an in-place
 * rebuild of this one. [close] releases the client.
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

  /**
   * The client and executor pair, built on first use and then kept. Nothing about
   * the connection can change underneath this object — it was handed a snapshot —
   * so there is no later target to compare against and no swap to perform: one
   * connection, one client, until [close].
   */
  private var built: State? = null

  override val executor: PromptExecutor
    get() = state().executor

  override val model: LLModel
    get() = state().model

  /** Closes the client this backend built; a backend that was never used has none. */
  override fun close() {
    built?.client?.close()
  }

  private fun state(): State = built ?: build().also { built = it }

  private fun build(): State = when (connection.provider) {
    RemoteProvider.OpenAiCompatible -> openAiCompatible()
    RemoteProvider.Ollama -> ollama()
    RemoteProvider.Anthropic -> anthropic()
  }

  private fun openAiCompatible(): State {
    val client = OpenAILLMClient(
      apiKey = connection.apiKey,
      settings = OpenAIClientSettings(baseUrl = normalizeRemoteBaseUrl(connection.baseUrl)),
      httpClientFactory = httpClientFactory,
    )
    return State(
      executor = MultiLLMPromptExecutor(client),
      // A user-configured OpenAI-compatible model: chat completions, plain text
      // completion, and temperature sampling.
      model = LLModel(
        provider = LLMProvider.OpenAI,
        id = connection.model,
        capabilities = listOf(
          LLMCapability.Temperature,
          LLMCapability.Completion,
          LLMCapability.OpenAIEndpoint.Completions,
        ),
        contextLength = connection.contextLength.toLong(),
      ),
      client = client,
    )
  }

  private fun ollama(): State {
    val client = OllamaClient(
      httpClientFactory = httpClientFactory,
      baseUrl = normalizeRemoteBaseUrl(connection.baseUrl),
      // Send the declared window as `num_ctx` so Ollama truncates at the number
      // the app budgeted against, rather than its own default (often 2048) which
      // can be smaller than what the transcript was measured to fit.
      contextWindowStrategy = ContextWindowStrategy.Companion.Fixed(connection.contextLength.toLong()),
    )
    return State(
      executor = MultiLLMPromptExecutor(client),
      model = LLModel(
        provider = LLMProvider.Ollama,
        id = connection.model,
        capabilities = listOf(LLMCapability.Temperature, LLMCapability.Completion),
        contextLength = connection.contextLength.toLong(),
      ),
      client = client,
    )
  }

  private fun anthropic(): State {
    // A catalogue id already carries the capabilities and window Anthropic
    // expects; anything else is a custom id the user typed, built with the same
    // capabilities so the request path does not reject it.
    val model = AnthropicModels.modelsById()[connection.model]
      ?: LLModel(
        provider = LLMProvider.Anthropic,
        id = connection.model,
        capabilities = AnthropicCapabilities,
        contextLength = connection.contextLength.toLong(),
      )
    val client = AnthropicLLMClient(
      apiKey = connection.apiKey,
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
      executor = MultiLLMPromptExecutor(client),
      model = model,
      client = client,
    )
  }

  private class State(
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
