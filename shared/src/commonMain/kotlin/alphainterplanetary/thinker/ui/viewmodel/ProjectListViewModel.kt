package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.repository.ProjectWrites
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
  private val projectWrites: ProjectWrites,
  private val scope: CoroutineScope,
) {
  private val vmJob = SupervisorJob(scope.coroutineContext[Job])
  private val vmScope = CoroutineScope(scope.coroutineContext + vmJob)

  private val _uiState = MutableStateFlow<ProjectListUiState>(ProjectListUiState.Loading)
  val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

  /**
   * Every generation task the app has run, so the list can show "generating…"
   * chips for the live ones *and* a failure marker for a project whose
   * generation came up empty (the failure is what the user acts on, and the
   * list is where they are when it happens).
   */
  private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())
  val tasks: StateFlow<List<GenerationTask>> = _tasks.asStateFlow()

  init {
    vmScope.launch {
      taskRunner.tasks.collect { current ->
        _tasks.value = current
      }
    }
    // Sample projects are generated from the header flyout, which is composed
    // over every screen — so the run happens while this list is on screen and
    // without this list having asked for it. The write bus is what says so, and
    // the reload is the only way its projects reach the user. The bus is inert
    // on subscribe, so collecting it here cannot race the screen's own first
    // load.
    vmScope.launch {
      projectWrites.writes.collect {
        reloadProjects()
      }
    }
  }

  fun loadProjects() {
    _uiState.value = ProjectListUiState.Loading
    reloadProjects()
  }

  /**
   * Re-reads the projects, keeping whatever is already on screen until the read
   * lands. [loadProjects] is the one that goes back to [ProjectListUiState.Loading]
   * first, because a first read has nothing to show; a reload over a populated
   * list would only put a spinner between the user and projects they are
   // looking at.
   */
  private fun reloadProjects() {
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