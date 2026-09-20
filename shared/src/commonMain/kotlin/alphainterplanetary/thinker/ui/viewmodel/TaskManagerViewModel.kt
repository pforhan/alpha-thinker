package alphainterplanetary.thinker.ui.viewmodel

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

/** A session task with the owning project's title resolved for display. */
data class TaskManagerRow(
  val task: GenerationTask,
  val projectTitle: String,
)

/**
 * Read-only view over the app-scoped [TaskRunner]: every task enqueued this
 * process is listed, most-recent-first, with the owning project's title. The
 * Task Manager screen observes [rows]; control actions (cancel/retry) and
 * task persistence are roadmap items.
 */
class TaskManagerViewModel(
  private val repository: ProjectRepository,
  private val taskRunner: TaskRunner,
  scope: CoroutineScope,
) {
  private val vmJob = SupervisorJob(scope.coroutineContext[Job])
  private val vmScope = CoroutineScope(scope.coroutineContext + vmJob)

  private val _rows = MutableStateFlow<List<TaskManagerRow>>(emptyList())
  val rows: StateFlow<List<TaskManagerRow>> = _rows.asStateFlow()

  /** Resolved titles are stable for a session; project renames show here next launch. */
  private val projectTitles = mutableMapOf<String, String>()

  init {
    vmScope.launch {
      taskRunner.tasks.collect { current ->
        _rows.value = current.map { task -> TaskManagerRow(task, titleFor(task.projectId)) }
      }
    }
  }

  private suspend fun titleFor(projectId: String): String {
    projectTitles[projectId]?.let { return it }
    val title = repository.getProject(projectId)?.editableTitle ?: projectId
    projectTitles[projectId] = title
    return title
  }

  fun close() {
    vmJob.cancel()
  }
}