package alphainterplanetary.thinker.engine

/**
 * One in-process inference whose text arrives in pieces.
 *
 * The evaluation harness uses this seam instead of [KoogPlanningBackend] so it
 * can watch the reply as it is generated: a model that starts repeating itself
 * is stopped in seconds rather than at the request timeout, and time to first
 * token is measured instead of inferred from the wall time.
 */
interface LocalStreamingBackend {
  /**
   * Runs [text] and hands each text delta to [onToken] as it arrives.
   *
   * [onToken] may throw to abort the generation; the implementation must cancel
   * the runtime and rethrow, so a repetition loop does not keep a native
   * generation running after the caller has given up on it.
   */
  suspend fun execute(text: String, onToken: (String) -> Unit): LocalStreamingReply

  fun close()
}

/** The measurements a streaming runtime can report for one request. */
data class LocalStreamingReply(
  val text: String,
  val inputTokens: Int? = null,
  val outputTokens: Int? = null,
  val firstTokenS: Double? = null,
  val promptEvalS: Double? = null,
  val evalS: Double? = null,
  val genTps: Double? = null,
)

/** Builds a [LocalStreamingBackend] for one installed model. */
fun interface LocalStreamingBackendFactory {
  fun create(instance: EngineInstance): LocalStreamingBackend?
}

/**
 * The production factory: resolves the instance to its platform runtime, or
 * null on a platform without in-process inference.
 *
 * The evaluation harness falls back to the non-streaming [LocalBackendFactory]
 * when this returns null, which is what keeps the desktop test suite able to
 * exercise the orchestration with a fake Koog client.
 */
object DefaultLocalStreamingBackendFactory : LocalStreamingBackendFactory {
  override fun create(instance: EngineInstance): LocalStreamingBackend? {
    if (!LocalModelRuntime.inferenceSupported) return null
    val path = requireNotNull(instance.localPath) {
      "Local model instance ${instance.id} has no file path"
    }
    val fileName = instance.model.ifBlank { path.substringAfterLast('/') }
    val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
    return LocalModelRuntime.createStreamingBackend(
      fileName = fileName,
      directory = directory,
      contextTokens = instance.contextTokens,
    )
  }
}
