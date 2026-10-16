package alphainterplanetary.thinker.engine

/**
 * The archetype of a planning backend (ENG-DESIGN.md "Engine modes"). `Offline`
 * is the built-in fallback ([OfflinePlanningEngine]); the other kinds host LLM
 * inference via the Koog seam ([KoogPlanningEngine]) once their backend lands.
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
   * the built-in question library entirely on device, so it has none; the LLM
   * kinds all run a model locally; only [EngineKind.Remote] egresses, because it
   * talks to a cloud or localhost endpoint.
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
  OnDevice(
    key = "on-device",
    label = "On device",
    description = "System models (Gemini Nano / Apple Foundation) running locally.",
    setOf(EngineCapability.Llm),
  ),
  Remote(
    key = "remote",
    label = "Remote",
    description = "A cloud or localhost endpoint (OpenAI-compatible / Ollama).",
    setOf(EngineCapability.Network, EngineCapability.Llm),
  ),
  LocalFile(
    // The key keeps its historical "downloaded" spelling: it is the durable
    // value a saved choice persists under, not a label (see the type doc).
    key = "downloaded",
    label = "Local file",
    description = "An in-process model installed on this device (LiteRT-LM).",
    setOf(EngineCapability.Llm),
  ),
  ;

  /**
   * Whether this backend can be selected right now. [Offline] is always
   * available, and [Remote] is selectable once configured — it talks to any
   * OpenAI-compatible endpoint from the Settings fields. The on-device
   * backends report their runtime availability once implemented (the
   * on-device client exposes `Available / Downloadable / Downloading /
   * Unavailable`); until then they gate the engine picker off.
   */
  fun available(): Boolean =
    when (this) {
      Offline -> true
      Remote -> true
      OnDevice, LocalFile -> false
    }

  companion object {
    /** A fresh install runs the built-in engine. */
    val Default: EngineKind = Offline

    /** Resolves a stored string key back to a kind; null for unknown values. */
    fun fromKey(key: String): EngineKind? = entries.find { it.key == key }
  }
}
