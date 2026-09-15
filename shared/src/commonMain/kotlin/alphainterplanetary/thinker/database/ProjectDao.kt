package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

@Dao
interface ProjectDao {
  @Upsert
  suspend fun upsertProject(project: ProjectEntity): Long

  @Transaction
  @Query("SELECT * FROM projects WHERE id = :id")
  suspend fun getProjectWithQuestions(id: String): ProjectWithQuestions?

  @Transaction
  @Query("SELECT * FROM projects")
  suspend fun getAllProjectsWithQuestions(): List<ProjectWithQuestions>

  @Query("DELETE FROM projects WHERE id = :id")
  suspend fun deleteProject(id: String)

  @Query("DELETE FROM projects")
  suspend fun deleteAllProjects()

  @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :projectId")
  suspend fun updateProjectUpdatedAt(projectId: String, updatedAt: Long)
}

@Dao
interface QuestionDao {
  @Upsert
  suspend fun upsertQuestion(question: QuestionEntity): Long

  @Query("SELECT * FROM questions WHERE projectId = :projectId ORDER BY sortOrder ASC")
  suspend fun getQuestionsForProject(projectId: String): List<QuestionEntity>

  @Query("UPDATE questions SET sortOrder = :sortOrder WHERE id = :questionId AND projectId = :projectId")
  suspend fun updateSortOrder(questionId: String, projectId: String, sortOrder: Int)

  @Transaction
  suspend fun updateSortOrderForProject(projectId: String, order: List<String>) {
    order.forEachIndexed { index, questionId ->
      updateSortOrder(questionId, projectId, index)
    }
  }

  @Query("DELETE FROM questions WHERE id IN (:questionIds)")
  suspend fun deleteQuestionsByIds(questionIds: List<String>)
}

@Dao
interface AnswerDao {
  @Upsert
  suspend fun upsertAnswer(answer: AnswerEntity): Long

  @Query("SELECT * FROM answers WHERE questionId IN (:questionIds) ORDER BY createdAt ASC, id ASC")
  suspend fun getAnswersForQuestions(questionIds: List<String>): List<AnswerEntity>

  @Query("DELETE FROM answers WHERE id IN (:answerIds)")
  suspend fun deleteAnswersByIds(answerIds: List<String>)
}

@Dao
interface RoundDao {
  @Upsert
  suspend fun upsertRound(round: RoundEntity): Long

  @Query("SELECT * FROM rounds WHERE id = :id")
  suspend fun getRound(id: String): RoundEntity?

  @Query("SELECT * FROM rounds WHERE projectId = :projectId ORDER BY roundNumber ASC")
  suspend fun getRoundsForProject(projectId: String): List<RoundEntity>

  @Query("SELECT * FROM rounds ORDER BY roundNumber ASC")
  suspend fun getAllRounds(): List<RoundEntity>

  @Query("DELETE FROM rounds WHERE id IN (:roundIds)")
  suspend fun deleteRoundsByIds(roundIds: List<String>)
}

@Dao
interface SettingsDao {
  @Query("SELECT value FROM settings WHERE `key` = :key")
  suspend fun getValue(key: String): String?

  @Upsert
  suspend fun upsertValue(entry: SettingsEntity)
}