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
import kotlinx.coroutines.sync.Semaphore
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
 * Bodies are scheduled by **resource group** ([TaskGroup], defaulting to the
 * task's [TaskKind.group]): engine work is a single shared resource and
 * serializes (limit 1, FIFO in enqueue order), while remote groups run with
 * bounded parallelism. Grouping is a scheduling policy, not a fixed property
 * of a run — a title recommendation and the initial batch both run in the
 * serial engine group, so a recommendation another task's output depends on
 * (the title before the batch reads the project) is guaranteed to land first.
 *
 * On top of the group limit, tasks for the **same project never run
 * concurrently**: task bodies re-read and re-persist the whole `Project`
 * aggregate, so two writers for one project would clobber each other's write.
 * Parallelism is safe across projects and for read-only checks like
 * [TaskKind.RemainingInPhase].
 */
class TaskRunner(
  private val scope: CoroutineScope,
) {
  private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())

  /** Per-resource concurrency gate; see [TaskGroup]. */
  private val groupGates: Map<TaskGroup, Gate> = TaskGroup.entries.associateWith { Gate(it.concurrency) }

  /** Guards the [projectGuards] map so lookups are safe on any dispatcher. */
  private val projectGuardsLock = Mutex()

  /** Serializes bodies that touch the same project (they rewrite the whole aggregate). */
  private val projectGuards = mutableMapOf<String, Mutex>()

  /** Live tasks in insertion order; [GenerationTask.status] is the read model. */
  val tasks: StateFlow<List<GenerationTask>> = _tasks.asStateFlow()

  /** Live tasks for one project, in insertion order. */
  fun tasksFor(projectId: String): Flow<List<GenerationTask>> =
    _tasks.map { list -> list.filter { it.projectId == projectId } }

  /**
   * Enqueues [body] as a [kind] generation task for [projectId], returning the
   * task immediately (still [TaskStatus.Queued]); the body runs later on the
   * injected scope. [group] selects the concurrency pool, defaulting to the
   * kind's [TaskKind.group]; pass an explicit group when the body competes for
   * a different resource (e.g. a remote HTTP lookup).
   */
  fun enqueue(
    projectId: String,
    kind: TaskKind,
    group: TaskGroup = kind.group,
    body: suspend () -> Unit,
  ): GenerationTask {
    val task = newTask(projectId, kind, group)
    return launchTask(task) {
      body()
      null
    }
  }

  /**
   * Like [enqueue], but the body answers a question (e.g. "can the engine
   * still produce questions?") and its result is folded into the terminal
   * task's [GenerationTask.result].
   */
  fun enqueueResult(
    projectId: String,
    kind: TaskKind,
    group: TaskGroup = kind.group,
    body: suspend () -> Boolean,
  ): GenerationTask {
    val task = newTask(projectId, kind, group)
    return launchTask(task) { body() }
  }

  private fun newTask(
    projectId: String,
    kind: TaskKind,
    group: TaskGroup,
  ): GenerationTask =
    GenerationTask(
      id = randomUUID(),
      projectId = projectId,
      kind = kind,
      group = group,
      status = TaskStatus.Queued,
      createdAt = now(),
    )

  private suspend fun projectGuard(projectId: String): Mutex {
    projectGuardsLock.withLock {
      return projectGuards.getOrPut(projectId) { Mutex() }
    }
  }

  private fun launchTask(
    task: GenerationTask,
    produce: suspend () -> Boolean?,
  ): GenerationTask {
    _tasks.update { it + task }
    scope.launch {
      val guard = projectGuard(task.projectId)
      val groupGate = groupGates.getValue(task.group)
      guard.withLock {
        groupGate.withLock {
          val startedAt = now()
          _tasks.update { it.replace(task.asStarted(startedAt)) }
          println(
            "[AlphaThinker] task started: kind=${task.kind}, group=${task.group}, " +
              "project=${task.projectId}, id=${task.id}"
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
            "[AlphaThinker] task finished: kind=${task.kind}, group=${task.group}, " +
              "project=${task.projectId}, id=${task.id}, outcome=$outcome, " +
              "duration=${finishedAt - startedAt}"
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

/**
 * One concurrency gate: a fair FIFO [Mutex] when the limit is one (so serial
 * groups preserve enqueue order), a counting [Semaphore] otherwise.
 */
private class Gate(concurrency: Int) {
  private val mutex: Mutex? = if (concurrency == 1) Mutex() else null
  private val semaphore: Semaphore? = if (concurrency > 1) Semaphore(concurrency) else null

  suspend fun <T> withLock(block: suspend () -> T): T {
    val runningMutex = mutex
    if (runningMutex != null) {
      runningMutex.lock()
      try {
        return block()
      } finally {
        runningMutex.unlock()
      }
    }
    val permit = requireNotNull(semaphore) { "concurrency must be at least 1" }
    permit.acquire()
    try {
      return block()
    } finally {
      permit.release()
    }
  }
}

private fun List<GenerationTask>.replace(task: GenerationTask): List<GenerationTask> =
  map { if (it.id == task.id) task else it }