package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.EngineState
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.util.now

/**
 * In-memory [alphainterplanetary.thinker.database.Storage] fake for tests.
 */
class FakeStorage(
  val projects: MutableMap<String, Project> = mutableMapOf(),
  val settings: MutableMap<String, String> = mutableMapOf(),
  val engineInstances: MutableMap<String, EngineInstance> = mutableMapOf(),
  val engineStates: MutableMap<String, EngineState> = mutableMapOf(),
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

  override suspend fun getEngineInstances(): List<EngineInstance> = engineInstances.values.toList()

  override suspend fun upsertEngineInstance(instance: EngineInstance) {
    engineInstances[instance.id] = instance
  }

  override suspend fun deleteEngineInstance(id: String) {
    engineInstances.remove(id)
  }

  override suspend fun getEngineStates(): List<EngineState> = engineStates.values.toList()

  override suspend fun upsertEngineState(state: EngineState) {
    engineStates[state.ref.storageKey] = state
  }

  override suspend fun setSelectedEngine(ref: EngineRef) {
    val key = ref.storageKey
    engineStates.keys.toList().forEach { storedKey ->
      engineStates[storedKey] = engineStates.getValue(storedKey).copy(selected = storedKey == key)
    }
    if (key !in engineStates) {
      engineStates[key] = EngineState(ref = ref, selected = true)
    }
  }
}
