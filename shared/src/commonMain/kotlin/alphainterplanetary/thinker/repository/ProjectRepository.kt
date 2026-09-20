package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import me.tatarka.inject.annotations.Inject
import kotlin.time.Instant

@AppScope
class ProjectRepository @Inject constructor(
  private val storage: Storage,
  private val generator: QuestionGenerator,
  private val taskRunner: TaskRunner,
) {

  /**
   * Persists the project shell immediately and returns it; the initial batch is
   * generated on the [taskRunner] (an [TaskKind.InitialQuestions] task) so the
   * caller and the UI are never blocked on inference — the detail screen
   * reloads when that task completes (see ProjectDetailViewModel).
   */
  suspend fun createProject(synopsis: String, title: String? = null): Project {
    val now = now()
    val projectId = randomUUID()

    val trimmedTitle = title.orEmpty().trim()

    val resolvedTitle = trimmedTitle.takeIf { it.isNotEmpty() }
      ?.substring(0, trimmedTitle.length.coerceAtMost(30))
      ?: generator.recommendTitle(synopsis)

    val roundId = randomUUID()
    val round = Round(
      id = roundId,
      projectId = projectId,
      phase = Phase.first,
      roundNumber = 1,
      origin = RoundOrigin.Initial,
      startedAt = now,
    )

    val project = Project(
      id = projectId,
      synopsis = synopsis.trim(),
      editableTitle = resolvedTitle,
      status = "Draft",
      questions = emptyList(),
      rounds = listOf(round),
      createdAt = now,
      updatedAt = now,
    )
    // Save the initial version of the project, in case generation fails.
    storage.saveProject(project)
    enqueueInitialGeneration(project.id)
    return project
  }

  /** Generates and persists the opening question batch for the project's current round. */
  private fun enqueueInitialGeneration(projectId: String) {
    taskRunner.enqueue(projectId = projectId, kind = TaskKind.InitialQuestions) {
      val reloaded = storage.getProject(projectId) ?: return@enqueue
      val round = reloaded.currentRound ?: return@enqueue
      val questions = generator.generateInitialQuestions(
        editableTitle = reloaded.editableTitle,
        synopsis = reloaded.synopsis,
        roundId = round.id,
        phase = round.phase,
      ).shuffled()
      val updated = reloaded.copy(questions = questions, updatedAt = now())
      storage.saveProject(updated)
    }
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
      ProjectUpdateMode.CLEAR -> project.questions.map { it.resetState() }

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
   *
   * Saving an answer never advances the project phase — rounds move on only through
   * [advanceToPhase].
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
        question.withAnswer(newAnswer)
      }
    } else {
      if (trimmed.isNotBlank()) {
        question.withDraft(trimmed, now)
      } else {
        question.withoutAnswer()
      }
    }

    if (updatedQuestion == question) return project

    val updatedQuestions = project.questions.map { q ->
      if (q.id == questionId) updatedQuestion else q
    }

    val updatedProject = project.copy(
      questions = updatedQuestions,
      updatedAt = now
    )

    storage.saveProject(updatedProject)
    return updatedProject
  }

  suspend fun generateMoreQuestions(projectId: String): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = now()
    val round = nextRound(project, RoundOrigin.UserRequested, now)
    val newQs = generator.generateFollowUpQuestions(
      synopsis = project.synopsis,
      previousQuestions = project.questions,
      roundId = round.id,
      phase = round.phase,
    )
    if (newQs.isEmpty()) return project
    val updatedProject = project.copy(
      questions = project.questions + newQs,
      rounds = project.rounds + round,
      updatedAt = now,
    )
    storage.saveProject(updatedProject)
    return updatedProject
  }

  /** Whether the current phase's pool still has questions the generator could produce. */
  suspend fun canGenerateMoreQuestions(projectId: String): Boolean {
    val project = storage.getProject(projectId) ?: return false
    return generator.remainingInPhase(
      synopsis = project.synopsis,
      previousQuestions = project.questions,
      phase = project.currentPhase,
    ) > 0
  }

  /**
   * Wraps up the round(s) currently in progress and advances the project to
   * [nextPhase], opening its first `Initial` round with a fresh batch of
   * generated questions (deduped against everything already asked in the
   * project).
   */
  suspend fun advanceToPhase(projectId: String, nextPhase: Phase): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = now()

    val completedRounds = project.rounds.map { round ->
      if (round.isCompleted) round else round.complete(now)
    }

    val round = Round(
      id = randomUUID(),
      projectId = project.id,
      phase = nextPhase,
      roundNumber = nextRoundNumber(project),
      origin = RoundOrigin.Initial,
      startedAt = now,
    )

    val newQs = generator.generateInitialQuestions(
      editableTitle = project.editableTitle,
      synopsis = project.synopsis,
      roundId = round.id,
      phase = round.phase,
    )
      .filterNot { question -> project.questions.any { it.text == question.text } }
      .shuffled()

    val updatedProject = project.copy(
      questions = project.questions + newQs,
      rounds = completedRounds + round,
      updatedAt = now,
    )
    storage.saveProject(updatedProject)
    return updatedProject
  }

  /** The next sequential round number in the project. */
  private fun nextRoundNumber(project: Project): Int =
    (project.rounds.maxOfOrNull { it.roundNumber } ?: 0) + 1

  /** The next sequential round in the project's current phase, or [Phase.first] if none is in progress. */
  private fun nextRound(project: Project, origin: RoundOrigin, startedAt: Instant): Round = Round(
    id = randomUUID(),
    projectId = project.id,
    phase = project.currentRound?.phase ?: Phase.first,
    roundNumber = nextRoundNumber(project),
    origin = origin,
    startedAt = startedAt,
  )

  suspend fun ignoreQuestion(
    projectId: String,
    questionId: String,
  ): Project? {
    val project = storage.getProject(projectId) ?: return null
    val now = now()
    val updatedQuestions = project.questions.map { q ->
      if (q.id == questionId) q.withIgnored(now) else q
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
      if (q.id == questionId) q.withoutIgnored() else q
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

  fun exportProject(project: Project): String = buildString {
    appendLine(
      """
        # ${project.synopsis}

        ## Overview
        ${project.synopsis}
        
        """.trimIndent()
    )

    for (question in project.questions.sortedBy { it.timestamp }) {
      val answer = question.currentAnswer
      val answerBlock = if (answer != null) {
        """
            | **Answer:** | ${answer.text} |
            |-------------|--------
            | **Answered:** | ${answer.createdAt} |
            """.trimIndent()
      } else {
        """
            |**Status:** | unanswered |
            |------------|----------
            """.trimIndent()
      }

      appendLine("### Q: ${question.text}\n")
      appendLine(answerBlock)
      appendLine()
    }
  }
}
