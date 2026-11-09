package alphainterplanetary.thinker.engine.config

/**
 * The archetype of a planning backend (ENG-DESIGN.md "Engine modes"). `Offline`
 * is the built-in fallback ([OfflinePlanningEngine]); [Remote] hosts LLM
 * inference via the Koog seam ([KoogPlanningEngine]).
 *
 * A kind is the *category*, not the connection: [Remote] covers every endpoint
 * a user configures, and the specific endpoint is a per-instance identity
 * (`EngineRef` / `EngineInstance`). See ENGINE-CONFIG-PLAN.md.
 *
 * The persisted [key] is the durable string stored by the settings table, so
 * label or naming changes never break a saved choice.
 */
enum class EngineKind(
  val key: String,
  val label: String,
  val description: String,
  /**
   * The capabilities implied by choosing this kind. [EngineKind.Offline] runs
   * the built-in question library entirely on device, so it has none; only
   * [EngineKind.Remote] egresses, because it talks to a cloud or localhost
   * endpoint.
   *
   * [EngineCapability.Tools] is absent from every kind until the agentic-lookup
   * work lands a tool registry — at which point a Koog backend can report its own
   * capability rather than this static mapping.
   */
  val capabilities: Set<EngineCapability>,
) {
  Offline(
    key = "offline",
    label = "Offline",
    description = "Built-in question library. Runs entirely on this device — no network, no language model.",
    emptySet(),
  ),
  Remote(
    key = "remote",
    label = "Remote",
    description = "A cloud or localhost endpoint (OpenAI-compatible / Ollama).",
    setOf(EngineCapability.Network, EngineCapability.Llm),
  ),
  ;

  companion object {
    /** A fresh install runs the built-in engine. */
    val Default: EngineKind = Offline

    /** Resolves a stored string key back to a kind; null for unknown values. */
    fun fromKey(key: String): EngineKind? = entries.find { it.key == key }
  }
}
