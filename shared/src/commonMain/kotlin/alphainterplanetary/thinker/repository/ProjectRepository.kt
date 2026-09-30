package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.engine.PlanningContext
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
  private val settings: SettingsRepository,
  private val activityLogger: ActivityLogger,
) {

  /**
   * Persists the project shell immediately and returns it. When no [title] is
   * supplied, the shell ships with an empty title and a [TaskKind.TitleRecommendation]
   * task fills it in from the synopsis — the title and batch both run in the
   * serial engine group ([TaskKind.group]), so the title lands before the
   * [TaskKind.QuestionGeneration] batch reads the project.
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
    enqueueQuestionGeneration(project.id, roundId, ContextCompaction.DropEarlierAnswers)
    return project
  }

  /**
   * Re-runs the title recommendation for an existing project, which is how the
   * user retries a title that never landed. Does nothing if the project already
   * has a title (a recommendation never overwrites one), so it is safe to call
   * on any project.
   */
  fun recommendTitle(projectId: String) {
    enqueueTitleRecommendation(projectId)
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
      // Re-checked here, not just at the call site: the project may have been
      // titled (by the user, or by a recommendation that did land) between the
      // enqueue and the run.
      if (reloaded.editableTitle.isNotBlank()) return@enqueue
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
   * through: generate for [roundId] with the project's questions as context
   * (compacted to the configured budget by [PlanningContext], which is also
   * where the engine's own rendering lives), dedupe against what was already
   * asked, shuffle, and persist as a [TaskKind.QuestionGeneration] task on the
   * [taskRunner] so the caller returns immediately and the UI can surface
   * progress (and navigate away freely) while it runs. Every kind of round — a
   * project's opening batch, "Get more questions", a wrap-up advance into a new
   * phase — is this one call; what varies is the round, not the interaction.
   *
   * [compaction] is the user's answer to [checkContext]'s near-limit question,
   * and it is taken here rather than decided at this seam because the seam runs
   * on the task runner, after the user has long since been asked: a question
   * asked from inside a background task could only be asked by blocking on it.
   * [ContextCompaction.SummarizeEarlierPhases] therefore summarizes *inside*
   * the task, where the summarize requests are ordinary log rows under the same
   * activity as the batch they feed.
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
    compaction: ContextCompaction,
  ) {
    val engine = engineSelector.selectedEngine()
    taskRunner.enqueue(projectId, TaskKind.QuestionGeneration) { taskId ->
      val reloaded = storage.getProject(projectId) ?: return@enqueue
      val round = reloaded.rounds.find { it.id == roundId } ?: return@enqueue
      // The engine renders whatever transcript it is handed, so the budget is
      // applied here: an over-long project reaches it with the earlier phases'
      // compacted away — the current phase's answers always intact, whether
      // they were dropped or summarized. Dedupe still reads the whole project
      // below — a compacted answer never costs a question its place in the
      // "already asked" set.
      val context = buildContext(reloaded, engine, compaction, budgetTokens(engine), taskId)
      if (context.droppedAnswers > 0) {
        logContextCompacted(taskId, reloaded.id, context.droppedAnswers)
      }
      val generated = try {
        engine.generateQuestions(
          title = reloaded.editableTitle,
          synopsis = reloaded.synopsis,
          previousQuestions = context.questions,
          roundId = round.id,
          phase = round.phase,
          activityId = taskId,
          priorSummaries = context.summaries,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Record why the round came up empty before the task fails, so the UI
        // can explain itself on the next read. Best-effort, and deliberately so:
        // the engine's own failure is the one that has to reach the task, so a
        // failed write here must not replace it with a storage error.
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
        runCatching {
          storage.saveProject(
            reloaded.withRoundOutcome(round.id) { it.withFailed(detail) }.copy(updatedAt = now())
          )
        }
        throw e
      }
      val fresh = generated.questions
        .filterNot { newQuestion -> reloaded.questions.any { it.text == newQuestion.text } }
        .shuffled()
      // A batch that came back empty (or only with questions already asked) while
      // claiming more are available is a dead end, not a success, so the round
      // records why and the task fails instead of quietly landing nothing.
      val noNewQuestions = fresh.isEmpty() && !generated.done
      storage.saveProject(
        reloaded
          .withRoundOutcome(round.id) { current ->
            when {
              // The engine's explicit stop condition wins even when it answered
              // with questions, so the phase reads exhausted either way.
              generated.done -> current.withExhausted()
              noNewQuestions -> current.withFailed(NoNewQuestionsMessage)
              else -> current.withMoreAvailable()
            }
          }
          .copy(
            questions = reloaded.questions + fresh,
            updatedAt = now(),
          )
      )
      if (noNewQuestions) throw PlanningEngine.AnalysisFailure(NoNewQuestionsMessage)
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
   * How much planning context a project would send with its next round, and
   * whether that is worth putting to the user first.
   *
   * Read before anything is enqueued, because that is the only moment the
   * answer can be acted on: the near-limit choice is a dialog, and a dialog
   * asked from a background task is a dialog nobody sees. Null when the project
   * is gone, which the caller treats as "don't ask, there's nothing to ask
   * about".
   */
  suspend fun checkContext(projectId: String): ContextCheck? {
    val project = storage.getProject(projectId) ?: return null
    val engine = engineSelector.selectedEngine()
    val estimatedTokens = PlanningContext.estimateTokens(project.questions)
    // Everything below is about a prompt window. An engine that composes no
    // prompt has no window to overrun, so it is never asked the question — the
    // measurement is kept because it is true, but every action the answer would
    // enable is reported as unavailable rather than offered and then performed
    // for an engine that was never going to read the result.
    val window = engine.contextWindowTokens
      ?: return ContextCheck(
        estimatedTokens = estimatedTokens,
        windowTokens = null,
        budgetTokens = null,
        nearLimit = false,
        droppableAnswers = 0,
        summarizablePhases = emptyList(),
        canSummarize = false,
      )
    val budgetTokens = PlanningContext.budgetTokens(window)
    val phases = PlanningContext.summarizablePhases(project)
    return ContextCheck(
      estimatedTokens = estimatedTokens,
      windowTokens = window,
      budgetTokens = budgetTokens,
      nearLimit = PlanningContext.nearLimit(
        estimatedTokens = estimatedTokens,
        budgetTokens = budgetTokens,
      ),
      droppableAnswers = PlanningContext.trim(project, budgetTokens).droppedAnswers,
      summarizablePhases = phases.map { it.phase },
      canSummarize = engine.canSummarize && phases.isNotEmpty(),
    )
  }

  /**
   * The token budget for the engine as it currently resolves: its window's
   * [PlanningContext.TranscriptSharePercent], or null when there is no window to
   * take a share of.
   *
   * Nothing to configure here — the window is read from the model, and the share
   * is a constant — so this is the whole of the "budget" as far as settings are
   * concerned.
   */
  private fun budgetTokens(engine: PlanningEngine): Int? =
    engine.contextWindowTokens?.let { window -> PlanningContext.budgetTokens(window) }

  /**
   * The transcript one generation sends: the user's [compaction] applied to
   * [project], measured against [budgetTokens].
   *
   * [ContextCompaction.SummarizeEarlierPhases] walks the past phases oldest
   * first and asks the engine for a summary of each, re-measuring after every
   * one and stopping as soon as the transcript fits — so a project that only
   * just crossed the line spends one request, and one that is far over it
   * spends one per phase it takes, each of which the engine and the log both
   * see as its own interaction. Whatever it doesn't manage, [PlanningContext.trim]
   * still has to drop: summarization is a model that may return nothing useful,
   * and the budget is a hard limit rather than a target.
   *
   * An engine that doesn't read the answers short-circuits all of it: its
   * budget is not this method's to enforce, and compacting text it would
   * discard would file a "context compacted" note about a loss the model never
   * saw.
   */
  private suspend fun buildContext(
    project: Project,
    engine: PlanningEngine,
    compaction: ContextCompaction,
    budgetTokens: Int?,
    taskId: String,
  ): PlanningContext.TrimResult = when {
    // No window, or nothing to spend: the project goes over whole. An engine
    // that composes no prompt has no budget, and "keep everything" is that
    // answer without having to know why.
    budgetTokens == null || compaction == ContextCompaction.KeepEverything ->
      PlanningContext.TrimResult(project.questions)

    compaction == ContextCompaction.DropEarlierAnswers ->
      PlanningContext.trim(project, budgetTokens)

    else -> {
      val summaries = mutableListOf<PlanningContext.PhaseSummary>()
      var questions = project.questions
      for (unit in PlanningContext.summarizablePhases(project)) {
        if (summaries.isNotEmpty() &&
          PlanningContext.estimateTokens(questions, summaries) <= budgetTokens
        ) {
          break
        }
        val summary = engine.summarizePriorAnswers(
          title = project.editableTitle,
          synopsis = project.synopsis,
          phase = unit.phase,
          transcript = unit.transcript,
          activityId = taskId,
        )
        summaries += PlanningContext.PhaseSummary(
          phase = unit.phase,
          summary = summary,
          coversQuestionIds = unit.coveredQuestionIds,
        )
        // The phase's answers are replaced by the summary, which is what the
        // next re-measurement has to see — the summary only costs anything once
        // the verbatim answers it stands in for are actually gone.
        questions = questions.map { question ->
          if (question.id in unit.coveredQuestionIds) question.asCompacted() else question
        }
        logPhaseSummarized(taskId, project.id, unit, summary)
      }
      PlanningContext.trim(project.copy(questions = questions), budgetTokens, summaries)
    }
  }

  /**
   * Opens a new [RoundOrigin.UserRequested] round in the project's current
   * phase and enqueues its follow-up generation as a task, returning
   * immediately with the round in place. When the phase is exhausted (its
   * newest round latched `RoundOutcome.Exhausted`) no round is opened and the
   * project is returned untouched; the "Get more questions" affordance gates on
   * the same derived answer to reach here.
   *
   * [compaction] is the answer to [checkContext], which the caller is expected
   * to have asked about first; [ContextCompaction.DropEarlierAnswers] is the
   * default for anything that didn't (a project's opening round, or a caller
   * that skipped the check).
   */
  suspend fun generateMoreQuestions(
    projectId: String,
    compaction: ContextCompaction = ContextCompaction.DropEarlierAnswers,
  ): Project? {
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
    enqueueQuestionGeneration(project.id, round.id, compaction)
    return updated
  }

  /**
   * Wraps up the round(s) currently in progress and advances the project to
   * [nextPhase], opening its first `Initial` round immediately; the round's
   * fresh batch is generated on the [taskRunner] (deduped against everything
   * already asked in the project, new questions shuffled) so the phase swap
   * lands instantly and questions stream in when the task succeeds.
   *
   * A phase advance is the moment a project's transcript is most likely to need
   * compacting, so [compaction] carries the [checkContext] answer the same way
   * [generateMoreQuestions] does.
   */
  suspend fun advanceToPhase(
    projectId: String,
    nextPhase: Phase,
    compaction: ContextCompaction = ContextCompaction.DropEarlierAnswers,
  ): Project? {
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
    enqueueQuestionGeneration(project.id, round.id, compaction)
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

  /**
   * Files one note on the task's activity that the context the engine was given
   * had to be compacted to fit the configured budget — how many answers were
   * dropped, which the engine (and its prompt) never sees. A standalone
   * [LogContext] per call keeps the note in the task's activity (grouped by
   * `activityId` in the read model) without sharing lifecycle state with the
   * task's own context; the note is plain text, so the read model's
   * started/terminal parsing is unaffected. Deliberately phrased as
   * "compacted … dropped" so the near-limit compaction choice can extend it
   * without a breaking change — which it does, in [logPhaseSummarized].
   */
  private suspend fun logContextCompacted(taskId: String, projectId: String, dropped: Int) {
    val noun = if (dropped == 1) "answer" else "answers"
    activityLogger.context(
      activityId = taskId,
      category = LogCategory.TaskRun,
      source = LogSource.TaskRunner,
      projectId = projectId,
    ).append("context compacted: $dropped $noun dropped to fit budget")
  }

  /**
   * Files one note per summarized phase, alongside that phase's own
   * `prompt:`/`response:` pair. The pair carries the text itself; this carries
   * the fact and the size of the trade, in the same words the
   * dropped-answers note uses so the two read as one story in the log — a
   * compacted context says how many answers it lost, whichever way it lost
   * them.
   */
  private suspend fun logPhaseSummarized(
    taskId: String,
    projectId: String,
    unit: PlanningContext.PhaseTranscript,
    summary: String,
  ) {
    val count = unit.questions.size
    val noun = if (count == 1) "answer" else "answers"
    activityLogger.context(
      activityId = taskId,
      category = LogCategory.TaskRun,
      source = LogSource.TaskRunner,
      projectId = projectId,
    ).append(
      "context compacted: ${unit.phase.label} summarized, $count $noun replaced " +
        "(${unit.estimatedTokens}t → ${PlanningContext.estimateTokens(summary)}t)"
    )
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
