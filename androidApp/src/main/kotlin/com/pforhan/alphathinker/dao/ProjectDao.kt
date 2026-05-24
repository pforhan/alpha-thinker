package com.pforhan.alphathinker.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswer(answer: AnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswers(answers: List<AnswerEntity>)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): ProjectEntity?

    @Query("SELECT * FROM projects")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectWithDetails(id: String): ProjectWithDetails?

    @Query("SELECT * FROM questions WHERE project_id = :projectId")
    suspend fun getQuestionsForProject(projectId: String): List<QuestionEntity>

    @Query("SELECT * FROM answers WHERE question_id IN (:questionIds)")
    suspend fun getAnswersForQuestions(questionIds: List<String>): List<AnswerEntity>

    @Transaction
    @Query("SELECT * FROM questions WHERE project_id = :projectId")
    suspend fun getQuestionsForProjectWithDetails(projectId: String): List<QuestionWithAnswers>

    @Transaction
    @Query("SELECT * FROM questions WHERE project_id = :projectId ORDER BY round ASC")
    suspend fun getQuestionsByRound(projectId: String): List<QuestionWithAnswers>

    data class QuestionWithAnswers(
        @Embedded val question: QuestionEntity,
        @Relation(
            parentColumn = "id",
            entity = AnswerEntity::class,
            entityColumn = "question_id"
        )
        val answers: List<AnswerEntity> = emptyList()
    )
}
