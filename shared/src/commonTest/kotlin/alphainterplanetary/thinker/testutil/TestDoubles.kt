package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question

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

class FakeGenerator : QuestionGenerator {
  var recommendedTitle: String = "Recommended"
  val initialQuestions: MutableList<Question> = mutableListOf()
  val followUpQuestions: MutableList<Question> = mutableListOf()
  var initialCalls: MutableList<InitialCall> = mutableListOf()
  var followUpCalls: MutableList<FollowUpCall> = mutableListOf()

  override suspend fun recommendTitle(synopsis: String): String = recommendedTitle

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    contextId: String,
  ): List<Question> {
    initialCalls += InitialCall(editableTitle, synopsis, contextId)
    return initialQuestions
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    contextId: String,
  ): List<Question> {
    followUpCalls += FollowUpCall(synopsis, previousQuestions, contextId)
    return followUpQuestions
  }

  data class InitialCall(
    val editableTitle: String,
    val synopsis: String,
    val contextId: String,
  )

  data class FollowUpCall(
    val synopsis: String,
    val previousQuestions: List<Question>,
    val contextId: String,
  )
}
