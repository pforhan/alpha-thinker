package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.util.now

/**
 * In-memory [alphainterplanetary.thinker.database.Storage] fake for tests.
 */
class FakeStorage(
  val projects: MutableMap<String, Project> = mutableMapOf(),
  val settings: MutableMap<String, String> = mutableMapOf(),
) : Storage {
  override suspend fun saveProject(project: Project) {
    projects[project.id] = project
  }

  override suspend fun getProject(id: String): Project? = projects[id]

  override suspend fun getAllProjects(): List<Project> = projects.values.toList()

  override suspend fun deleteProject(id: String) {
    projects.remove(id)
  }

  override suspend fun deleteAllProjects() {
    projects.clear()
  }

  override suspend fun saveQuestionOrder(projectId: String, order: List<String>) {
    val current = projects[projectId] ?: return
    val byId = current.questions.associateBy { it.id }
    projects[projectId] = current.copy(
      questions = order.mapNotNull { byId[it] },
      updatedAt = now()
    )
  }

  override suspend fun getSetting(key: SettingsKey, default: String): String =
    settings[key.storageKey] ?: default

  override suspend fun saveSetting(key: SettingsKey, value: String) {
    settings[key.storageKey] = value
  }
}