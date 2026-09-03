package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProjectListViewModel(private val repository: ThinkerRepository) {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun loadProjects() {
        _isLoading.value = true
        repository.getAllProjects { result ->
            result.onSuccess { projects ->
                _projects.value = projects
            }.onFailure { e ->
                _projects.value = emptyList()
                _error.value = "Failed to load projects: ${e.message ?: "Unknown error"}"
            }
            _isLoading.value = false
        }
    }

    private val _createdProject = MutableStateFlow<Project?>(null)
    val createdProject: StateFlow<Project?> = _createdProject.asStateFlow()

    fun createProject(synopsis: String, title: String?) {
        repository.createProject(synopsis, title) { result ->
            result.onSuccess { project ->
                _createdProject.value = project
                loadProjects()
            }.onFailure { e ->
                _error.value = "Failed to create project: ${e.message ?: "Unknown error"}"
            }
        }
    }

    fun consumeCreatedProject() {
        _createdProject.value = null
    }

    fun deleteProject(id: String) {
        repository.deleteProject(id) { result ->
            result.onSuccess {
                loadProjects()
            }.onFailure { e ->
                _error.value = "Failed to delete project: ${e.message ?: "Unknown error"}"
            }
        }
    }
}

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
            result.onSuccess { project ->
                _project.value = project
            }.onFailure { e ->
                _project.value = null
                _error.value = "Failed to load project: ${e.message ?: "Unknown error"}"
            }
            _isLoading.value = false
        }
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
