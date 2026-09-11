package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProjectListUiState {
  data object Loading : ProjectListUiState
  data class Success(val projects: List<Project>) : ProjectListUiState
  data class Error(val message: String) : ProjectListUiState
}

class ProjectListViewModel(
  private val repository: ProjectRepository,
  private val scope: CoroutineScope,
) {
  private val _uiState = MutableStateFlow<ProjectListUiState>(ProjectListUiState.Loading)
  val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

  fun loadProjects() {
    _uiState.value = ProjectListUiState.Loading
    scope.launch {
      try {
        val projects = repository.getAllProjects()
        _uiState.value = ProjectListUiState.Success(projects)
      } catch (e: Exception) {
        _uiState.value = ProjectListUiState.Error(
          "Failed to load projects: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  private val _createdProject = MutableStateFlow<Project?>(null)
  val createdProject: StateFlow<Project?> = _createdProject.asStateFlow()

  fun createProject(synopsis: String, title: String?) {
    scope.launch {
      try {
        val project = repository.createProject(synopsis, title)
        _createdProject.value = project
        loadProjects()
      } catch (e: Exception) {
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
    scope.launch {
      try {
        repository.deleteProject(id)
        loadProjects()
      } catch (e: Exception) {
        _uiState.value = ProjectListUiState.Error(
          "Failed to delete project: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }
}