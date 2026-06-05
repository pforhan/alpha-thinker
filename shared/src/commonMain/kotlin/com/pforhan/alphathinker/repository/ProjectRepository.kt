package com.pforhan.alphathinker.repository

import com.pforhan.alphathinker.llm.LLMIntegration
import com.pforhan.alphathinker.model.Answer
import com.pforhan.alphathinker.model.ExchangeRound
import com.pforhan.alphathinker.model.Question
import com.pforhan.alphathinker.model.Project
import com.pforhan.alphathinker.util.randomUUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
            exchangeRounds = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        val saved = storage.saveProject(project)

        val contextId = randomUUID()
        val questions = llm.generateInitialQuestions(saved.synopsis)
            .map { it.copy(id = randomUUID(), contextId = contextId) }
        val round = ExchangeRound(
            round = 1,
            questions = questions,
            contextId = contextId,
            createdAt = Clock.System.now(),
            questionsCount = questions.size
        )

        val updated = saved.copy(
            questions = questions,
            exchangeRounds = listOf(round),
            updatedAt = Clock.System.now()
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
            ?: return emptyList()

        val answeredQuestionIds = lastActiveRound.answers
            .filter { it.isAnswered }
            .map { it.questionId }
            .toSet()

        return lastActiveRound.questions.filterNot { question ->
            question.id in answeredQuestionIds || question.isArchived
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
        val now = Clock.System.now()
        val updatedRounds = project.exchangeRounds.map { round ->
            if (round.questions.any { it.id == questionId }) {
                val existingAnswer = round.answers.find { it.questionId == questionId }
                val newAnswer = if (existingAnswer == null) {
                    Answer(questionId, text, now)
                } else {
                    existingAnswer.copy(text = text, modifiedAt = now)
                }
                
                round.copy(
                    answers = round.answers.filterNot { it.questionId == questionId } + newAnswer,
                    questions = round.questions.map { q ->
                        if (q.id == questionId) {
                            q.copy(
                                archivedAt = if (autoArchive) now else q.archivedAt
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
                val contextId = randomUUID()
                val newQs = llm.generateFollowUpQuestions(
                    project.synopsis,
                    updatedRounds.maxOf { it.round }
                ).map { it.copy(id = randomUUID(), contextId = contextId) }
                val newRound = ExchangeRound(
                    round = updatedRounds.maxOf { it.round } + 1,
                    questions = newQs,
                    contextId = contextId,
                    createdAt = Clock.System.now(),
                    questionsCount = newQs.size
                )

                val previousRounds = updatedRounds.toMutableList()
                previousRounds.add(newRound)

                project.copy(
                    questions = project.questions + newQs,
                    exchangeRounds = previousRounds,
                    updatedAt = Clock.System.now()
                )
            }
            else -> {
                project.copy(
                    exchangeRounds = updatedRounds,
                    updatedAt = Clock.System.now()
                )
            }
        }

        return storage.saveProject(updatedProject)
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
                sb.appendLine("### Q: ${question.text}")
                val answer = answersMap[question.id]
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
        }

        return sb.toString()
    }
}
