package alphainterplanetary.thinker.tasks

import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * App-scoped executor for long-running generation work. [enqueue] wraps a
 * suspend [body] and transitions it through `Queued -> Running ->
 * Succeeded | Failed`, publishing every change on the observable [tasks] flow
 * so the UI can reload the affected project and surface progress. One instance
 * owns the injected, app-lifetime [scope]; bodies are cancellable like any
 * launched coroutine.
 *
 * Bodies run strictly **serially** in enqueue order: generation is a single
 * shared resource (an LLM), so a recommendation that another task's output
 * depends on (e.g. the title before the initial question batch) is guaranteed
 * to land before the dependent task reads the project. A task behind a slow
 * peer stays [TaskStatus.Queued] until the peer finishes.
 */
class TaskRunner(
  private val scope: CoroutineScope,
) {
  private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())

  /** Serializes [body] execution so tasks run in enqueue order. */
  private val bodyLock = Mutex()

  /** Live tasks in insertion order; [GenerationTask.status] is the read model. */
  val tasks: StateFlow<List<GenerationTask>> = _tasks.asStateFlow()

  /** Live tasks for one project, in insertion order. */
  fun tasksFor(projectId: String): Flow<List<GenerationTask>> =
    _tasks.map { list -> list.filter { it.projectId == projectId } }

  /**
   * Enqueues [body] as a [kind] generation task for [projectId], returning the
   * task immediately (still [TaskStatus.Queued]); the body runs later on the
   * injected scope.
   */
  fun enqueue(
    projectId: String,
    kind: TaskKind,
    body: suspend () -> Unit,
  ): GenerationTask {
    val task = newTask(projectId, kind)
    return launchTask(task) {
      body()
      null
    }
  }

  /**
   * Like [enqueue], but the body answers a question (e.g. "can the generator
   * still produce questions?") and its result is folded into the terminal
   * task's [GenerationTask.result].
   */
  fun enqueueResult(
    projectId: String,
    kind: TaskKind,
    body: suspend () -> Boolean,
  ): GenerationTask {
    val task = newTask(projectId, kind)
    return launchTask(task) { body() }
  }

  private fun newTask(projectId: String, kind: TaskKind): GenerationTask =
    GenerationTask(
      id = randomUUID(),
      projectId = projectId,
      kind = kind,
      status = TaskStatus.Queued,
      createdAt = now(),
    )

  private fun launchTask(
    task: GenerationTask,
    produce: suspend () -> Boolean?,
  ): GenerationTask {
    _tasks.update { it + task }
    scope.launch {
      bodyLock.withLock {
        val startedAt = now()
        _tasks.update { it.replace(task.asStarted(startedAt)) }
        println(
          "[AlphaThinker] task started: kind=${task.kind}, project=${task.projectId}, id=${task.id}"
        )
        var cancelled = false
        var failure: String? = null
        var result: Boolean? = null
        try {
          result = produce()
        } catch (e: CancellationException) {
          cancelled = true
        } catch (e: Exception) {
          failure = e.message ?: e.toString()
        }
        val finishedAt = now()
        val outcome = when {
          cancelled -> "cancelled"
          failure != null -> "failed: $failure"
          else -> "succeeded"
        }
        println(
          "[AlphaThinker] task finished: kind=${task.kind}, project=${task.projectId}, " +
            "id=${task.id}, outcome=$outcome, duration=${finishedAt - startedAt}"
        )
        val terminal: (GenerationTask) -> GenerationTask = when {
          cancelled -> { t -> t.asFailed(finishedAt, "Task cancelled") }
          failure != null -> { t -> t.asFailed(finishedAt, failure) }
          else -> { t -> t.asSucceeded(finishedAt).copy(result = result) }
        }
        // Fold any progress/error published via [setProgress] into the terminal
        // state instead of clobbering it with a stale local read.
        _tasks.update { list -> list.replace(terminal(list.first { it.id == task.id })) }
      }
    }
    return task
  }

  /** Reports streaming progress (0..1) for a running task, e.g. a synthesis. */
  fun setProgress(taskId: String, progress: Float) {
    _tasks.update { list ->
      list.map { task -> if (task.id == taskId) task.copy(progress = progress) else task }
    }
  }
}

private fun List<GenerationTask>.replace(task: GenerationTask): List<GenerationTask> =
  map { if (it.id == task.id) task else it }