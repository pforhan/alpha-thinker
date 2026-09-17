package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.util.now
import androidx.room3.withWriteTransaction
import me.tatarka.inject.annotations.Inject

class RoomStorage @Inject constructor(private val database: AppDatabase) : Storage {
  override suspend fun saveProject(project: Project) {
    database.withWriteTransaction {
      database.projectDao().upsertProject(project.toEntity())

      project.rounds.forEach { round ->
        database.roundDao().upsertRound(round.toEntity())
      }

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

    val savedRoundIds = project.rounds.map { it.id }
    val orphanedRoundIds = database.roundDao()
      .getRoundsForProject(project.id)
      .map { it.id }
      .filterNot { it in savedRoundIds.toSet() }
    if (orphanedRoundIds.isNotEmpty()) {
      database.roundDao().deleteRoundsByIds(orphanedRoundIds)
    }
  }

  override suspend fun getProject(id: String): Project? {
    val data = database.projectDao().getProjectWithQuestions(id) ?: return null
    return data.toDomainModel(
      database.answerDao().getAnswersForQuestions(data.questions.map { it.id }),
      database.roundDao().getRoundsForProject(id)
    )
  }

  override suspend fun getAllProjects(): List<Project> =
    database.projectDao().getAllProjectsWithQuestions().let { rows ->
      val answers = database.answerDao()
        .getAnswersForQuestions(rows.flatMap { row -> row.questions.map { it.id } })
      val roundsByProject = database.roundDao().getAllRounds().groupBy { it.projectId }
      return rows.map { row ->
        row.toDomainModel(answers, roundsByProject[row.project.id].orEmpty())
      }
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

  override suspend fun getSetting(key: SettingsKey, default: String): String =
    database.settingsDao().getValue(key.storageKey) ?: default

  override suspend fun saveSetting(key: SettingsKey, value: String) {
    database.settingsDao().upsertValue(SettingsEntity(key = key.storageKey, value = value))
  }
}
