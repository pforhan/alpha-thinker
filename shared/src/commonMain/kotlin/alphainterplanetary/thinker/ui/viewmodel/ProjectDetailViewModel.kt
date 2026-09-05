package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ProjectDetailUiState {
  data object Loading : ProjectDetailUiState
  data class Success(val project: Project) : ProjectDetailUiState
  data class Error(val message: String) : ProjectDetailUiState
}

class ProjectDetailViewModel(private val repository: ThinkerRepository) {
  private val _uiState = MutableStateFlow<ProjectDetailUiState>(ProjectDetailUiState.Loading)
  val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

  fun loadProject(id: String) {
    _uiState.value = ProjectDetailUiState.Loading
    repository.getProject(id) { result ->
      result.onSuccess { loaded ->
        if (loaded == null) {
          _uiState.value = ProjectDetailUiState.Error("Failed to load project: project not found")
        } else {
          _uiState.value = ProjectDetailUiState.Success(loaded)
        }
      }.onFailure { e ->
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

  private fun persistOrder(reordered: Project) {
    _uiState.value = ProjectDetailUiState.Success(reordered)
    repository.saveQuestionOrder(reordered.id, reordered.questionOrderIds) { }
  }

  fun updateAnswer(projectId: String, questionId: String, text: String, isDraft: Boolean) {
    repository.updateAnswer(projectId, questionId, text, isDraft) { result ->
      result.onSuccess {
        loadProject(projectId)
      }.onFailure { e ->
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to save answer: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun ignoreQuestion(projectId: String, questionId: String) {
    repository.ignoreQuestion(projectId, questionId) { result ->
      result.onSuccess {
        loadProject(projectId)
      }.onFailure { e ->
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to ignore question: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun unignoreQuestion(projectId: String, questionId: String) {
    repository.unignoreQuestion(projectId, questionId) { result ->
      result.onSuccess {
        loadProject(projectId)
      }.onFailure { e ->
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to unignore question: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun deleteAnswer(projectId: String, questionId: String, answerId: Long) {
    repository.deleteAnswer(projectId, questionId, answerId) { result ->
      result.onSuccess {
        loadProject(projectId)
      }.onFailure { e ->
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to delete answer: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun updateProject(id: String, title: String, synopsis: String, mode: ProjectUpdateMode) {
    repository.updateProject(id, title, synopsis, mode) { result ->
      result.onSuccess { project ->
        if (project != null) {
          _uiState.value = ProjectDetailUiState.Success(project)
        }
      }.onFailure { e ->
        _uiState.value = ProjectDetailUiState.Error(
          "Failed to update project: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }
}
