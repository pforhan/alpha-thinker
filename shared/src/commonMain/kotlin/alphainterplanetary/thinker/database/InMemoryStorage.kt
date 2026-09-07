package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Project

/**
 * In-memory [Storage] implementation (e.g. as a test double).
 *
 * The web targets (`js`/`wasmJs`) now back [Storage] with Room/SQLite via `sqlite-web` and
 * `WebWorkerSQLiteDriver`, persisting to OPFS so data survives page reloads (see
 * IMPLEMENTATION-PLAN.md line 89).
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