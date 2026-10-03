package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface AnswerDao {
  @Upsert
  suspend fun upsertAnswer(answer: AnswerEntity): Long

  @Query("SELECT * FROM answers WHERE questionId IN (:questionIds) ORDER BY createdAt ASC, id ASC")
  suspend fun getAnswersForQuestions(questionIds: List<String>): List<AnswerEntity>

  @Query("DELETE FROM answers WHERE id IN (:answerIds)")
  suspend fun deleteAnswersByIds(answerIds: List<String>)
}
