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
