package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingUndo(
  val token: Long,
  val snapshot: Project,
  val message: String,
)

sealed interface ProjectDetailUiState {
  data object Loading : ProjectDetailUiState
  data class Success(val project: Project) : ProjectDetailUiState
  data class Error(val message: String) : ProjectDetailUiState
}

class ProjectDetailViewModel(
  private val repository: ProjectRepository,
  private val scope: CoroutineScope,
) {
  private val _uiState = MutableStateFlow<ProjectDetailUiState>(ProjectDetailUiState.Loading)
  val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

  private var undoToken = 0L
  private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)
  val pendingUndo: StateFlow<PendingUndo?> = _pendingUndo.asStateFlow()

  fun loadProject(id: String) {
    _uiState.value = ProjectDetailUiState.Loading
    scope.launch {
      try {
        val loaded = repository.getProject(id)
        if (loaded == null) {
          _uiState.value = ProjectDetailUiState.Error("Failed to load project: project not found")
        } else {
          _uiState.value = ProjectDetailUiState.Success(loaded)
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
    scope.launch {
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

  private fun persistOrder(reordered: Project) {
    _uiState.value = ProjectDetailUiState.Success(reordered)
    scope.launch {
      repository.saveQuestionOrder(reordered.id, reordered.questionOrderIds)
    }
  }

  fun saveAnswer(projectId: String, questionId: String, text: String, completed: Boolean) {
    val current = (_uiState.value as? ProjectDetailUiState.Success)?.project
    val deletingAnswer = !completed && text.isBlank() &&
      current?.questions?.find { it.id == questionId }?.currentAnswer != null

    if (deletingAnswer && current != null) {
      val optimistic = current.copy(
        questions = current.questions.map { q ->
          if (q.id == questionId) {
            q.copy(answerId = null, draftText = null, draftUpdatedAt = null)
          } else {
            q
          }
        }
      )
      beginUndoable(current, "Answer deleted")
      _uiState.value = ProjectDetailUiState.Success(optimistic)

      scope.launch {
        try {
          repository.saveAnswer(projectId, questionId, text, completed)
        } catch (e: Exception) {
          _uiState.value = ProjectDetailUiState.Success(current)
          clearUndoable()
          _uiState.value = ProjectDetailUiState.Error(
            "Failed to save answer: ${e.message ?: "Unknown error"}"
          )
        }
      }
      return
    }

    scope.launch {
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
        if (q.id == questionId) q.copy(ignoredAt = now()) else q
      }
    )

    beginUndoable(snapshot, "Question ignored")
    _uiState.value = ProjectDetailUiState.Success(optimistic)

    scope.launch {
      try {
        repository.ignoreQuestion(projectId, questionId)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Success(snapshot)
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
        if (q.id == questionId) q.copy(ignoredAt = null) else q
      }
    )

    beginUndoable(snapshot, "Question restored")
    _uiState.value = ProjectDetailUiState.Success(optimistic)

    scope.launch {
      try {
        repository.unignoreQuestion(projectId, questionId)
      } catch (e: Exception) {
        _uiState.value = ProjectDetailUiState.Success(snapshot)
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
    scope.launch {
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
    scope.launch {
      try {
        val project = repository.updateProject(id, title, synopsis, mode)
        if (project != null) {
          _uiState.value = ProjectDetailUiState.Success(project)
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
}
