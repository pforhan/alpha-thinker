package com.pforhan.alphathinker

import com.pforhan.alphathinker.model.Project
import com.pforhan.alphathinker.model.Question
import com.pforhan.alphathinker.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ThinkerApiImpl(
    private val repository: ProjectRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : ThinkerApi {

    override fun createProject(synopsis: String, callback: (Result<ProjectDto>) -> Unit) {
        scope.launch {
            try {
                val project = repository.createProject(synopsis)
                callback(Result.success(project.toDto()))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun getAllProjects(callback: (Result<List<ProjectDto>>) -> Unit) {
        scope.launch {
            try {
                val projects = repository.getAllProjects()
                callback(Result.success(projects.map { it.toDto() }))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun getProject(id: String, callback: (Result<ProjectDto>) -> Unit) {
        scope.launch {
            try {
                val project = repository.getProject(id)
                if (project != null) {
                    callback(Result.success(project.toDto()))
                } else {
                    callback(Result.failure(Exception("Project not found")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun getUnansweredQuestions(projectId: String, callback: (Result<List<QuestionDto>>) -> Unit) {
        scope.launch {
            try {
                val project = repository.getProject(projectId)
                if (project != null) {
                    val questions = repository.getUnansweredQuestions(project)
                    callback(Result.success(questions.map { it.toDto() }))
                } else {
                    callback(Result.failure(Exception("Project not found")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun deleteProject(id: String, callback: (Result<Unit>) -> Unit) {
        scope.launch {
            try {
                repository.deleteProject(id)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun updateAnswer(
        projectId: String,
        questionId: String,
        text: String,
        autoIgnore: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        scope.launch {
            try {
                repository.updateAnswer(projectId, questionId, text, autoIgnore)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun ignoreQuestion(projectId: String, questionId: String, callback: (Result<Unit>) -> Unit) {
        scope.launch {
            try {
                repository.ignoreQuestion(projectId, questionId)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun unignoreQuestion(projectId: String, questionId: String, callback: (Result<Unit>) -> Unit) {
        scope.launch {
            try {
                repository.unignoreQuestion(projectId, questionId)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}

fun Project.toDto(): ProjectDto = ProjectDto(
    id = id,
    synopsis = synopsis,
    editableTitle = editableTitle,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    status = status,
    questions = questions.map { it.toDto() }
)

fun Question.toDto(): QuestionDto = QuestionDto(
    id = id,
    text = text,
    timestamp = timestamp.toEpochMilliseconds(),
    contextId = contextId,
    ignoredAt = ignoredAt?.toEpochMilliseconds(),
    answers = answers.map { 
        AnswerDto(it.questionId, it.text, it.answeredAt.toEpochMilliseconds(), it.modifiedAt?.toEpochMilliseconds()) 
    }
)
