package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.model.Project

/**
 * Well-known app-wide setting keys. The durable [storageKey] string is what's
 * persisted in the settings table, so renames are safe.
 */
enum class SettingsKey(val storageKey: String) {
  PhaseTheme("phase-theme"),
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

expect fun provideStorage(context: PlatformContext): Storage