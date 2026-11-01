package alphainterplanetary.thinker.engine

/**
 * Desktop has no LiteRT-LM runtime yet (see IMPLEMENTATION-PLAN.md phase 5), so
 * the Local tier stays disabled here while the rest of the app compiles against
 * the same seam.
 */
actual object LocalModelRuntime {
  actual val inferenceSupported: Boolean = false
  actual val downloadsSupported: Boolean = false

  actual fun createBackend(
    fileName: String,
    directory: String,
    contextTokens: Int,
  ): KoogPlanningBackend = throw IllegalStateException(
    "Local model inference is not supported on desktop",
  )

  actual fun fileExists(path: String): Boolean = false

  actual fun deleteFile(path: String): Boolean = false
}
