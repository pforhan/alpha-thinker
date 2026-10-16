package alphainterplanetary.thinker.engine

/**
 * Identifies one selectable engine. An [EngineKind] is only the *archetype*
 * (Offline / On device / Local file / Remote); a ref is the specific thing the
 * user has selected, which for a Remote backend is one configured endpoint
 * rather than "Remote in general" (see ENGINE-CONFIG-PLAN.md).
 *
 * Two refs anchor the model:
 *
 * - [Builtin] is a code-defined backend with no stored configuration — the
 *   built-in `Offline` library, the system on-device runtime, and (once it
 *   exists) the free Alpha Interplanetary backend.
 * - [Instance] is a row in `engine_instances`, a user-created connection or a
 *   downloaded local model.
 *
 * The durable [storageKey] is what `engine_state` is keyed by, so selection and
 * last-use survive as long as the underlying kind key or instance id does.
 */
sealed interface EngineRef {
  /** A code-defined backend, addressed by its [kind]. */
  data class Builtin(val kind: EngineKind) : EngineRef

  /** A user-created engine, addressed by its [id]. */
  data class Instance(val id: String) : EngineRef

  /**
   * The durable string this ref persists under. A built-in uses its kind's
   * [EngineKind.key]; an instance uses its [id]. Instance ids are UUIDs, so a
   * collision with a kind key cannot arise in practice.
   */
  val storageKey: String
    get() = when (this) {
      is Builtin -> kind.key
      is Instance -> id
    }

  companion object {
    /**
     * Resolves a stored [storageKey] back to a ref: a known kind key is a
     * [Builtin], anything else is assumed to name an [Instance]. A key that
     * names neither (a stale row for a kind or instance that no longer exists)
     * still round-trips here; callers that care resolve it against the catalog.
     */
    fun fromStorageKey(key: String): EngineRef =
      EngineKind.fromKey(key)?.let { Builtin(it) } ?: Instance(key)
  }
}
