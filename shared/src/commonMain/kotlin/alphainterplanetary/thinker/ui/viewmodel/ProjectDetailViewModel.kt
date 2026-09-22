package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.Phase
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

  private val _nextPhaseSuggestions = MutableStateFlow<List<Phase>?>(null)
  val nextPhaseSuggestions: StateFlow<List<Phase>?> = _nextPhaseSuggestions.asStateFlow()

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
          // Availability is cached (no engine call here); a stale check is
          // enqueued as a task and this reloads once it lands.
          val canGenerate = repository.canGenerateMoreInPhase(id)
          _uiState.value = ProjectDetailUiState.Success(loaded, canGenerate)
          repository.ensureFreshRemainingInPhase(id)
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

  fun generateMoreQuestions(projectId: String) {
    vmScope.launch {
      try {
        repository.generateMoreQuestions(projectId)
        loadProject(projectId)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to generate more questions: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun advanceToPhase(projectId: String, phase: Phase) {
    vmScope.launch {
      try {
        repository.advanceToPhase(projectId, phase)
        loadProject(projectId)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to advance phase: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  /** (Re)computes the next-phase suggestions, `null` in the flow while computing. */
  fun loadNextPhaseSuggestions(projectId: String) {
    _nextPhaseSuggestions.value = null
    vmScope.launch {
      try {
        val project = repository.getProject(projectId)
        _nextPhaseSuggestions.value = project?.nextPhaseSuggestions ?: emptyList()
      } catch (e: Exception) {
        // TODO(phase-3): surface a recommendation failure; degrade to none for now
        _nextPhaseSuggestions.value = emptyList()
      }
    }
  }

  private fun persistOrder(reordered: Project) {
    _uiState.value = successPreservingAvailability(reordered)
    vmScope.launch {
      repository.saveQuestionOrder(reordered.id, reordered.questionOrderIds)
    }
  }

  /** A [ProjectDetailUiState.Success] that keeps the previously-computed remaining-in-phase flag. */
  private fun successPreservingAvailability(project: Project): ProjectDetailUiState.Success =
    ProjectDetailUiState.Success(
      project = project,
      canGenerateMoreInPhase = (_uiState.value as? ProjectDetailUiState.Success)
        ?.canGenerateMoreInPhase ?: false,
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
      _uiState.value = successPreservingAvailability(optimistic)

      vmScope.launch {
        try {
          repository.saveAnswer(projectId, questionId, text, completed)
        } catch (e: Exception) {
          _uiState.value = successPreservingAvailability(current)
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
    _uiState.value = successPreservingAvailability(optimistic)

    vmScope.launch {
      try {
        repository.ignoreQuestion(projectId, questionId)
      } catch (e: Exception) {
        _uiState.value = successPreservingAvailability(snapshot)
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
    _uiState.value = successPreservingAvailability(optimistic)

    vmScope.launch {
      try {
        repository.unignoreQuestion(projectId, questionId)
      } catch (e: Exception) {
        _uiState.value = successPreservingAvailability(snapshot)
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
          _uiState.value = successPreservingAvailability(project)
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
