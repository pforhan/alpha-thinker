package alphainterplanetary.thinker.engine

/**
 * The platform seam for in-process local inference.
 *
 * LiteRT-LM publishes a real client only for Android, so the capability flags
 * are how common code asks whether the Local tier exists on this build: desktop,
 * iOS, and web keep [inferenceSupported] false and the picker leaves the tier
 * disabled. [createBackend] is therefore only called after the flag is checked,
 * but it still fails loudly on an unsupported platform rather than returning a
 * backend that cannot run.
 *
 * File checks live here as well because an installed model's availability is
 * about the platform's storage, not about the repository row that names it
 * (see `EngineCatalog`). They are harmless on unsupported platforms.
 */
expect object LocalModelRuntime {
  /** Whether this build can run a `.litertlm` model in process. */
  val inferenceSupported: Boolean

  /** Whether this build can download model files into app storage. */
  val downloadsSupported: Boolean

  /**
   * Builds the Koog backend for [fileName] under [directory]. The platform
   * implementation owns the client it creates, so closing the backend releases
   * the runtime resources.
   */
  fun createBackend(
    fileName: String,
    directory: String,
    contextTokens: Int,
  ): KoogPlanningBackend

  /**
   * Builds a token-streaming backend for [fileName] under [directory], or null
   * when this platform has no runtime that can expose deltas.
   *
   * The evaluation harness prefers this path so it can abort a repetition loop
   * while the model is still generating; the ordinary planning path keeps using
   * [createBackend], which returns one complete reply.
   */
  fun createStreamingBackend(
    fileName: String,
    directory: String,
    contextTokens: Int,
  ): LocalStreamingBackend?

  /** Whether a model file exists at [path]. */
  fun fileExists(path: String): Boolean

  /** Deletes the file at [path]; returns whether it no longer exists. */
  fun deleteFile(path: String): Boolean
}
