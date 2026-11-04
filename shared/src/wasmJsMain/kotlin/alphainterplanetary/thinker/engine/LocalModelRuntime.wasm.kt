package alphainterplanetary.thinker.engine

/** Web has no in-process LiteRT runtime, so the Local tier stays disabled. */
actual object LocalModelRuntime {
  actual val inferenceSupported: Boolean = false
  actual val downloadsSupported: Boolean = false

  actual fun createBackend(
    fileName: String,
    directory: String,
    contextTokens: Int,
  ): KoogPlanningBackend = throw IllegalStateException(
    "Local model inference is not supported on web",
  )

  actual fun createStreamingBackend(
    fileName: String,
    directory: String,
    contextTokens: Int,
  ): LocalStreamingBackend? = null

  actual fun fileExists(path: String): Boolean = false

  actual fun deleteFile(path: String): Boolean = false
}
