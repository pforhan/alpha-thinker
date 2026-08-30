package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.repository.ProjectRepository.Storage
import kotlinx.datetime.Instant
import me.tatarka.inject.annotations.Inject

class RoomStorage @Inject constructor(private val database: AppDatabase) : Storage {
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
    val data = database.projectDao().getProjectWithQuestions(id) ?: return null
    val questions = data.questions.map { question ->
      question.toDomainModel(database.answerDao().getAnswersForQuestion(question.id))
    }
    return data.project.toDomainModel(questions)
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

private fun ProjectEntity.toDomainModel(questions: List<Question>): Project {
  return Project(
    id = id,
    synopsis = synopsis,
    editableTitle = editableTitle,
    status = status,
    questions = questions,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt)
  )
}

private fun QuestionEntity.toDomainModel(answers: List<AnswerEntity>): Question {
  return Question(
    id = id,
    text = text,
    timestamp = Instant.fromEpochMilliseconds(createdAt),
    contextId = "",
    ignoredAt = ignoredAt?.let { Instant.fromEpochMilliseconds(it) },
    answers = answers.map { it.toDomainModel() }
  )
}

private fun AnswerEntity.toDomainModel() = Answer(
  id = id,
  questionId = questionId,
  text = text,
  answeredAt = answeredAt?.let { Instant.fromEpochMilliseconds(it) },
  modifiedAt = modifiedAt?.let { Instant.fromEpochMilliseconds(it) },
  deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) }
)
