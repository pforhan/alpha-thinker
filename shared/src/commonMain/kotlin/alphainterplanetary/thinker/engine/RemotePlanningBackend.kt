package alphainterplanetary.thinker.engine

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.repository.SettingsRepository

/**
 * The [PlanningBackend] for [EngineMode.Remote]: a Koog OpenAI-compatible
 * client pointed at whichever endpoint, API key, and model the user has
 * configured in [SettingsRepository].
 *
 * The client (and the [LLModel] advertised to the executor) is rebuilt
 * lazily whenever the base URL, API key, or model setting changes, so edits
 * in Settings take effect from the next interaction without a restart. The
 * previous client is closed on rebuild. Rebuilds are best-effort: planning
 * interactions run single-flight, so an in-flight ask is never racing a
 * rebuild because it read the old client (which stays alive until the swap
 * happens on the next read).
 *
 * The endpoint's base URL is normalized before being handed to Koog: the
 * OpenAI client appends its own `v1/...` paths, so a user-supplied
 * `.../v1` root (as Ollama's docs print) is stripped to avoid doubled
 * segments.
 */
class RemotePlanningBackend(
  private val settings: SettingsRepository,
  private val httpClientFactory: KoogHttpClient.Factory = KtorKoogHttpClient.Factory(),
) : PlanningBackend {

  override val source: LogSource = LogSource.RemoteLLM

  private var current: State? = null

  override val executor: PromptExecutor
    get() = state().executor

  override val model: LLModel
    get() = state().model

  private fun state(): State {
    val target = Target(
      baseUrl = settings.remoteLlmBaseUrl.value,
      apiKey = settings.remoteLlmApiKey.value,
      model = settings.remoteLlmModel.value,
    )
    val existing = current
    if (existing != null && existing.target == target) {
      return existing
    }
    val previous = current
    val client = OpenAILLMClient(
      apiKey = target.apiKey,
      settings = OpenAIClientSettings(baseUrl = normalizeBaseUrl(target.baseUrl)),
      httpClientFactory = httpClientFactory,
    )
    val built = State(
      target = target,
      executor = MultiLLMPromptExecutor(client),
      model = LLModel(
        provider = LLMProvider.OpenAI,
        id = target.model,
        // A user-configured OpenAI-compatible model: chat completions,
        // plain text completion, and temperature sampling.
        capabilities = listOf(
          LLMCapability.Temperature,
          LLMCapability.Completion,
          LLMCapability.OpenAIEndpoint.Completions,
        ),
      ),
      client = client,
    )
    current = built
    previous?.client?.close()
    return built
  }

  private fun normalizeBaseUrl(url: String): String =
    url.trim().trimEnd('/').removeSuffix("/v1")

  private data class Target(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
  )

  private class State(
    val target: Target,
    val executor: PromptExecutor,
    val model: LLModel,
    val client: OpenAILLMClient,
  )
}