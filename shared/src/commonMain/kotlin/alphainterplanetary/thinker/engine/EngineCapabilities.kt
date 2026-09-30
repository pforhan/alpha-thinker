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

  /**
   * A language model produces the content.
   *
   * Also the reason a generation prompt has a context window at all, which is
   * why there is no separate window capability: an engine that composes no
   * prompt draws its output from a library instead and sends nothing the user
   * wrote. The window itself is a fact about the engine, not the mode — see
   * [PlanningEngine.contextWindowTokens].
   */
  Llm,

  /** The engine may call tools (lookup / web search) on the model's behalf. */
  Tools,
}
