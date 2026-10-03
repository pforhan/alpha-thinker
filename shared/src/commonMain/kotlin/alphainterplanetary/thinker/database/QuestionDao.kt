package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

@Dao
interface QuestionDao {
  @Upsert
  suspend fun upsertQuestion(question: QuestionEntity): Long

  @Query("SELECT * FROM questions WHERE projectId = :projectId ORDER BY sortOrder ASC")
  suspend fun getQuestionsForProject(projectId: String): List<QuestionEntity>

  /** Private to [updateSortOrderForProject] by convention — a `private` abstract method is not legal Kotlin. */
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
