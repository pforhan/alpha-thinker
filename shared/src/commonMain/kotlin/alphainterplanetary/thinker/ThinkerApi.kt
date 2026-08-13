package alphainterplanetary.thinker

enum class ProjectUpdateMode {
  KEEP,
  CLEAR,
  REVALIDATE;

  companion object {
    fun ofRaw(raw: Int): ProjectUpdateMode? {
      return values().firstOrNull { it.ordinal == raw }
    }
  }
}

data class ProjectDto(
  val id: String,
  val synopsis: String,
  val editableTitle: String,
  val createdAt: Long,
  val updatedAt: Long,
  val status: String,
  val questions: List<QuestionDto>,
)

data class QuestionDto(
  val id: String,
  val text: String,
  val timestamp: Long,
  val contextId: String,
  val ignoredAt: Long? = null,
  val answers: List<AnswerDto>,
)

data class AnswerDto(
  val id: Long,
  val questionId: String,
  val text: String,
  val answeredAt: Long? = null,
  val modifiedAt: Long? = null,
  val deletedAt: Long? = null,
)

/** The API interface for handling messages from the platform. */
interface ThinkerApi {
  fun createProject(synopsis: String, title: String?, callback: (Result<ProjectDto>) -> Unit)
  fun getAllProjects(callback: (Result<List<ProjectDto>>) -> Unit)
  fun getProject(id: String, callback: (Result<ProjectDto>) -> Unit)
  fun getUnansweredQuestions(projectId: String, callback: (Result<List<QuestionDto>>) -> Unit)
  fun deleteProject(id: String, callback: (Result<Unit>) -> Unit)
  fun updateAnswer(
    projectId: String,
    questionId: String,
    text: String,
    isDraft: Boolean,
    callback: (Result<Unit>) -> Unit,
  )

  fun ignoreQuestion(projectId: String, questionId: String, callback: (Result<Unit>) -> Unit)
  fun unignoreQuestion(projectId: String, questionId: String, callback: (Result<Unit>) -> Unit)
  fun deleteAnswer(
    projectId: String,
    questionId: String,
    answerId: Long,
    callback: (Result<Unit>) -> Unit,
  )

  fun updateProject(
    id: String,
    title: String,
    synopsis: String,
    updateMode: ProjectUpdateMode,
    callback: (Result<ProjectDto>) -> Unit,
  )
}
