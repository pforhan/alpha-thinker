package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProjectDetailViewModel(private val repository: ThinkerRepository) {
    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun loadProject(id: String) {
        _isLoading.value = true
        repository.getProject(id) { result ->
            result.onSuccess { loaded ->
                _project.value = loaded
            }.onFailure { e ->
                _project.value = null
                _error.value = "Failed to load project: ${e.message ?: "Unknown error"}"
            }
            _isLoading.value = false
        }
    }

    fun askLater(questionId: String) {
        val current = _project.value ?: return
        val reordered = current.moveToEnd(questionId)
        if (reordered == current) return
        persistOrder(reordered)
    }

    fun shuffle() {
        val current = _project.value ?: return
        val unanswered = current.unansweredQuestions
        if (unanswered.size <= 3) return
        persistOrder(current.rotateToEnd(unanswered.take(3).map { it.id }))
    }

    private fun persistOrder(reordered: Project) {
        _project.value = reordered
        repository.saveQuestionOrder(reordered.id, reordered.questionOrderIds) { }
    }

    fun updateAnswer(projectId: String, questionId: String, text: String, isDraft: Boolean) {
        repository.updateAnswer(projectId, questionId, text, isDraft) { result ->
            result.onSuccess {
                loadProject(projectId)
            }.onFailure { e ->
                _error.value = "Failed to save answer: ${e.message ?: "Unknown error"}"
            }
        }
    }

    fun ignoreQuestion(projectId: String, questionId: String) {
        repository.ignoreQuestion(projectId, questionId) { result ->
            result.onSuccess {
                loadProject(projectId)
            }.onFailure { e ->
                _error.value = "Failed to ignore question: ${e.message ?: "Unknown error"}"
            }
        }
    }

    fun unignoreQuestion(projectId: String, questionId: String) {
        repository.unignoreQuestion(projectId, questionId) { result ->
            result.onSuccess {
                loadProject(projectId)
            }.onFailure { e ->
                _error.value = "Failed to unignore question: ${e.message ?: "Unknown error"}"
            }
        }
    }

    fun deleteAnswer(projectId: String, questionId: String, answerId: Long) {
        repository.deleteAnswer(projectId, questionId, answerId) { result ->
            result.onSuccess {
                loadProject(projectId)
            }.onFailure { e ->
                _error.value = "Failed to delete answer: ${e.message ?: "Unknown error"}"
            }
        }
    }

    fun updateProject(id: String, title: String, synopsis: String, mode: ProjectUpdateMode) {
        repository.updateProject(id, title, synopsis, mode) { result ->
            result.onSuccess { project ->
                _project.value = project
            }.onFailure { e ->
                _error.value = "Failed to update project: ${e.message ?: "Unknown error"}"
            }
        }
    }
}
