package alphainterplanetary.thinker.engine

/**
 * Whether an engine can be run right now.
 */
sealed interface EngineAvailability {
  /** The engine is ready to use. */
  data object Available : EngineAvailability
  /**
   * The engine is not ready. [reason] provides a user-facing explanation.
   */
  data class Unavailable(val reason: String) : EngineAvailability
}
