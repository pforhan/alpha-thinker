package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.util.randomUUID
import kotlinx.datetime.Clock.System
import me.tatarka.inject.annotations.Inject

class ProjectRepository @Inject constructor(
  private val storage: Storage,
  private val generator: QuestionGenerator,
) {

  suspend fun createProject(synopsis: String, title: String? = null): Project {
    val now = System.now()
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
    val saved = storage.saveProject(project)

    val contextId = randomUUID()
    val questions = generator.generateInitialQuestions(
      editableTitle = saved.editableTitle,
      synopsis = saved.synopsis,
      contextId = contextId
    )
      .shuffled()

    val updated = saved.copy(
      questions = questions,
      updatedAt = System.now()
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

  suspend fun saveQuestionOrder(projectId: String, order: List<String>) {
    storage.saveQuestionOrder(projectId, order)
  }

  suspend fun updateProject(
    id: String,
    title: String,
    synopsis: String,
    mode: ProjectUpdateMode,
  ): Project? {
    val project = storage.getProject(id) ?: return null
    val now = System.now()

    val updatedQuestions = when (mode) {
      ProjectUpdateMode.CLEAR -> project.questions.map { q ->
        q.copy(
          answers = q.answers.map { a -> a.copy(deletedAt = now) },
          ignoredAt = null
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
    return storage.saveProject(updatedProject)
  }

  suspend fun getUnansweredQuestions(project: Project): List<Question> {
    return project.unansweredQuestions
  }

  suspend fun updateAnswer(
    projectId: String,
    questionId: String,
    text: String,
    isDraft: Boolean = false,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val question = project.questions.find { it.id == questionId } ?: return null

    if (isDraft && question.currentAnswer?.isComplete == true) {
      throw IllegalStateException("Cannot add a draft answer to a question that is already answered")
    }

    val now = System.now()

    val newAnswer =
      Answer(id = 0, questionId = questionId, text = text, answeredAt = if (isDraft) null else now)

    val updatedQuestions = project.questions.map { q ->
      if (q.id == questionId) {
        q.copy(
          answers = q.answers + newAnswer,
        )
      } else {
        q
      }
    }

    val updatedProject = project.copy(
      questions = updatedQuestions,
      updatedAt = System.now()
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

    return storage.saveProject(finalProject)
  }

  suspend fun ignoreQuestion(
    projectId: String,
    questionId: String,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = System.now()
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
    questionId: String,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = System.now()
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
    answerId: Long,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = System.now()
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
      if (answer != null && answer.isComplete) {
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
