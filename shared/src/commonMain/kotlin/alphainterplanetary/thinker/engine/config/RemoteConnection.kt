package alphainterplanetary.thinker.engine.config

/**
 * Connection details for a remote LLM backend.
 */
data class RemoteConnection(
  val provider: RemoteProvider,
  val baseUrl: String,
  val apiKey: String,
  val model: String,
  val contextLength: Int,
)
