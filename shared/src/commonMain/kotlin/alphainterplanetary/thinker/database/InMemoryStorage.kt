package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Project

/**
 * In-memory [Storage] used on web targets (and handy as a test double).
 *
 * Room/SQLite has no runnable storage on js/wasmJs in this build — the shared Room layer
 * compiles for every target, but web keeps data in process memory only. This means data does
 * not survive a page reload. See IMPLEMENTATION-PLAN.md Phase 2.6 for the follow-up to back
 * web storage with `sqlite-web` (`WebWorkerSQLiteDriver`) so it persists via OPFS.
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