package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.repository.ContextCheck
import alphainterplanetary.thinker.repository.ContextCompaction
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tasks.GenerationTask
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class PendingUndo(
  val token: Long,
  val snapshot: Project,
  val message: String,
)

sealed interface ProjectDetailUiState {
  data object Loading : ProjectDetailUiState
  data class Success(
    val project: Project,
    val canGenerateMoreInPhase: Boolean,
  ) : ProjectDetailUiState
  data class Error(val message: String) : ProjectDetailUiState
}

/**
 * A request for more questions (or a phase advance) that is parked while the
 * user decides what to do about an over-long planning transcript.
 *
 * The check has to happen before the round is opened, so the request is held
 * whole rather than half-done: the phase being advanced to rides along, so
 * answering the question never has to remember which button the user pressed.
 */
sealed interface ContextPrompt {
  /** What the project would send, and what the choice is worth. */
  val check: ContextCheck

  /** "Get more questions" in the current phase. */
  data class MoreQuestions(override val check: ContextCheck) : ContextPrompt

  /** The wrap-up advance into [phase]. */
  data class PhaseAdvance(override val check: ContextCheck, val phase: Phase) : ContextPrompt
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModel(
  private val repository: ProjectRepository,
  private val taskRunner: TaskRunner,
  scope: CoroutineScope,
) {
  /** Cancelled by [close] so UI teardown stops the per-VM work (e.g. the task collector). */
  private val vmJob = SupervisorJob(scope.coroutineContext[Job])
  private val vmScope = CoroutineScope(scope.coroutineContext + vmJob)

  private val _uiState = MutableStateFlow<ProjectDetailUiState>(ProjectDetailUiState.Loading)
  val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

  private var undoToken = 0L
  private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)
  val pendingUndo: StateFlow<PendingUndo?> = _pendingUndo.asStateFlow()

  /**
   * Candidate next phases for the wrap-up chooser, owned here so the empty
   * state and the advance dialog read one list. `null` while unresolved (today
   * the derivation is synchronous in [refresh]; the nullable flow is the seam
   * for a future async, engine-proposed recommendation).
   */
  private val _nextPhaseSuggestions = MutableStateFlow<List<Phase>?>(null)
  val nextPhaseSuggestions: StateFlow<List<Phase>?> = _nextPhaseSuggestions.asStateFlow()

  /** The parked near-limit request, or null when none is waiting on the user. */
  private val _contextPrompt = MutableStateFlow<ContextPrompt?>(null)
  val contextPrompt: StateFlow<ContextPrompt?> = _contextPrompt.asStateFlow()

  private val _projectId = MutableStateFlow<String?>(null)

