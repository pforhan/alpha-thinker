package alphainterplanetary.thinker.engine

/**
 * The selectable planning backends (ENG-DESIGN.md "Engine modes"). `Lite` is
 * the built-in fallback ([HardcodedPlanningEngine]); the other modes host LLM
 * inference via the Koog seam ([KoogPlanningEngine]) once their backend lands.
 *
 * The persisted [key] is the durable string stored by the settings table, so
 * label or ordering changes never break a saved choice.
 */
enum class EngineMode(
  val key: String,
  val label: String,
  val description: String,
) {
  Lite(
    key = "lite",
    label = "Lite",
    description = "Built-in question library. No network or device models — always available.",
  ),
  OnDevice(
    key = "on-device",
    label = "On device",
    description = "System models (Gemini Nano / Apple Foundation) running locally.",
  ),
  Remote(
    key = "remote",
    label = "Remote",
    description = "A cloud or localhost endpoint (OpenAI-compatible / Ollama).",
  ),
  Downloaded(
    key = "downloaded",
    label = "Downloaded",
    description = "An in-process model installed on this device (LiteRT-LM).",
  ),
  ;

  /**
   * Whether this backend can be selected right now. Each LLM backend reports
   * its own runtime availability once implemented (the on-device client
   * exposes `Available / Downloadable / Downloading / Unavailable`); until
   * then only [Lite] exists, so the others gate the engine picker off.
   */
  fun available(): Boolean =
    when (this) {
      Lite -> true
      OnDevice -> OnDeviceLLMClient().available
      Remote, Downloaded -> false
    }

  companion object {
    /** A fresh install runs the built-in engine. */
    val Default: EngineMode = Lite

    /** Resolves a stored string key back to a mode; null for unknown values. */
    fun fromKey(key: String): EngineMode? = entries.find { it.key == key }
  }
}