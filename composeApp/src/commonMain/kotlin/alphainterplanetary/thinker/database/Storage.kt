package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Project

interface Storage {
  suspend fun saveProject(project: Project): Project
  suspend fun getProject(id: String): Project?
  suspend fun getAllProjects(): List<Project>
  suspend fun deleteProject(id: String)
  suspend fun deleteAllProjects()
  suspend fun saveQuestionOrder(projectId: String, order: List<String>)
}