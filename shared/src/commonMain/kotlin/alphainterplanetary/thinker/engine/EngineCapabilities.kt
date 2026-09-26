package alphainterplanetary.thinker.engine

/**
 * A thing the selected planning backend does, derived from its [EngineMode]
 * rather than measured at runtime.
 *
 * The app's status chrome reports these so a user can see what the app *will
 * do* — never what the device currently is. A missing [Network] capability says
 * "this engine does not reach out", which is a fact about the selection; it is
 * not a claim that the device is offline.
 */
enum class EngineCapability {
  /** The engine sends requests over the network (a remote / cloud endpoint). */
  Network,

  /** A language model produces the content. */
  Llm,

  /** The engine may call tools (lookup / web search) on the model's behalf. */
  Tools,
}

/**
 * The capabilities implied by choosing this mode. [EngineMode.Lite] runs the
 * built-in question library entirely on device, so it has none; the LLM modes
 * all run a model locally; only [EngineMode.Remote] egresses, because it talks
 * to a cloud or localhost endpoint.
 *
 * [EngineCapability.Tools] is absent from every mode until the agentic-lookup
 * work lands a tool registry — at which point a Koog backend can report its own
 * capability rather than this static mapping.
 */
val EngineMode.capabilities: Set<EngineCapability>
  get() = when (this) {
    EngineMode.Lite -> emptySet()
    EngineMode.Remote -> setOf(EngineCapability.Network, EngineCapability.Llm)
    EngineMode.OnDevice, EngineMode.Downloaded -> setOf(EngineCapability.Llm)
  }
