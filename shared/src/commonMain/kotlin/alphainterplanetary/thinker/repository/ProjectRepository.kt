package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.PlanningEngineSelector
import alphainterplanetary.thinker.engine.QuestionBatch
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import me.tatarka.inject.annotations.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

@AppScope
class ProjectRepository @Inject constructor(
  private val storage: Storage,
  private val engineSelector: PlanningEngineSelector,
  private val taskRunner: TaskRunner,
) {

  /**
   * Persists the project shell immediately and returns it. When no [title] is
   * supplied, the shell ships with an empty title and a [TaskKind.TitleRecommendation]
   * task fills it in from the synopsis — the title and batch both run in the
   * serial engine group ([TaskKind.group]), so the title lands before the
   * [TaskKind.InitialQuestions] batch reads the project.
   */
  suspend fun createProject(synopsis: String, title: String? = null): Project {
    val now = now()
    val projectId = randomUUID()

    val trimmedTitle = title.orEmpty().trim()
    val resolvedTitle = trimmedTitle.takeIf { it.isNotEmpty() }
      ?.substring(0, trimmedTitle.length.coerceAtMost(30))
      .orEmpty()

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
    if (resolvedTitle.isEmpty()) {
      enqueueTitleRecommendation(projectId)
    }
    enqueueQuestionGeneration(project.id, roundId, TaskKind.InitialQuestions)
    return project
  }

  /**
   * Fills in the project's recommended title on the task runner, re-reading first.
   * The engine is frozen via [engineSelector] at enqueue time, so a queued task
   * runs the engine it was created under even if settings change before it runs.
   */
  private fun enqueueTitleRecommendation(projectId: String) {
    val engine = engineSelector.selectedEngine()
    taskRunner.enqueue(projectId, TaskKind.TitleRecommendation) { taskId ->
      val reloaded = storage.getProject(projectId) ?: return@enqueue
      val recommended = engine.recommendTitle(reloaded.synopsis, activityId = taskId)
      if (recommended.isNotBlank()) {
        storage.saveProject(
          reloaded.copy(
            editableTitle = recommended.take(30),
            updatedAt = now(),
          )
        )
      }
    }
  }

  /**
   * The single generation seam every "request new questions" touchpoint flows
   * through: generate for [roundId] (initial or follow-up depending on [kind]),
   * dedupe against everything already asked in the project, shuffle, and
   * persist as a task on the [taskRunner] so the caller returns immediately and
   * the UI can surface progress (and navigate away freely) while it runs.
   *
   * The batch's [QuestionBatch.done] is latched onto the round as its
   * [RoundOutcome], which is what the "Get more questions" affordances read
   * (see `Project.currentPhaseExhausted`) — a phase is exhausted only because
   * a generation said so, never because a capability probe guessed. A batch
   * that yields nothing while claiming more is available is a dead end, not a
   * success: it latches [RoundOutcome.Failed] and fails the task so the
   * affordance stays available and the failure is visible and retryable.
   */
  private fun enqueueQuestionGeneration(
    projectId: String,
    roundId: String,
    kind: TaskKind,
  ) {
    val engine = engineSelector.selectedEngine()
    taskRunner.enqueue(projectId, kind) { taskId ->
      val reloaded = storage.getProject(projectId) ?: return@enqueue
      val round = reloaded.rounds.find { it.id == roundId } ?: return@enqueue
      val generated = try {
        when (kind) {
          TaskKind.InitialQuestions -> engine.generateInitialQuestions(
            editableTitle = reloaded.editableTitle,
            synopsis = reloaded.synopsis,
            roundId = round.id,
            phase = round.phase,
            activityId = taskId,
          )

          TaskKind.FollowUpQuestions -> engine.generateFollowUpQuestions(
            synopsis = reloaded.synopsis,
            previousQuestions = reloaded.questions,
            roundId = round.id,
            phase = round.phase,
            activityId = taskId,
          )

          else -> return@enqueue
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Record why the round came up empty before the task fails, so the UI
        // can explain itself on the next read.
        recordOutcome(reloaded, round.id, RoundOutcome.Failed, e.message ?: e.toString())
        throw e
      }
      val fresh = generated.questions
        .filterNot { newQuestion -> reloaded.questions.any { it.text == newQuestion.text } }
        .shuffled()
      val outcome = when {
        // The engine's explicit stop condition wins even when it answered with
        // questions, so the phase reads exhausted either way.
        generated.done -> RoundOutcome.Exhausted
        fresh.isEmpty() -> RoundOutcome.Failed
        else -> RoundOutcome.MoreAvailable
      }
      storage.saveProject(
        reloaded.copy(
          questions = reloaded.questions + fresh,
          rounds = reloaded.rounds.map { if (it.id == round.id) it.withOutcome(outcome) else it },
          updatedAt = now(),
        )
      )
      if (outcome == RoundOutcome.Failed) throw PlanningEngine.AnalysisFailure(NoNewQuestionsMessage)
    }
  }

  /**
   * Latches [outcome] onto one round of [project] and persists the project,
   * best-effort: used on the engine-failure path, where the original failure
   * must still reach the task.
   */
  private suspend fun recordOutcome(
    project: Project,
    roundId: String,
    outcome: RoundOutcome,
    detail: String? = null,
  ) {
    runCatching {
      storage.saveProject(
        project.copy(
          rounds = project.rounds.map { if (it.id == roundId) it.withOutcome(outcome, detail) else it },
          updatedAt = now(),
        )
      )
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

  /**
   * Opens a new [RoundOrigin.UserRequested] round in the project's current
   * phase and enqueues its follow-up generation as a task, returning
   * immediately with the round in place. When the phase is exhausted (its
   * newest round latched `RoundOutcome.Exhausted`) no round is opened and the
   * project is returned untouched; the "Get more questions" affordance gates on
   * the same derived answer to reach here.
   */
  suspend fun generateMoreQuestions(projectId: String): Project? {
    val project = storage.getProject(projectId) ?: return null
    if (project.currentPhaseExhausted) return project
    val now = now()
    val round = nextRound(project, RoundOrigin.UserRequested, now)
    val updated = project.copy(
      questions = project.questions,
      rounds = project.rounds + round,
      updatedAt = now,
    )
    storage.saveProject(updated)
    enqueueQuestionGeneration(project.id, round.id, TaskKind.FollowUpQuestions)
    return updated
  }

  /**
   * Wraps up the round(s) currently in progress and advances the project to
   * [nextPhase], opening its first `Initial` round immediately; the round's
   * fresh batch is generated on the [taskRunner] (deduped against everything
   * already asked in the project, new questions shuffled) so the phase swap
   * lands instantly and questions stream in when the task succeeds.
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

    val updatedProject = project.copy(
      questions = project.questions,
      rounds = completedRounds + round,
      updatedAt = now,
    )
    storage.saveProject(updatedProject)
    enqueueQuestionGeneration(project.id, round.id, TaskKind.InitialQuestions)
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

  private companion object {
    /**
     * Why a generation that produced nothing new is reported as a failure:
     * user-facing on the task row, and the round's [RoundOutcome.Failed]
     * detail. An engine that returns an empty batch (or only questions already
     * asked) while claiming more is available is a dead end, not a success.
     */
    const val NoNewQuestionsMessage =
      "The planner came back with no new questions for this phase. Try again."
  }
}
