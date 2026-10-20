package alphainterplanetary.thinker.engine

/**
 * Which flavor of remote endpoint a [RemoteKoogPlanningBackend] talks to. The
 * mode is one ([EngineKind.Remote]) but the client that reaches it is not: a
 * native Ollama server and Anthropic's API each speak a different protocol,
 * with different defaults and different ways to learn a model's name and
 * window, so the provider decides which Koog client the backend builds.
 *
 * This is deliberately a small, flat catalogue rather than a configurable
 * instance list: every entry here needs at most an endpoint, a key, and a model.
 * [EngineKind.Remote]'s `OpenAiCompatible` remains the escape hatch for the
 * long tail of `/v1` endpoints (OpenAI itself, OpenRouter, Groq, LM Studio,
 * vLLM, …) because they all speak the same wire format and so need no entry of
 * their own.
 *
 * The persisted [key] is the durable string stored by the settings table, so
 * label or ordering changes never break a saved choice.
 */
enum class RemoteProvider(
  val key: String,
  val label: String,
  val description: String,
  /**
   * The endpoint a freshly-chosen provider starts from, and — when
   * [configurableBaseUrl] is false — the one it always uses. [Ollama] and
   * [OpenAiCompatible] both point at a local server out of the box (the native
   * `/api` root and the `/v1` shim respectively), so a fresh install still has
   * somewhere to point; for [Anthropic] it is the fixed API origin.
   */
  val defaultBaseUrl: String,
  /**
   * Whether the user may edit the endpoint. False only for [Anthropic], whose
   * API lives at one origin the client already knows: showing an editable URL
   * there would offer to break a connection the user cannot meaningfully move.
   */
  val configurableBaseUrl: Boolean,
  /** The model a freshly-chosen provider starts from. */
  val defaultModel: String,
  /** The context window a freshly-chosen provider starts from. */
  val defaultContextTokens: Int,
  /**
   * The name a new connection starts with, prefilled in the dialog's Name
   * field and following the provider pill until the user types a name of
   * their own. Short and human — a connection is named for what it is to the
   * user ("API" for the generic `/v1` escape hatch), not for the protocol
   * chip it was picked from ([label]).
   */
  val suggestedConnectionName: String,
  /** Whether a request is rejected outright without an API key. */
  val requiresApiKey: Boolean,
  /**
   * Where the user creates a key, for the providers that need one; null
   * otherwise. Presented as a link out so the value never has to be hunted for
   * in a separate browser tab the app cannot reach.
   */
  val apiKeyUrl: String?,
) {
  /**
   * A native Ollama server. Listed first because it is the app's documented
   * local target and [Default]: the distinct client is worth carrying because
   * Ollama can be asked for a model's real context window
   * ([RemoteModelCatalog]) and told which window to allocate (`num_ctx`),
   * neither of which the `/v1` shim exposes; it also needs no key. (It can list
   * its own models, but so can the shim's standard `GET /v1/models`, so the
   * window — not the list — is what earns the separate entry.)
   */
  Ollama(
    key = "ollama",
    label = "Ollama",
    description = "A local Ollama server. No API key; models are listed from the server.",
    defaultBaseUrl = "http://localhost:11434",
    configurableBaseUrl = true,
    defaultModel = "llama3.2:latest",
    defaultContextTokens = 8_192,
    suggestedConnectionName = "Ollama",
    requiresApiKey = false,
    apiKeyUrl = null,
  ),

  /**
   * The generic OpenAI-compatible connection, the escape hatch for the long
   * tail of `/v1` servers. Its models can be listed through the standard
   * `GET /v1/models`, but that wire format carries no window, so this is where
   * a model typed into a settings field becomes the fact the app budgets
   * against: the user declares the window unless the server happens to report
   * one in the listing.
   */
  OpenAiCompatible(
    key = "openai-compatible",
    label = "OpenAI-compatible",
    description = "OpenAI, OpenRouter, Groq, LM Studio, and other /v1 servers.",
    defaultBaseUrl = "http://localhost:11434/v1",
    configurableBaseUrl = true,
    defaultModel = "llama3.2:latest",
    defaultContextTokens = 8_192,
    suggestedConnectionName = "API",
    requiresApiKey = false,
    apiKeyUrl = null,
  ),

  /**
   * Anthropic's Claude models. The model's window is read from the catalogue
   * ([RemoteModelCatalog]) rather than declared, so [defaultContextTokens] is
   * only a placeholder for an id the catalogue does not know.
   */
  Anthropic(
    key = "anthropic",
    label = "Anthropic",
    description = "Claude models over Anthropic's API.",
    defaultBaseUrl = "https://api.anthropic.com",
    configurableBaseUrl = false,
    // Kept in step with `AnthropicModels.Sonnet_4_5`; RemoteProviderTest asserts
    // the catalogue still carries it.
    defaultModel = "claude-sonnet-4-5",
    defaultContextTokens = 200_000,
    suggestedConnectionName = "Anthropic",
    requiresApiKey = true,
    apiKeyUrl = "https://console.anthropic.com/settings/keys",
  ),
  ;

  companion object {
    /**
     * The connection a fresh install starts on, and the option the provider
     * picker lists first: a local Ollama server, the app's documented local
     * target. It serves unauthenticated and can be asked for its own model
     * list, unlike the OpenAI-compatible shim.
     */
    val Default: RemoteProvider = Ollama

    /** Resolves a stored string key back to a provider; null for unknown values. */
    fun fromKey(key: String): RemoteProvider? = entries.find { it.key == key }
  }
}

/**
 * The endpoint origin a Koog client should be given for [baseUrl].
 *
 * The OpenAI client appends its own `v1/...` paths, and a native Ollama client
 * appends `api/...`, so a user-supplied `.../v1` root (as Ollama's docs print,
 * and as [RemoteProvider.OpenAiCompatible]'s default does) is stripped to avoid
 * a doubled segment. Shared by the backend and the model catalogue so the two
 * cannot disagree about where a server lives.
 */
internal fun normalizeRemoteBaseUrl(url: String): String =
  url.trim().trimEnd('/').removeSuffix("/v1")
