package com.pforhan.alphathinker.repository

import com.pforhan.alphathinker.llm.LLMIntegration
import com.pforhan.alphathinker.model.Answer
import com.pforhan.alphathinker.model.Question
import com.pforhan.alphathinker.model.Project
import com.pforhan.alphathinker.util.randomUUID
import kotlinx.datetime.Clock

class ProjectRepository(
    private val storage: Storage,
    private val llm: LLMIntegration
) {
    interface Storage {
        suspend fun saveProject(project: Project): Project
        suspend fun getProject(id: String): Project?
        suspend fun getAllProjects(): List<Project>
        suspend fun deleteProject(id: String)
        suspend fun deleteAllProjects()
    }

    suspend fun createProject(synopsis: String): Project {
        val now = Clock.System.now()
        val projectId = randomUUID()
        val project = Project(
            id = projectId,
            synopsis = synopsis.trim(),
            editableTitle = synopsis.take(30).trim() + "...",
            status = "Draft",
            questions = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        val saved = storage.saveProject(project)

        val contextId = randomUUID()
        val questions = llm.generateInitialQuestions(saved.synopsis)
            .map { it.copy(id = randomUUID(), contextId = contextId) }

        val updated = saved.copy(
            questions = questions,
            updatedAt = Clock.System.now()
        )
        return storage.saveProject(updated)
    }

  suspend fun deleteProject(id: String) {
    return storage.deleteProject(id)
  }

    suspend fun getProject(id: String): Project? {
        return storage.getProject(id)
    }

    suspend fun getAllProjects(): List<Project> {
        return storage.getAllProjects()
    }

    suspend fun getUnansweredQuestions(project: Project): List<Question> {
        return project.questions.filterNot { question ->
                (question.currentAnswer?.isAnswered ?: false) || question.isIgnored
        }
    }

    suspend fun updateAnswer(
        projectId: String,
        questionId: String,
        text: String,
        autoIgnore: Boolean = false
    ): Project? {
        val project = storage.getProject(projectId) ?: return null
        val now = Clock.System.now()
        
        val newAnswer = Answer(id = 0, questionId = questionId, text = text, answeredAt = now)
        
        val updatedQuestions = project.questions.map { q ->
            if (q.id == questionId) {
                q.copy(
                    answers = q.answers + newAnswer,
                    ignoredAt = if (autoIgnore) now else q.ignoredAt
                )
            } else {
                q
            }
        }
        
        val answered = allQuestionsAnswered(project, updatedQuestions)
        
        val updatedProject = if (answered) {
            val contextId = randomUUID()
            val newQs = llm.generateFollowUpQuestions(
                project.synopsis,
                project.questions.size 
            ).map { it.copy(id = randomUUID(), contextId = contextId) }
            
            project.copy(
                questions = project.questions + newQs,
                updatedAt = Clock.System.now()
            )
        } else {
            project.copy(
                questions = updatedQuestions,
                updatedAt = Clock.System.now()
            )
        }
        
        return storage.saveProject(updatedProject)
    }

    private fun allQuestionsAnswered(project: Project, questions: List<Question>): Boolean {
        val activeQuestions = questions.filterNot { it.isIgnored }
        return activeQuestions.all { it.currentAnswer?.isAnswered == true }
    }

    suspend fun ignoreQuestion(
        projectId: String,
        questionId: String
    ): Project? {
        val project = storage.getProject(projectId) ?: return null
        val now = Clock.System.now()
        val updatedQuestions = project.questions.map { q ->
            if (q.id == questionId) q.copy(ignoredAt = now) else q
        }

        val updatedProject = project.copy(
            questions = updatedQuestions,
            updatedAt = now
        )
        return storage.saveProject(updatedProject)
    }

    suspend fun unignoreQuestion(
        projectId: String,
        questionId: String
    ): Project? {
        val project = storage.getProject(projectId) ?: return null
        val now = Clock.System.now()
        val updatedQuestions = project.questions.map { q ->
            if (q.id == questionId) q.copy(ignoredAt = null) else q
        }

        val updatedProject = project.copy(
            questions = updatedQuestions,
            updatedAt = now
        )
        return storage.saveProject(updatedProject)
    }



    suspend fun deleteAllProjects() {
        storage.deleteAllProjects()
    }

    suspend fun deleteAnswer(
        projectId: String,
        questionId: String,
        answerId: Long
    ): Project? {
        val project = storage.getProject(projectId) ?: return null
        val now = Clock.System.now()
        val updatedQuestions = project.questions.map { q ->
            if (q.id == questionId) {
                q.copy(answers = q.answers.map { a ->
                    if (a.id == answerId) a.copy(deletedAt = now) else a
                })
            } else {
                q
            }
        }
        return storage.saveProject(project.copy(questions = updatedQuestions, updatedAt = now))
    }

    suspend fun exportProject(project: Project): String {
        val sb = StringBuilder()
        sb.appendLine("# ${project.synopsis}")
        sb.appendLine()
        sb.appendLine("## Overview")
        sb.appendLine("${project.synopsis}")
        sb.appendLine()

        project.questions.sortedBy { it.timestamp }.forEach { question ->
            sb.appendLine("### Q: ${question.text}")
            val answer = question.currentAnswer
            if (answer != null && answer.isAnswered) {
                sb.appendLine()
                sb.appendLine("| **Answer:** | ${answer.text} |")
                sb.appendLine("|-------------|--------")
                sb.appendLine("| **Answered:** | ${answer.answeredAt} |")
                if (answer.modifiedAt != null) {
                    sb.appendLine("| **Modified:** | ${answer.modifiedAt} |")
                }
            } else {
                sb.appendLine()
                sb.appendLine("|**Status:** | unanswered |")
                sb.appendLine("|------------|----------")
            }
            sb.appendLine()
        }

        return sb.toString()
    }
}
