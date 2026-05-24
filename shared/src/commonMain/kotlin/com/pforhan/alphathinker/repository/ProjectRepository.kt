package com.pforhan.alphathinker.repository

import com.pforhan.alphathinker.llm.LLMIntegration
import com.pforhan.alphathinker.model.Answer
import com.pforhan.alphathinker.model.ExchangeRound
import com.pforhan.alphathinker.model.Question
import com.pforhan.alphathinker.model.Project
import java.time.Instant
import java.util.UUID

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
        val now = Instant.now()
        val projectId = UUID.randomUUID().toString()
        val project = Project(
            id = projectId,
            synopsis = synopsis.trim(),
            questions = emptyList(),
            exchangeRounds = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        val saved = storage.saveProject(project)

        val contextId = UUID.randomUUID().toString()
        val questions = llm.generateInitialQuestions(saved.synopsis)
            .map { it.copy(id = UUID.randomUUID().toString(), contextId = contextId) }
        val round = ExchangeRound(
            round = 1,
            questions = questions,
            contextId = contextId,
            createdAt = Instant.now(),
            questionsCount = questions.size
        )

        val updated = saved.copy(
            questions = questions,
            exchangeRounds = listOf(round),
            updatedAt = Instant.now()
        )
        return storage.saveProject(updated)
    }

    suspend fun getProject(id: String): Project? {
        return storage.getProject(id)
    }

    suspend fun getAllProjects(): List<Project> {
        return storage.getAllProjects()
    }

    suspend fun getUnansweredQuestions(project: Project): List<Question> {
        val lastActiveRound = project.exchangeRounds.filter { it.isActive }.lastOrNull()
        if (lastActiveRound == null) {
            return project.questions
        }
        val answeredQuestionIds = lastActiveRound.questions
            .filter { question ->
                project.questions.find { it.id == question.id }?.isBlank() == false
            }
            .map { it.id }
            .toSet()
        return lastActiveRound.questions.filterNot { question ->
            question.text.isBlank() || question.id in answeredQuestionIds || question.isArchived
        }
    }

    private fun List<ExchangeRound>.lastActive() = this.filter { it.isActive }.lastOrNull()

    suspend fun updateAnswer(
        projectId: String,
        questionId: String,
        text: String,
        autoArchive: Boolean = false
    ): Project? {
        val project = storage.getProject(projectId) ?: return null
        val updatedRounds = project.exchangeRounds.map { round ->
            if (round.questions.any { it.id == questionId }) {
                round.copy(
                    questions = round.questions.map { q ->
                        if (q.id == questionId) {
                            q.copy(
                                text = text,
                                archivedAt = if (autoArchive) Instant.now() else q.archivedAt
                            )
                        } else {
                            q
                        }
                    }
                )
            } else {
                round
            }
        }

        val answered = allQuestionsAnswered(project, updatedRounds)

        val updatedProject = when {
            answered && updatedRounds.filter { it.isActive }.isNotEmpty() -> {
                val contextId = UUID.randomUUID().toString()
                val newQs = llm.generateFollowUpQuestions(
                    project.synopsis,
                    updatedRounds.maxOf { it.round }
                ).map { it.copy(id = UUID.randomUUID().toString(), contextId = contextId) }
                val newRound = ExchangeRound(
                    round = updatedRounds.maxOf { it.round } + 1,
                    questions = newQs,
                    contextId = contextId,
                    createdAt = Instant.now(),
                    questionsCount = newQs.size
                )

                val previousRounds = updatedRounds.toMutableList()
                previousRounds.add(newRound)

                project.copy(
                    questions = project.questions + newQs,
                    exchangeRounds = previousRounds,
                    updatedAt = Instant.now()
                )
            }
            else -> {
                project.copy(
                    exchangeRounds = updatedRounds,
                    updatedAt = Instant.now()
                )
            }
        }

        if (project.id != updatedProject.id) {
            return storage.saveProject(updatedProject)
        }
        return updatedProject
    }

    private fun allQuestionsAnswered(project: Project, rounds: List<ExchangeRound>): Boolean {
        val allQuestions = rounds.filter { it.isActive }.flatMap { it.questions }.filterNot { it.isArchived }
        return allQuestions.isEmpty()
    }

    suspend fun deleteProject(id: String) {
        storage.deleteProject(id)
    }

    suspend fun deleteAllProjects() {
        storage.deleteAllProjects()
    }

    suspend fun exportProject(project: Project): String {
        val sb = StringBuilder()
        sb.appendLine("# ${project.synopsis}")
        sb.appendLine()
        sb.appendLine("## Overview")
        sb.appendLine("${project.synopsis}")
        sb.appendLine()

        val sortedRounds = project.exchangeRounds.sortedBy { it.round }
        sortedRounds.forEach { round ->
            val status = if (round.isActive) " (Active)" else " (Archived)"
            sb.appendLine("## Round ${round.round}$status")
            sb.appendLine("> Generated: ${round.createdAt}")
            sb.appendLine()

            val answersMap = round.answers.associate { it.questionId to it }

            round.questions.forEach { question ->
                sb.appendLine("### Q: $question.text")
                val answer = answersMap[question.id]
                if (answer != null && answer.isAnswered) {
                    sb.appendLine()
                    sb.appendLine("| **Answer:** | ${answer.text} |")
                    sb.appendLine("|-------------|--------")
                    sb.appendLine("| **Answered:** | $answer.answeredAt |")
                    if (answer.modifiedAt != null) {
                        sb.appendLine("| **Modified:** | $answer.modifiedAt |")
                    }
                } else {
                    sb.appendLine()
                    sb.appendLine("|**Status:** | unanswered |")
                    sb.appendLine("|------------|----------")
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
