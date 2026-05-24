package com.pforhan.alphathinker.repository

import androidx.room.withTransaction
import com.pforhan.alphathinker.dao.AnswerEntity as RoomAnswer
import com.pforhan.alphathinker.dao.QuestionEntity as RoomQuestion
import com.pforhan.alphathinker.dao.ProjectEntity as RoomProject
import com.pforhan.alphathinker.model.ExchangeRound
import com.pforhan.alphathinker.model.Project
import com.pforhan.alphathinker.model.Question
import java.time.Instant
import java.util.UUID

class AndroidProjectStorage(
    private val database: AppDatabase
) : ProjectRepository.Storage {

    override suspend fun saveProject(project: Project) {
        database.withTransaction {
            val projectEntity = RoomProject(
                id = project.id,
                synopsis = project.synopsis,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt
            )
            dao().insertProject(projectEntity)

            val questions = project.questions.map { q ->
                RoomQuestion(
                    id = q.id,
                    projectId = project.id,
                    round = project.exchangeRounds.find { it.questions.any { it.id == q.id } }?.round
                        ?: 0,
                    contextId = if (project.exchangeRounds.isNotEmpty()) project.exchangeRounds.first().contextId
                        else q.contextId,
                    text = q.text,
                    timestamp = q.timestamp,
                    archivedAt = q.archivedAt
                )
            }
            dao().insertQuestions(questions)

            val answers = project.exchangeRounds.flatMap { round ->
                round.answers.map { answer ->
                    RoomAnswer(
                        questionId = answer.questionId,
                        contextId = round.contextId,
                        text = answer.text,
                        answeredAt = answer.answeredAt,
                        modifiedAt = answer.modifiedAt
                    )
                }
            }
            if (answers.isNotEmpty()) {
                dao().insertAnswers(answers)
            }
        }
    }

    override suspend fun getProject(id: String): Project? {
        val projectEntity = dao().getProject(id) ?: return null
        val questionsList = dao().getQuestionsForProject(id)
        val questionsWithDetails = dao().getQuestionsForProjectWithDetails(id)

        val answersByQuestion = questionsWithDetails
            .flatMap { it.answers }
            .groupBy { it.questionId }

        val exchangeRounds = questionsList
            .groupBy { it.contextId to it.round }
            .mapNotNull { (key, qaList) ->
                val (contextId, round) = key
                qaList.firstOrNull()?.let {
                    ExchangeRound(
                        round = round,
                        questions = qaList.map { qa ->
                            Question(
                                id = qa.id,
                                text = qa.text,
                                timestamp = qa.timestamp,
                                contextId = contextId,
                                archivedAt = qa.archivedAt
                            )
                        },
                        contextId = contextId,
                        createdAt = qaList.first().timestamp,
                        answers = qaList.flatMap { qa ->
                            answersByQuestion[qa.id]?.map { a ->
                                com.pforhan.alphathinker.model.Answer(
                                    questionId = a.questionId,
                                    text = a.text,
                                    answeredAt = a.answeredAt,
                                    modifiedAt = a.modifiedAt
                                )
                            } ?: emptyList()
                        },
                        questionsCount = qaList.size
                    )
                }
            }

        val questions = questionsList.map { q ->
            Question(
                id = q.id,
                text = q.text,
                timestamp = q.timestamp,
                contextId = q.contextId,
                archivedAt = q.archivedAt
            )
        }

        return Project(
            id = projectEntity.id,
            synopsis = projectEntity.synopsis,
            questions = questions,
            exchangeRounds = exchangeRounds,
            createdAt = projectEntity.createdAt,
            updatedAt = projectEntity.updatedAt
        )
    }

    override suspend fun getAllProjects(): List<Project> {
        return dao().getAllProjects().map { entity ->
            getProject(entity.id)
        }.filterNotNull()
    }

    override suspend fun deleteProject(id: String) {
        val project = dao().getProject(id)
        project?.let { dao().deleteProject(it) }
    }

    override suspend fun deleteAllProjects() {
        dao().deleteAllProjects()
    }

    private fun dao() = database.projectDao()
}
