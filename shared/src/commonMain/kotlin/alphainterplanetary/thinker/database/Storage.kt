package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.model.Project

/**
 * Well-known app-wide setting keys. The durable [storageKey] string is what's
 * persisted in the settings table, so renames are safe.
 */
enum class SettingsKey(val storageKey: String) {
  PhaseTheme("phase-theme"),
  EngineMode("engine-mode"),
  SlowDownPlanningEngine("slow-down-planning-engine"),
  EngineRecommendTitleDelay("engine-delay-recommend-title"),
  EngineQuestionGenerationDelay("engine-delay-question-generation"),
  ActivityLoggerRetentionDays("activity-log-retention-days"),
  AnnouncedFailureActivityId("announced-failure-activity-id"),
  RemoteLlmBaseUrl("remote-llm-base-url"),
  RemoteLlmApiKey("remote-llm-api-key"),
  RemoteLlmModel("remote-llm-model"),
  RemoteLlmContextTokens("remote-llm-context-tokens"),

  /**
   * The id of the project the project simulator last created, so a later run
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
}

/**
 * The app's [Storage] over the shared Room database. DI memoizes it under
 * `@AppScope`, so this builds the whole persistence stack exactly once.
 */
fun provideStorage(context: PlatformContext): Storage =
  RoomStorage(getRoomDatabase(provideDatabaseBuilder(context)))
