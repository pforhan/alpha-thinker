package com.pforhan.alphathinker.database

import com.pforhan.alphathinker.model.Project
import com.pforhan.alphathinker.model.Question
import com.pforhan.alphathinker.model.Answer
import com.pforhan.alphathinker.repository.ProjectRepository
import kotlinx.datetime.Instant

class RoomStorage(private val database: AppDatabase) : ProjectRepository.Storage {
    override suspend fun saveProject(project: Project): Project {
        val entity = ProjectEntity(
            id = project.id,
            synopsis = project.synopsis,
            editableTitle = project.editableTitle,
            createdAt = project.createdAt.toEpochMilliseconds(),
            updatedAt = project.updatedAt.toEpochMilliseconds(),
            status = project.status
        )
        database.projectDao().upsertProject(entity)

        // Save questions
        project.questions.forEach { q ->
            database.questionDao().upsertQuestion(
                QuestionEntity(
                    id = q.id,
                    projectId = project.id,
                    text = q.text,
                    createdAt = q.timestamp.toEpochMilliseconds(),
                     ignoredAt = q.ignoredAt?.toEpochMilliseconds()
                )
            )
        }

        // Save answers
        project.questions.flatMap { it.answers }.forEach { a ->
            database.answerDao().upsertAnswer(
                AnswerEntity(
                    questionId = a.questionId,
                    text = a.text,
                    answeredAt = a.answeredAt.toEpochMilliseconds(),
                    modifiedAt = a.modifiedAt?.toEpochMilliseconds()
                )
            )
        }

        return project
    }

    override suspend fun getProject(id: String): Project? {
        val entity = database.projectDao().getProjectById(id) ?: return null
        val questions = database.questionDao().getQuestionsForProject(id).map { qe ->
            val qAnswers = database.answerDao().getAnswersForQuestion(qe.id).map { ae ->
                Answer(
                    questionId = ae.questionId,
                    text = ae.text,
                    answeredAt = Instant.fromEpochMilliseconds(ae.answeredAt),
                    modifiedAt = ae.modifiedAt?.let { Instant.fromEpochMilliseconds(it) }
                )
            }
            Question(
                id = qe.id,
                text = qe.text,
                timestamp = Instant.fromEpochMilliseconds(qe.createdAt),
                contextId = "", // Not stored in entity currently
                 ignoredAt = qe.ignoredAt?.let { Instant.fromEpochMilliseconds(it) },
                answers = qAnswers
            )
        }

        return Project(
            id = entity.id,
            synopsis = entity.synopsis,
            editableTitle = entity.editableTitle,
            status = entity.status,
            questions = questions,
            createdAt = Instant.fromEpochMilliseconds(entity.createdAt),
            updatedAt = Instant.fromEpochMilliseconds(entity.updatedAt)
        )
    }

    override suspend fun getAllProjects(): List<Project> {
        return database.projectDao().getAllProjects().map { entity ->
            Project(
                id = entity.id,
                synopsis = entity.synopsis,
                editableTitle = entity.editableTitle,
                status = entity.status,
                questions = emptyList(), // Not loading all questions for list view
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
