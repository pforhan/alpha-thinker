package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.util.randomUUID

/**
 * A user-created engine: one configured connection (an [EngineKind.Remote] with
 * a [provider], endpoint, key, and model) or one downloaded local model (an
 * [EngineKind.LocalFile] with a [localPath]). Code-defined backends have no row
 * here — they are described by the catalog, not stored.
 *
 * The fields are a superset: a Remote instance reads [baseUrl], [apiKey],
 * [model], and [contextTokens], while a Local file instance reads [localPath].
 * Which fields matter is decided by [kind], so the others keep their defaults.
 */
data class EngineInstance(
  val id: String,
  val kind: EngineKind,
  /** Remote only: which protocol the connection speaks. */
  val provider: RemoteProvider?,
  /** The user-facing name shown in the picker. */
  val label: String,
  val baseUrl: String = "",
  val apiKey: String = "",
  val model: String = "",
  val contextTokens: Int = 0,
  /** Local file only: where the downloaded model lives. */
  val localPath: String? = null,
) {
  companion object {
    /**
     * A Remote connection seeded from [provider]'s starter endpoint, model, and
     * window — what both a fresh install and a newly created connection start
     * from, so there is one definition of "a configured connection" rather than
     * one per call site.
     */
    fun remote(
      provider: RemoteProvider = RemoteProvider.Default,
      label: String = provider.label,
    ): EngineInstance = EngineInstance(
      id = randomUUID(),
      kind = EngineKind.Remote,
      provider = provider,
      label = label,
      baseUrl = provider.defaultBaseUrl,
      model = provider.defaultModel,
      contextTokens = provider.defaultContextTokens,
    )

    /**
     * The Remote connection a fresh install starts from: the app's documented
     * local target ([RemoteProvider.Default], Ollama) with its starter endpoint,
     * model, and window. The repository seeds one so the picker always offers
     * something configured rather than an empty list on first run.
     */
    fun defaultRemote(): EngineInstance = remote()
  }
}
