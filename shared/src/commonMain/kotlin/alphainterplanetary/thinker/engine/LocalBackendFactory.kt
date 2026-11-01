package alphainterplanetary.thinker.engine

/**
 * Builds the backend for one downloaded [EngineInstance].
 *
 * A seam rather than a direct call to [LocalModelRuntime] so the registry can
 * be exercised with a fake backend in tests and so the platform runtime stays
 * the only place that knows about LiteRT types.
 */
fun interface LocalBackendFactory {
  fun create(instance: EngineInstance): KoogPlanningBackend
}

/** The production factory: resolves the instance to its platform runtime. */
object DefaultLocalBackendFactory : LocalBackendFactory {
  override fun create(instance: EngineInstance): KoogPlanningBackend {
    check(LocalModelRuntime.inferenceSupported) {
      "Local model inference is not supported on this platform"
    }
    val path = requireNotNull(instance.localPath) {
      "Local model instance ${instance.id} has no file path"
    }
    val fileName = instance.model.ifBlank { path.substringAfterLast('/') }
    val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
    return LocalModelRuntime.createBackend(
      fileName = fileName,
      directory = directory,
      contextTokens = instance.contextTokens,
    )
  }
}
