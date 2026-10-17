package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.EngineState
import alphainterplanetary.thinker.model.Project

/**
 * Well-known app-wide setting keys. The durable [storageKey] string is what's
 * persisted in the settings table, so renames are safe.
 */
enum class SettingsKey(val storageKey: String) {
  PhaseTheme("phase-theme"),
  SlowDownPlanningEngine("slow-down-planning-engine"),
  EngineTitleRecommendationDelay("engine-delay-recommend-title"),
  EngineQuestionGenerationDelay("engine-delay-question-generation"),
  ActivityLoggerRetentionDays("activity-log-retention-days"),
  AnnouncedFailureActivityId("announced-failure-activity-id"),

  /**
   * The id of the project the simulator last created, so a later run
   * replaces it. A setting rather than a marker on the project, because the
   * thing being remembered is "which project this tool made", not a state of
   * the project — the same reasoning as [AnnouncedFailureActivityId].
   */
  SimulatedProjectId("simulated-project-id"),
}

interface Storage {
  suspend fun saveProject(project: Project)
  suspend fun getProject(id: String): Project?
  suspend fun getAllProjects(): List<Project>
  suspend fun deleteProject(id: String)
  suspend fun deleteAllProjects()
  suspend fun saveQuestionOrder(projectId: String, order: List<String>)

  /** Reads an app-wide string setting by key, returning [default] when unset. */
  suspend fun getSetting(key: SettingsKey, default: String): String

  /** Persists an app-wide string setting by key. */
  suspend fun saveSetting(key: SettingsKey, value: String)

  /** All user-created engine instances. Code-defined built-ins are not rows. */
  suspend fun getEngineInstances(): List<EngineInstance>

  /** Inserts or replaces one engine instance, addressed by its id. */
  suspend fun upsertEngineInstance(instance: EngineInstance)

  /** Deletes the engine instance with [id], if it exists. */
  suspend fun deleteEngineInstance(id: String)

  /** The selection/usage state of every engine that has one. */
  suspend fun getEngineStates(): List<EngineState>

  /** Inserts or replaces one engine's state row, addressed by its ref. */
  suspend fun upsertEngineState(state: EngineState)

  /** Marks [ref] as the sole selected engine, clearing every other selection. */
  suspend fun setSelectedEngine(ref: EngineRef)
}

/**
 * The app's [Storage] over the shared Room database. DI memoizes it under
 * `@AppScope`, so this builds the whole persistence stack exactly once.
 */
fun provideStorage(context: PlatformContext): Storage =
  RoomStorage(getRoomDatabase(provideDatabaseBuilder(context)))
