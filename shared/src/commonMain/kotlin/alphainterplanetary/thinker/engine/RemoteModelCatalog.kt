package alphainterplanetary.thinker.engine

/**
 * The models reachable from a [RemoteProvider]. An interface so the browser UI
 * and its tests can fake the provider's answer.
 *
 * [apiKey] is the credential the provider's listing needs; providers whose list
 * is not gated on one ([RemoteProvider.Anthropic], [RemoteProvider.Ollama])
 * ignore it.
 */
interface RemoteModelCatalog {
  suspend fun list(
    provider: RemoteProvider,
    baseUrl: String,
    apiKey: String,
  ): List<RemoteModelInfo>

  /**
   * The context window [model] is served at. Unlike [list] this asks the
   * provider about one model — Ollama's `POST /api/show`, Anthropic's bundled
   * catalogue — so a caller that already knows the id it wants does not have to
   * list (and detail) a whole server to learn a single number. It exists for
   * the provider-switch defaults (ENGINE-CONFIG-PLAN.md 2a3), where the starter
   * model's window should be the model's real one rather than a code fallback.
   *
   * Null when the provider cannot say: the OpenAI-compatible path has no
   * standard per-model endpoint, and a model the provider does not serve (or a
   * server that cannot be reached) has no window to report.
   */
  suspend fun contextLength(
    provider: RemoteProvider,
    baseUrl: String,
    model: String,
  ): Int?
}
