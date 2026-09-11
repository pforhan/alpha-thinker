package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import me.tatarka.inject.annotations.Inject

class ProjectRepository @Inject constructor(
  private val storage: Storage,
  private val generator: QuestionGenerator,
) {

  suspend fun createProject(synopsis: String, title: String? = null): Project {
    val now = now()
    val projectId = randomUUID()

    val trimmedTitle = title.orEmpty().trim()

    val resolvedTitle = trimmedTitle.takeIf { it.isNotEmpty() }
      ?.substring(0, trimmedTitle.length.coerceAtMost(30))
      ?: generator.recommendTitle(synopsis)

    val project = Project(
      id = projectId,
      synopsis = synopsis.trim(),
      editableTitle = resolvedTitle,
      status = "Draft",
      questions = emptyList(),
      createdAt = now,
      updatedAt = now
    )
    // Save the inital version of the project, in case generation fails.
    storage.saveProject(project)

    val contextId = randomUUID()
    val questions = generator.generateInitialQuestions(
      editableTitle = project.editableTitle,
      synopsis = project.synopsis,
      contextId = contextId
    ).shuffled()

    val updated = project.copy(
      questions = questions,
      updatedAt = now()
    )
    // Save again but with the generated questions.
    storage.saveProject(updated)
    return updated
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

  suspend fun saveQuestionOrder(projectId: String, order: List<String>) {
    storage.saveQuestionOrder(projectId, order)
  }

  suspend fun restoreProject(project: Project): Project {
    storage.saveProject(project)
    return project
  }

  suspend fun updateProject(
    id: String,
    title: String,
    synopsis: String,
    mode: ProjectUpdateMode,
  ): Project? {
    val project = storage.getProject(id) ?: return null
    val now = now()

    val updatedQuestions = when (mode) {
      ProjectUpdateMode.CLEAR -> project.questions.map { q ->
        q.copy(
          answerId = null,
          draftText = null,
          draftUpdatedAt = null,
          ignoredAt = null,
        )
      }

      ProjectUpdateMode.REVALIDATE -> {
        // TODO: AI revalidation logic
        project.questions
      }

      ProjectUpdateMode.KEEP -> project.questions
    }

    val updatedProject = project.copy(
      synopsis = synopsis.trim(),
      editableTitle = title.trim().substring(0, title.trim().length.coerceAtMost(30)),
      questions = updatedQuestions,
      updatedAt = now
    )
    storage.saveProject(updatedProject)
    return updatedProject
  }

  /**
   * Persists the answer state for a question. This is the only way a question's
   * committed/draft state changes.
   *
   * [completed] is the toggle: `true` commits [text] as an immutable [Answer]
   * version (a no-op when the committed text is unchanged); `false` stores
   * [text] as a draft, clearing the draft entirely when the text is blank. A
   * question is always either committed or a draft, never both, so saving a
   * draft demotes any current answer out of "answered".
   */
  suspend fun saveAnswer(
    projectId: String,
    questionId: String,
    text: String,
    completed: Boolean,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val question = project.questions.find { it.id == questionId } ?: return null
    val now = now()
    val trimmed = text.trim()

    val updatedQuestion = if (completed) {
      val current = question.currentAnswer
      if (current != null && current.text == trimmed) {
        question
      } else {
        val newAnswer = Answer(
          id = randomUUID(),
          questionId = questionId,
          text = trimmed,
          createdAt = now,
        )
        question.copy(
          answerId = newAnswer.id,
          draftText = null,
          draftUpdatedAt = null,
          answers = question.answers + newAnswer,
        )
      }
    } else {
      question.copy(
        answerId = null,
        draftText = trimmed.takeIf { it.isNotBlank() },
        draftUpdatedAt = if (trimmed.isNotBlank()) now else null,
      )
    }

    if (updatedQuestion == question) return project

    val updatedQuestions = project.questions.map { q ->
      if (q.id == questionId) updatedQuestion else q
    }

    val updatedProject = project.copy(
      questions = updatedQuestions,
      updatedAt = now
    )

    val answered = updatedProject.allActiveQuestionsAnswered

    val finalProject = if (answered) {
      val contextId = randomUUID()
      val newQs = generator.generateFollowUpQuestions(
        synopsis = project.synopsis,
        previousQuestions = project.questions,
        contextId = contextId
      )

      updatedProject.copy(
        questions = updatedProject.questions + newQs,
      )
    } else {
      updatedProject
    }

    storage.saveProject(finalProject)
    return finalProject
  }

  suspend fun generateMoreQuestions(projectId: String): Project? {
    val project = storage.getProject(projectId) ?: return null
    val contextId = randomUUID()
    val newQs = generator.generateFollowUpQuestions(
      synopsis = project.synopsis,
      previousQuestions = project.questions,
      contextId = contextId
    )
    if (newQs.isEmpty()) return project
    val updatedProject = project.copy(
      questions = project.questions + newQs,
      updatedAt = now()
    )
    storage.saveProject(updatedProject)
    return updatedProject
  }

  suspend fun ignoreQuestion(
    projectId: String,
    questionId: String,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = now()
    val updatedQuestions = project.questions.map { q ->
      if (q.id == questionId) q.copy(ignoredAt = now) else q
    }

    val updatedProject = project.copy(
      questions = updatedQuestions,
      updatedAt = now
    )
    storage.saveProject(updatedProject)
    return updatedProject
  }

  suspend fun unignoreQuestion(
    projectId: String,
    questionId: String,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = now()
    val updatedQuestions = project.questions.map { q ->
      if (q.id == questionId) q.copy(ignoredAt = null) else q
    }

    val updatedProject = project.copy(
      questions = updatedQuestions,
      updatedAt = now
    )
    storage.saveProject(updatedProject)
    return updatedProject
  }

  suspend fun deleteAllProjects() {
    storage.deleteAllProjects()
  }

  suspend fun exportProject(project: Project): String {
    val sb = StringBuilder()
    sb.appendLine("# ${project.synopsis}")
    sb.appendLine()
    sb.appendLine("## Overview")
    sb.appendLine(project.synopsis)
    sb.appendLine()

    project.questions.sortedBy { it.timestamp }.forEach { question ->
      sb.appendLine("### Q: ${question.text}")
      val answer = question.currentAnswer
      if (answer != null) {
        sb.appendLine()
        sb.appendLine("| **Answer:** | ${answer.text} |")
        sb.appendLine("|-------------|--------")
        sb.appendLine("| **Answered:** | ${answer.createdAt} |")
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
