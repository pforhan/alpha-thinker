package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ProjectListUiState {
  data object Loading : ProjectListUiState
  data class Success(val projects: List<Project>) : ProjectListUiState
  data class Error(val message: String) : ProjectListUiState
}

class ProjectListViewModel(private val repository: ThinkerRepository) {
  private val _uiState = MutableStateFlow<ProjectListUiState>(ProjectListUiState.Loading)
  val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

  fun loadProjects() {
    _uiState.value = ProjectListUiState.Loading
    repository.getAllProjects { result ->
      result.onSuccess { projects ->
        _uiState.value = ProjectListUiState.Success(projects)
      }.onFailure { e ->
        _uiState.value = ProjectListUiState.Error(
          "Failed to load projects: ${e.message ?: "Unknown error"}"
        )
      }
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
        _uiState.value = ProjectListUiState.Error(
          "Failed to create project: ${e.message ?: "Unknown error"}"
        )
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
        _uiState.value = ProjectListUiState.Error(
          "Failed to delete project: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }
}