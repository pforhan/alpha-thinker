package alphainterplanetary.thinker.engine

/**
 * Produces the [PlanningEngine] for one generation task, resolving the app's
 * current settings and freezing that selection for the run (engine "lock-in").
 * The settings can change while a queue of tasks waits; each task keeps the
 * engine it was created under so a backlog stays internally coherent, and a
 * fresh task — e.g. a re-run after a failure — re-resolves and picks up the
 * current selection.
 */
fun interface PlanningEngineSelector {
  /** The [PlanningEngine] to run for one task, resolved from the current settings. */
  fun selectedEngine(): PlanningEngine
}
