package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tasks.GenerationTask
import alphainterplanetary.thinker.tasks.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
  private val taskRunner: TaskRunner,
  private val scope: CoroutineScope,
) {
  private val vmJob = SupervisorJob(scope.coroutineContext[Job])
  private val vmScope = CoroutineScope(scope.coroutineContext + vmJob)

  private val _uiState = MutableStateFlow<ProjectListUiState>(ProjectListUiState.Loading)
  val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

  /** Live non-terminal tasks, so the list can show "generating…" chips and a Task Manager FAB. */
  private val _activeTasks = MutableStateFlow<List<GenerationTask>>(emptyList())
  val activeTasks: StateFlow<List<GenerationTask>> = _activeTasks.asStateFlow()

  init {
    vmScope.launch {
      taskRunner.tasks.collect { current ->
        _activeTasks.value = current.filter { it.isActive }
      }
    }
  }

  fun loadProjects() {
    _uiState.value = ProjectListUiState.Loading
    vmScope.launch {
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
    vmScope.launch {
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
    vmScope.launch {
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

  fun close() {
    vmJob.cancel()
  }
}