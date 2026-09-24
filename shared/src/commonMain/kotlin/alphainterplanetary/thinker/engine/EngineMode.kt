package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogSource

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
  val logSource: LogSource,
) {
  Lite(
    key = "lite",
    label = "Lite",
    description = "Built-in question library. No network or device models — always available.",
    logSource = LogSource.Lite,
  ),
  OnDevice(
    key = "on-device",
    label = "On device",
    description = "System models (Gemini Nano / Apple Foundation) running locally.",
    logSource = LogSource.LocalLLM,
  ),
  Remote(
    key = "remote",
    label = "Remote",
    description = "A cloud or localhost endpoint (OpenAI-compatible / Ollama).",
    logSource = LogSource.RemoteLLM,
  ),
  Downloaded(
    key = "downloaded",
    label = "Downloaded",
    description = "An in-process model installed on this device (LiteRT-LM).",
    logSource = LogSource.LocalLLM,
  ),
  ;

  /**
   * Whether this backend can be selected right now. [Lite] is always
   * available, and [Remote] is selectable once configured — it talks to any
   * OpenAI-compatible endpoint from the Settings fields. The on-device
   * backends report their runtime availability once implemented (the
   * on-device client exposes `Available / Downloadable / Downloading /
   * Unavailable`); until then they gate the engine picker off.
   */
  fun available(): Boolean =
    when (this) {
      Lite -> true
      Remote -> true
      OnDevice, Downloaded -> false
    }

  companion object {
    /** A fresh install runs the built-in engine. */
    val Default: EngineMode = Lite

    /** Resolves a stored string key back to a mode; null for unknown values. */
    fun fromKey(key: String): EngineMode? = entries.find { it.key == key }
  }
}