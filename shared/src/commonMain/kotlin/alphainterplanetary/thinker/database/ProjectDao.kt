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