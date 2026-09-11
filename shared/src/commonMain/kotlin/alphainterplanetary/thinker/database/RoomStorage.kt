package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.util.now
import androidx.room3.withWriteTransaction
import kotlin.time.Instant
import me.tatarka.inject.annotations.Inject

class RoomStorage @Inject constructor(private val database: AppDatabase) : Storage {
  override suspend fun saveProject(project: Project) {
    database.withWriteTransaction {
      database.projectDao().upsertProject(project.toEntity())

      project.questions.forEachIndexed { index, q ->
        database.questionDao().upsertQuestion(q.toEntity(project.id, index))
      }

      reconcileChildren(project)
    }
  }

  /**
   * Brings the questions and answers tables back in line with the saved
   * aggregate: answers already stored (immutable history) are left untouched,
   * only new versions are inserted, and any question/answer rows not present in
   * the aggregate are deleted so a projected question can never orphan its rows.
   */
  private suspend fun reconcileChildren(project: Project) {
    val savedQuestionIds = project.questions.map { it.id }
    val savedAnswerIds = project.questions.flatMap { it.answers }.map { it.id }.toSet()

    val existingAnswerIds = database.answerDao()
      .getAnswersForQuestions(savedQuestionIds)
      .map { it.id }
      .toSet()

    project.questions
      .flatMap { it.answers }
      .filterNot { it.id in existingAnswerIds }
      .forEach { a ->
        database.answerDao().upsertAnswer(a.toEntity())
      }

    val existingQuestionIds = database.questionDao()
      .getQuestionsForProject(project.id)
      .map { it.id }
    val orphanedQuestionIds = existingQuestionIds.filterNot { it in savedQuestionIds.toSet() }
    if (orphanedQuestionIds.isNotEmpty()) {
      database.questionDao().deleteQuestionsByIds(orphanedQuestionIds)
    }

    val orphanedAnswerIds = existingAnswerIds.filterNot { it in savedAnswerIds }
    if (orphanedAnswerIds.isNotEmpty()) {
      database.answerDao().deleteAnswersByIds(orphanedAnswerIds)
    }
  }

  override suspend fun getProject(id: String): Project? {
    val data = database.projectDao().getProjectWithQuestions(id) ?: return null
    return data.toDomainModel(
      database.answerDao().getAnswersForQuestions(data.questions.map { it.id })
    )
  }

  override suspend fun getAllProjects(): List<Project> =
    database.projectDao().getAllProjectsWithQuestions().let { rows ->
      val answers = database.answerDao()
        .getAnswersForQuestions(rows.flatMap { row -> row.questions.map { it.id } })
      return rows.map { row -> row.toDomainModel(answers) }
    }

  override suspend fun deleteProject(id: String) {
    database.projectDao().deleteProject(id)
  }

  override suspend fun deleteAllProjects() {
    database.projectDao().deleteAllProjects()
  }

  override suspend fun saveQuestionOrder(projectId: String, order: List<String>) {
    database.withWriteTransaction {
      database.questionDao().updateSortOrderForProject(projectId, order)
      database.projectDao().updateProjectUpdatedAt(projectId, now().toEpochMilliseconds())
    }
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

private fun Question.toEntity(projectId: String, index: Int) = QuestionEntity(
  id = id,
  projectId = projectId,
  text = text,
  contextId = contextId,
  createdAt = timestamp.toEpochMilliseconds(),
  sortOrder = index,
  ignoredAt = ignoredAt?.toEpochMilliseconds(),
  answerId = answerId,
  draftText = draftText,
  draftUpdatedAt = draftUpdatedAt?.toEpochMilliseconds(),
)

private fun Answer.toEntity() = AnswerEntity(
  id = id,
  questionId = questionId,
  text = text,
  createdAt = createdAt.toEpochMilliseconds(),
)

private fun ProjectWithQuestions.toDomainModel(answers: List<AnswerEntity>): Project =
  answers.groupBy { it.questionId }.let { answersByQuestionId ->
    Project(
      id = project.id,
      synopsis = project.synopsis,
      editableTitle = project.editableTitle,
      status = project.status,
      questions = questions
        .sortedBy { it.sortOrder }
        .map { question -> question.toDomainModel(answersByQuestionId[question.id].orEmpty()) },
      createdAt = Instant.fromEpochMilliseconds(project.createdAt),
      updatedAt = Instant.fromEpochMilliseconds(project.updatedAt)
    )
  }

private fun QuestionEntity.toDomainModel(answers: List<AnswerEntity>): Question = Question(
  id = id,
  text = text,
  timestamp = Instant.fromEpochMilliseconds(createdAt),
  contextId = contextId,
  ignoredAt = ignoredAt?.let { Instant.fromEpochMilliseconds(it) },
  answerId = answerId,
  draftText = draftText,
  draftUpdatedAt = draftUpdatedAt?.let { Instant.fromEpochMilliseconds(it) },
  answers = answers.map { it.toDomainModel() }
)

private fun AnswerEntity.toDomainModel() = Answer(
  id = id,
  questionId = questionId,
  text = text,
  createdAt = Instant.fromEpochMilliseconds(createdAt)
)