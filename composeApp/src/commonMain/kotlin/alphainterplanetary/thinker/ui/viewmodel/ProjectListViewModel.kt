package alphainterplanetary.thinker.ui.viewmodel

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