package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.repository.ProjectRepository.Storage
import kotlinx.datetime.Instant

class RoomStorage(private val database: AppDatabase) : Storage {
  override suspend fun saveProject(project: Project): Project {
    database.projectDao().upsertProject(project.toEntity())

    project.questions.forEach { q ->
      database.questionDao().upsertQuestion(q.toEntity(project.id))
    }

    project.questions.flatMap { it.answers }.forEach { a ->
      database.answerDao().upsertAnswer(a.toEntity())
    }

    return project
  }

  override suspend fun getProject(id: String): Project? {
    return database.projectDao().getProjectWithQuestions(id)?.toDomainModel()
  }

  override suspend fun getAllProjects(): List<Project> {
    return database.projectDao().getAllProjects().map { entity ->
      Project(
        id = entity.id,
        synopsis = entity.synopsis,
        editableTitle = entity.editableTitle,
        status = entity.status,
        questions = emptyList(),
        createdAt = Instant.fromEpochMilliseconds(entity.createdAt),
        updatedAt = Instant.fromEpochMilliseconds(entity.updatedAt)
      )
    }
  }

  override suspend fun deleteProject(id: String) {
    val entity = database.projectDao().getProjectById(id)
    if (entity != null) {
      database.projectDao().deleteProject(entity)
    }
  }

  override suspend fun deleteAllProjects() {
    database.projectDao().deleteAllProjects()
  }
}

private fun Project.toEntity() = ProjectEntity(
  id = id,
  synopsis = synopsis,
  editableTitle = editableTitle,
  createdAt = createdAt.toEpochMilliseconds(),
  updatedAt = updatedAt.toEpochMilliseconds(),
  status = status
)

private fun Question.toEntity(projectId: String) = QuestionEntity(
  id = id,
  projectId = projectId,
  text = text,
  createdAt = timestamp.toEpochMilliseconds(),
  ignoredAt = ignoredAt?.toEpochMilliseconds()
)

private fun Answer.toEntity() = AnswerEntity(
  questionId = questionId,
  text = text,
  answeredAt = answeredAt?.toEpochMilliseconds(),
  modifiedAt = modifiedAt?.toEpochMilliseconds(),
  deletedAt = deletedAt?.toEpochMilliseconds()
)

private fun ProjectWithQuestions.toDomainModel() = Project(
  id = project.id,
  synopsis = project.synopsis,
  editableTitle = project.editableTitle,
  status = project.status,
  questions = questions.map { it.toDomainModel() },
  createdAt = Instant.fromEpochMilliseconds(project.createdAt),
  updatedAt = Instant.fromEpochMilliseconds(project.updatedAt)
)

private fun QuestionWithAnswers.toDomainModel() = Question(
  id = question.id,
  text = question.text,
  timestamp = Instant.fromEpochMilliseconds(question.createdAt),
  contextId = "",
  ignoredAt = question.ignoredAt?.let { Instant.fromEpochMilliseconds(it) },
  answers = answers.map { it.toDomainModel() }
)

private fun AnswerEntity.toDomainModel() = Answer(
  id = id,
  questionId = questionId,
  text = text,
  answeredAt = answeredAt?.let { Instant.fromEpochMilliseconds(it) },
  modifiedAt = modifiedAt?.let { Instant.fromEpochMilliseconds(it) },
  deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) }
)
