package com.pforhan.alphathinker.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
  @Upsert
  suspend fun upsertProject(project: ProjectEntity): Long

  @Query("SELECT * FROM projects")
  fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

  @Query("SELECT * FROM projects")
  suspend fun getAllProjects(): List<ProjectEntity>

  @Query("SELECT * FROM projects WHERE id = :id")
  suspend fun getProjectById(id: String): ProjectEntity?

  @Delete
  suspend fun deleteProject(project: ProjectEntity)

  @Query("DELETE FROM projects")
  suspend fun deleteAllProjects()
}

@Dao
interface QuestionDao {
  @Upsert
  suspend fun upsertQuestion(question: QuestionEntity): Long

  @Query("SELECT * FROM questions WHERE projectId = :projectId")
  suspend fun getQuestionsForProject(projectId: String): List<QuestionEntity>

  @Delete
  suspend fun deleteQuestion(question: QuestionEntity)
}

@Dao
interface AnswerDao {
  @Upsert
  suspend fun upsertAnswer(answer: AnswerEntity): Long

  @Query("SELECT * FROM answers WHERE questionId = :questionId ORDER BY answeredAt DESC")
  suspend fun getAnswersForQuestion(questionId: String): List<AnswerEntity>

  @Delete
  suspend fun deleteAnswer(answer: AnswerEntity)
}
