package alphainterplanetary.thinker.engine

/**
 * The per-engine state that outlives the configuration itself: whether an
 * [EngineRef] is the app's current selection, and when it was last used. It is
 * keyed by ref, so a built-in gets a row only once the user picks it — the
 * catalog stays authoritative for what exists, and this table only remembers
 * what has been touched.
 *
 * Lives beside the engine model rather than in the database layer so the
 * repository, the catalog, and the persistence layer can share one shape.
 */
data class EngineState(
  val ref: EngineRef,
  /** True for the one engine the app will run. At most one row is selected. */
  val selected: Boolean = false,
  /** When this engine last ran a task, or null if it never has. */
  val lastUsedAt: Long? = null,
)
