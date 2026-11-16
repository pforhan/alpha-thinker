package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.engine.config.EngineInstance
import alphainterplanetary.thinker.engine.config.EngineRef
import alphainterplanetary.thinker.engine.config.EngineSelectionState
import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [alphainterplanetary.thinker.database.Storage] fake for tests.
 */
class FakeStorage(
  val projects: MutableMap<String, Project> = mutableMapOf(),
  val settings: MutableMap<String, String> = mutableMapOf(),
  val engineInstances: MutableMap<String, EngineInstance> = mutableMapOf(),
  val engineSelectionStates: MutableMap<String, EngineSelectionState> = mutableMapOf(),
  val exportArtifacts: MutableMap<String, ExportArtifact> = mutableMapOf(),
) : Storage {
  private val exportFlows = MutableStateFlow(exportArtifacts.toMap())

  private fun publishExports() {
    exportFlows.value = exportArtifacts.toMap()
  }

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

  override suspend fun getEngineSelectionStates(): List<EngineSelectionState> =
    engineSelectionStates.values.toList()

  override suspend fun upsertEngineSelectionState(state: EngineSelectionState) {
    engineSelectionStates[state.ref.storageKey] = state
  }

  override suspend fun deleteEngineSelectionState(ref: EngineRef) {
    engineSelectionStates.remove(ref.storageKey)
  }

  override suspend fun setSelectedEngine(ref: EngineRef) {
    val key = ref.storageKey
    engineSelectionStates.keys.toList().forEach { storedKey ->
      engineSelectionStates[storedKey] =
        engineSelectionStates.getValue(storedKey).copy(selected = storedKey == key)
    }
    if (key !in engineSelectionStates) {
      engineSelectionStates[key] = EngineSelectionState(ref = ref, selected = true)
    }
  }

  override suspend fun saveExportArtifact(artifact: ExportArtifact) {
    exportArtifacts[artifact.id] = artifact
    publishExports()
  }

  override suspend fun getExportArtifact(id: String): ExportArtifact? = exportArtifacts[id]

  override fun observeExportArtifacts(projectId: String): Flow<List<ExportArtifact>> =
    exportFlows.map { all ->
      all.values.filter { it.projectId == projectId }.sortedByDescending { it.createdAt }
    }

  override suspend fun getExportArtifacts(projectId: String): List<ExportArtifact> =
    exportArtifacts.values.filter { it.projectId == projectId }.sortedByDescending { it.createdAt }

  override suspend fun deleteExportArtifact(id: String) {
    exportArtifacts.remove(id)
    publishExports()
  }
}
