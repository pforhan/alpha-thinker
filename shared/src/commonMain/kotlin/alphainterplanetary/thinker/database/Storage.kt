package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.model.Project

interface Storage {
  suspend fun saveProject(project: Project): Project
  suspend fun getProject(id: String): Project?
  suspend fun getAllProjects(): List<Project>
  suspend fun deleteProject(id: String)
  suspend fun deleteAllProjects()
  suspend fun saveQuestionOrder(projectId: String, order: List<String>)
}

expect fun provideStorage(context: PlatformContext): Storage