  private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())
  val tasks: StateFlow<List<GenerationTask>> = _tasks.asStateFlow()

  /** Terminal task ids already reloaded for, so each completion reloads exactly once. */
  private val handledTerminalTaskIds = mutableSetOf<String>()

  init {
    vmScope.launch {
      _projectId
        .flatMapLatest { id ->
          if (id == null) flowOf(emptyList()) else taskRunner.tasksFor(id)
        }
        .collect { active ->
          _tasks.value = active
          for (task in active) {
            if (task.isFinished && handledTerminalTaskIds.add(task.id)) {
              refresh()
            }
          }
        }
    }
  }

  fun loadProject(id: String) {
    _projectId.value = id
    _uiState.value = ProjectDetailUiState.Loading
    _nextPhaseSuggestions.value = null
    refresh()
  }

  /** Reloads the loaded project without toggling Loading, so generation results swap in. */
  private fun refresh() {
    val id = _projectId.value ?: return
    vmScope.launch {
      try {
        val loaded = repository.getProject(id)
        if (loaded == null) {
          _uiState.value = ProjectDetailUiState.Error("Failed to load project: project not found")
        } else {
          // Publish the suggestions before the project so the empty state's
          // wrap-up block never renders against a stale/absent list.
          _nextPhaseSuggestions.value = loaded.nextPhaseSuggestions
          // Availability is a fact about the project's newest round, so it
          // needs no engine call and can't go stale within a session.
          _uiState.value = ProjectDetailUiState.Success(
            project = loaded,
            canGenerateMoreInPhase = !loaded.currentPhaseExhausted,
          )
        }
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to load project: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun askLater(questionId: String) {
    val current = (_uiState.value as? ProjectDetailUiState.Success)?.project ?: return
    val reordered = current.moveToEnd(questionId)
    if (reordered == current) return
    persistOrder(reordered)
  }

  fun shuffle() {
    val current = (_uiState.value as? ProjectDetailUiState.Success)?.project ?: return
    val unanswered = current.unansweredQuestions
    if (unanswered.size <= 3) return
    persistOrder(current.rotateToEnd(unanswered.take(3).map { it.id }))
  }

  /**
   * Requests a round in the current phase, asking first when the project's
   * planning transcript is over the budget.
   *
   * The check is a storage read and a measurement, so the common case — a
   * project well inside its budget — goes straight through to the repository
   * with the default compaction and never surfaces a dialog. A failed check
   * falls back to requesting the round anyway rather than stranding the
   * affordance: the budget is a guard rail, not a gate.
   */
  fun requestMoreQuestions(projectId: String) {
    vmScope.launch {
      val check = checkContext(projectId) ?: run {
        generateMoreQuestions(projectId, ContextCompaction.DropEarlierAnswers)
        return@launch
      }
      if (check.nearLimit) {
        _contextPrompt.value = ContextPrompt.MoreQuestions(check)
      } else {
        generateMoreQuestions(projectId, ContextCompaction.DropEarlierAnswers)
      }
    }
  }

  /**
   * Answers the parked [ContextPrompt] and carries out the request behind it.
   * Taking the prompt out of the flow first means a second tap can't enqueue
   * the same round twice while this is in flight.
   */
  fun resolveContextPrompt(compaction: ContextCompaction) {
    val prompt = _contextPrompt.value ?: return
    _contextPrompt.value = null
    val projectId = _projectId.value ?: return
    when (prompt) {
      is ContextPrompt.MoreQuestions -> generateMoreQuestions(projectId, compaction)
      is ContextPrompt.PhaseAdvance -> advanceToPhase(projectId, prompt.phase, compaction)
    }
  }

  /** Backs out of the parked request, changing nothing. */
  fun dismissContextPrompt() {
    _contextPrompt.value = null
  }

  /** The project's context check, or null when the project is gone. */
  private suspend fun checkContext(projectId: String): ContextCheck? =
    runCatching { repository.checkContext(projectId) }
      .getOrNull()

  private fun generateMoreQuestions(projectId: String, compaction: ContextCompaction) {
    vmScope.launch {
      try {
        repository.generateMoreQuestions(projectId, compaction)
        loadProject(projectId)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to generate more questions: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  /**
   * Re-runs the title recommendation for a project that never got one. Like
   * question generation it is a task, not a blocking call, so there is nothing to
   * await: the project reloads when the task lands (see the task collector).
   */
  fun retryTitle(projectId: String) {
    repository.recommendTitle(projectId)
  }

  /**
   * Requests the wrap-up advance into [phase], asking first when the project's
   * planning transcript is over the budget — the moment a project is
   * most likely to need it, since the phase it is leaving behind is the one
   * whose answers the next round will no longer need in full.
   */
  fun requestPhaseAdvance(projectId: String, phase: Phase) {
    vmScope.launch {
      val check = checkContext(projectId) ?: run {
        advanceToPhase(projectId, phase, ContextCompaction.DropEarlierAnswers)
        return@launch
      }
      if (check.nearLimit) {
        _contextPrompt.value = ContextPrompt.PhaseAdvance(check, phase)
      } else {
        advanceToPhase(projectId, phase, ContextCompaction.DropEarlierAnswers)
      }
    }
  }

  private fun advanceToPhase(projectId: String, phase: Phase, compaction: ContextCompaction) {
    vmScope.launch {
      try {
        repository.advanceToPhase(projectId, phase, compaction)
        loadProject(projectId)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to advance phase: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  private fun persistOrder(reordered: Project) {
    _uiState.value = successFor(reordered)
    vmScope.launch {
      repository.saveQuestionOrder(reordered.id, reordered.questionOrderIds)
    }
  }

  /**
   * A [ProjectDetailUiState.Success] for a locally-edited project; availability
   * is re-derived from the project's own rounds rather than carried over, so an
   * optimistic edit can never contradict the round it came from.
   */
  private fun successFor(project: Project): ProjectDetailUiState.Success =
    ProjectDetailUiState.Success(
      project = project,
      canGenerateMoreInPhase = !project.currentPhaseExhausted,
    )

  fun saveAnswer(projectId: String, questionId: String, text: String, completed: Boolean) {
    val current = (_uiState.value as? ProjectDetailUiState.Success)?.project
    val deletingAnswer = !completed && text.isBlank() &&
      current?.questions?.find { it.id == questionId }?.currentAnswer != null

    if (deletingAnswer) {
      val optimistic = current.copy(
        questions = current.questions.map { q ->
          if (q.id == questionId) {
            q.withoutAnswer()
          } else {
            q
          }
        }
      )
      beginUndoable(current, "Answer deleted")
      _uiState.value = successFor(optimistic)

      vmScope.launch {
        try {
          repository.saveAnswer(projectId, questionId, text, completed)
        } catch (e: Exception) {
          _uiState.value = successFor(current)
          clearUndoable()
          _uiState.value = ProjectDetailUiState.Error(
            "Failed to save answer: ${e.message ?: "Unknown error"}"
          )
        }
      }
      return
    }

    vmScope.launch {
      try {
        repository.saveAnswer(projectId, questionId, text, completed)
        loadProject(projectId)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to save answer: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun ignoreQuestion(projectId: String, questionId: String) {
    val snapshot = (_uiState.value as? ProjectDetailUiState.Success)?.project ?: return
    val optimistic = snapshot.copy(
      questions = snapshot.questions.map { q ->
        if (q.id == questionId) q.withIgnored(now()) else q
      }
    )

    beginUndoable(snapshot, "Question ignored")
    _uiState.value = successFor(optimistic)

    vmScope.launch {
      try {
        repository.ignoreQuestion(projectId, questionId)
      } catch (e: Exception) {
        _uiState.value = successFor(snapshot)
        clearUndoable()
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to ignore question: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun unignoreQuestion(projectId: String, questionId: String) {
    val snapshot = (_uiState.value as? ProjectDetailUiState.Success)?.project ?: return
    val optimistic = snapshot.copy(
      questions = snapshot.questions.map { q ->
        if (q.id == questionId) q.withoutIgnored() else q
      }
    )

    beginUndoable(snapshot, "Question restored")
    _uiState.value = successFor(optimistic)

    vmScope.launch {
      try {
        repository.unignoreQuestion(projectId, questionId)
      } catch (e: Exception) {
        _uiState.value = successFor(snapshot)
        clearUndoable()
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to unignore question: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun undo(pending: PendingUndo) {
    if (_pendingUndo.value?.token != pending.token) return
    _pendingUndo.value = null
    vmScope.launch {
      try {
        repository.restoreProject(pending.snapshot)
        loadProject(pending.snapshot.id)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to undo: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun updateProject(id: String, title: String, synopsis: String, mode: ProjectUpdateMode) {
    vmScope.launch {
      try {
        val project = repository.updateProject(id, title, synopsis, mode)
        if (project != null) {
          _uiState.value = successFor(project)
        }
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to update project: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  private fun beginUndoable(snapshot: Project, message: String) {
    undoToken += 1
    _pendingUndo.value = PendingUndo(undoToken, snapshot, message)
  }

  private fun clearUndoable() {
    _pendingUndo.value = null
  }

  /** Cancels pending VM work; the caller owns the view model's lifecycle. */
  fun close() {
    vmJob.cancel()
  }
}
