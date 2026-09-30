package alphainterplanetary.thinker.tasks

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
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
 * Bodies are scheduled by **resource group** ([ConcurrencyGroup], defaulting to the
 * task's [TaskKind.group]): engine work serializes (limit 1, FIFO among tasks
 * already at the gate), while remote groups run with bounded parallelism.
 * Grouping is a scheduling policy, not a fixed property of a run.
 *
 * Ordering between tasks is a property of *enqueue order*, not of grouping: a
 * title recommendation and the initial batch both land in the serial engine
 * group, so the recommendation is enqueued first and normally lands first. That
 * is a convention, not a dependency the runner enforces — the recommendation and
 * the batch that reads the title are launched as independent coroutines, so on a
 * multi-threaded dispatcher either can reach the gate first. A body that must
 * observe another task's output has to say so (wait on it, or fold the work into
 * its own body) rather than rely on enqueue order.
 *
 * On top of the group limit, tasks for the **same project never run
 * concurrently**: task bodies re-read and re-persist the whole `Project`
 * aggregate, so two writers for one project would clobber each other's write.
 * Parallelism is safe across projects; within one project every task rewrites
 * the whole `Project` aggregate, so they stay serialized.
 *
 * When an [ActivityLogger] is injected, each lifecycle transition is appended as
 * an immutable [LogEntry] via a [LogContext] scoped to the task's [activityId]
 * (`TaskRun` category, `TaskRunner` source, the task's project): a "started:"
 * row when the body begins and a terminal
 * `succeeded`/`failed: <msg>`/`cancelled` row when it resolves. Appends happen
 * synchronously inside the task coroutine, so a task's log rows are always in
 * transition order and no extra coroutines are kept alive. The body receives
 * its [taskId] so it can pass it to engine calls as `activityId`, joining the
 * `LoggingPlanningEngine` detail rows to the same activity.
 */
class TaskRunner(
  private val scope: CoroutineScope,
  private val activityLogger: ActivityLogger? = null,
) {
  private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())

  /** Per-resource concurrency gate; see [ConcurrencyGroup]. */
  private val groupGates: Map<ConcurrencyGroup, Gate> = ConcurrencyGroup.entries.associateWith { Gate(it.concurrency) }

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
   * injected scope. [body] receives the task's id so it can thread it into
   * engine calls as `activityId`. [group] selects the concurrency pool,
   * defaulting to the kind's [TaskKind.group]; pass an explicit group when the
   * body competes for a different resource (e.g. a remote HTTP lookup).
   */
  fun enqueue(
    projectId: String,
    kind: TaskKind,
    group: ConcurrencyGroup = kind.group,
    body: suspend (taskId: String) -> Unit,
  ): GenerationTask {
    val task = newTask(projectId, kind, group)
    return launchTask(task) { body(it) }
  }

  private fun newTask(projectId: String, kind: TaskKind, group: ConcurrencyGroup): GenerationTask =
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
    produce: suspend (taskId: String) -> Unit,
  ): GenerationTask {
    _tasks.update { it + task }
    val logContext = activityLogger?.context(
      activityId = task.id,
      category = LogCategory.TaskRun,
      source = LogSource.TaskRunner,
      projectId = task.projectId,
    )
    scope.launch {
      logContext?.started(task.kind.name)
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
          try {
            produce(task.id)
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
            else -> { t -> t.asSucceeded(finishedAt) }
          }
          // The body may have streamed progress via [setProgress]; the durable
          // log keeps just the terminal row (transient ticks are UI-only).
          when {
            cancelled -> logContext?.closeCancelled()
            failure != null -> logContext?.closeFailed(failure)
            else -> logContext?.closeSucceeded()
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