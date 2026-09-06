package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.model.Project

/**
 * In-memory [Storage] for the web target.
 *
 * Room/SQLite is not available on wasmJs, so the web build keeps data in process memory only.
 * This means data does not survive a page reload. See IMPLEMENTATION-PLAN.md Phase 2.6 for a
 * follow-up to back web storage with `localStorage` (or similar) so it persists.
 */
class InMemoryStorage(
  val projects: MutableMap<String, Project> = mutableMapOf(),
) : Storage {
  override suspend fun saveProject(project: Project): Project {
    projects[project.id] = project
    return project
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
    projects[projectId] = current.copy(questions = order.mapNotNull { byId[it] })
  }
}

private var storageInstance: Storage = InMemoryStorage()

actual fun provideStorage(context: PlatformContext): Storage = storageInstance
