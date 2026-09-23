package alphainterplanetary.thinker.engine

/**
 * Produces the [PlanningEngine] for one generation task, resolving the app's
 * current settings and freezing that selection for the run (engine "lock-in").
 * The settings can change while a queue of tasks waits; each task keeps the
 * engine it was created under so a backlog stays internally coherent, and a
 * fresh task — e.g. a re-run after a failure — re-resolves and picks up the
 * current selection.
 *
 * Implemented by DI: the selector reads [alphainterplanetary.thinker.repository.SettingsRepository]
 * at creation time (i.e. when the task is enqueued), resolves via
 * [resolveSelectedEngine], and applies the logging / slow-down decoration to
 * the result so every interaction still flows through them.
 */
fun interface PlanningEngineSelector {
  /** The [PlanningEngine] to run for one task, resolved from the current settings. */
  fun selectedEngine(): PlanningEngine
}

/**
 * The pure selection policy: maps a chosen [EngineMode], gated by [llmEnabled],
 * to the engine that should run. [EngineMode.Lite] carries no model dependency
 * and is always available; any other mode must name a configured Koog engine,
 * and throws when none is bound so an unconfigured backend fails loudly instead
 * of silently falling back to another mode.
 *
 * Stateless with respect to time: it is the caller's job to freeze the result
 * into a task ([PlanningEngineSelector]).
 */
fun resolveSelectedEngine(
  selectedMode: EngineMode,
  llmEnabled: Boolean,
  liteEngine: PlanningEngine,
  koogEngines: Map<EngineMode, PlanningEngine>,
): PlanningEngine =
  if (!llmEnabled || selectedMode == EngineMode.Lite) {
    liteEngine
  } else {
    koogEngines[selectedMode]
      ?: throw IllegalStateException("No engine configured for engine mode $selectedMode")
  }