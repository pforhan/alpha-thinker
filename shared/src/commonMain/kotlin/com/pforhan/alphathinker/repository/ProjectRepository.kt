package com.pforhan.alphathinker.repository

import com.pforhan.alphathinker.llm.LLMIntegration
import com.pforhan.alphathinker.model.Answer
import com.pforhan.alphathinker.model.ExchangeRound
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
        val project = Project(
            id = UUID.randomUUID().toString(),
            synopsis = synopsis.trim(),
            questions = emptyList(),
            exchangeRounds = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        val saved = storage.saveProject(project)

        val questions = llm.generateInitialQuestions(saved.synopsis)
        val round = ExchangeRound(
            round = 1,
            questions = questions,
            createdAt = Instant.now()
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
        val existingAnswers = project.exchangeRounds
            .flatMap { it.questions }
            .associate { it.id }

        val rounds = project.exchangeRounds

        return project.questions
            .filter { it !in existingAnswers || existingAnswers[it.id].isBlank() }
            .ifEmpty {
                rounds.lastOrNull()?.questions ?: emptyList()
            }
    }

    suspend fun updateAnswer(projectId: String, questionId: String, text: String): Project? {
        val project = storage.getProject(projectId) ?: return null
        val updatedRounds = project.exchangeRounds.map { round ->
            if (round.questions.any { it.id == questionId }) {
                val updatedQuestions = round.questions.map { q ->
                    if (q.id == questionId) {
                        q
                    } else {
                        q
                    }
                }
                val updatedAnswers = round.answers.toMutableList()
                val answer = updatedAnswers.find { it.questionId == questionId }
                if (answer != null) {
                    updatedAnswers.add(answer.copy(text = text, modifiedAt = Instant.now()))
                } else {
                    updatedAnswers.add(Answer(questionId, text, Instant.now()))
                }
                round.copy(questions = updatedQuestions, answers = updatedAnswers)
            } else {
                round
            }
        }

        val answered = allQuestionsAnswered(project, updatedRounds)
        val updatedAnswers = updatedRounds.lastOrNull()?.answers ?: emptyList()
        val answersMap = updatedAnswers.associate { it.questionId to it.text }

        val updatedProject = when {
            answered && project.exchangeRounds.isNotEmpty() -> {
                val newQs = llm.generateFollowUpQuestions(
                    project.synopsis,
                    updatedRounds.last().round
                )
                val newRound = ExchangeRound(
                    round = updatedRounds.last().round + 1,
                    questions = newQs,
                    createdAt = Instant.now()
                )
                project.copy(
                    questions = project.questions + newQs,
                    exchangeRounds = updatedRounds + newRound,
                    updatedAt = Instant.now()
                )
            }
            else -> {
                val updatedRoundsMap = updatedRounds.map { r ->
                    r.copy(
                        questions = r.questions.map { q ->
                            q.copy(
                                id = q.id,
                                text = q.text
                            )
                        }
                    )
                }
                project.copy(
                    exchangeRounds = updatedRoundsMap,
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
        val allQuestions = rounds.flatMap { it.questions }
        val answeredIds = rounds.flatMap { it.answers }.map { it.questionId }.toSet()
        return allQuestions.all { it.id in answeredIds }
    }

    suspend fun deleteProject(id: String) {
        storage.deleteProject(id)
    }

    suspend fun deleteAllProjects() {
        storage.deleteAllProjects()
    }

    suspend fun exportProject(project: Project): String {
        val sb = StringBuilder()
        sb.appendLine("# $synopsis")
        sb.appendLine()
        sb.appendLine("## Overview")
        sb.appendLine("$synopsis")
        sb.appendLine()

        val sortedRounds = project.exchangeRounds.sortedBy { it.round }
        sortedRounds.forEach { round ->
            sb.appendLine("## Round $round.round")
            sb.appendLine("> Generated: $round.createdAt")
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
