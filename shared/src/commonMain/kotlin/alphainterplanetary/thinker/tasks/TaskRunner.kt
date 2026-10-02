package alphainterplanetary.thinker.tasks

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

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
            var cancelled = false
            var failure: String? = null
            try {
              // Installed for the body only — and only once both locks are held,
              // which is exactly the window [await] refuses to suspend in.
              withContext(TaskBodyContext(projectId = task.projectId, group = task.group)) {
                produce(task.id)
              }
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

  /**
   * Suspends until [taskId] finishes, returning the finished task — or throwing
   * [TaskFailed] carrying its failure. The counterpart to [enqueue]'s
   * fire-and-forget return: [enqueue] hands the body to the runner's scope, so
   * nothing a task produces exists yet when it returns, and a caller that needs
   * one generation to land before it starts the next had no way to say so.
   *
   * Failures are raised rather than returned so a caller cannot accidentally
   * read a failed task's output as a result — a task that failed has none, and
   * the storage it left behind is the failure's, not a partial success.
   *
   * Cancellation of the *awaiting* coroutine is not a task failure: it throws
   * [kotlinx.coroutines.CancellationException] out of the suspension without
   * touching the task, which keeps running. A task cancelled in its own right
   * lands [TaskStatus.Failed] like any other failure, so it surfaces as
   * [TaskFailed] carrying the runner's "Task cancelled" message.
   *
   * **Not callable from inside a task body for the same project.** A body runs
   * while holding that project's mutex (see [launchTask]), and every task for
   * the project needs it, so awaiting there would wait on a task that can never
   * reach its gate. Sequence from outside the runner, as the UI does.
   */
  @Throws(TaskFailed::class, CancellationException::class)
  suspend fun await(taskId: String): GenerationTask {
    // Checked against the current value rather than by suspending on the flow,
    // so an unknown id fails here instead of hanging on one that will never
    // carry it.
    val task = tasks.value.firstOrNull { it.id == taskId }
      ?: throw IllegalArgumentException("No task with id $taskId")
    checkAwaitIsReachable(task)
    val finished = tasks.first { list -> list.any { it.id == taskId && it.isFinished } }
      .single { it.id == taskId }
    if (finished.status == TaskStatus.Failed) {
      throw TaskFailed(finished)
    }
    return finished
  }

  /**
   * Fails fast when this coroutine is itself running as a task body that would
   * have to release its own lock before [task] could start — the deadlock the
   * KDoc on [await] describes, caught at the call instead of as a hang.
   *
   * The check is context-based rather than a flag on the runner, so it holds
   * across suspensions and dispatcher hops: [TaskBodyContext] is installed
   * around the body (see [launchTask]) and travels with the coroutine
   * everywhere it goes.
   *
   * Only an *unfinished* target deadlocks, and the two locks fail differently:
   * the project mutex serializes every task for a project regardless of group,
   * while a group's gate serializes only tasks in that group. So awaiting a
   * finished task is always safe, awaiting a task in a different group is
   * always safe, and awaiting a live task is safe exactly when neither lock
   * would be contended.
   */
  private suspend fun checkAwaitIsReachable(task: GenerationTask) {
    if (task.isFinished) return
    val body = coroutineContext[TaskBodyContext] ?: return
    val sameProject = task.projectId == body.projectId
    val sameGate = task.group == body.group
    if (!sameProject && !sameGate) return
    val held = if (sameProject) {
      "the project mutex for ${task.projectId}"
    } else {
      "the ${task.group} gate, which admits one task at a time"
    }
    throw IllegalStateException(
      "await(${task.id}) would deadlock: this coroutine is a ${body.group} task body " +
        "for project ${body.projectId} and is holding $held, which the awaited task " +
        "needs before it can start. Await from outside a task body.",
    )
  }

  /** Reports streaming progress (0..1) for a running task, e.g. a synthesis. */
  fun setProgress(taskId: String, progress: Float) {
    _tasks.update { list ->
      list.map { task -> if (task.id == taskId) task.copy(progress = progress) else task }
    }
  }
}

/**
 * Marks a coroutine as running as a [TaskRunner] task body, carrying the two
 * locks that body holds for its whole duration: the project's mutex and its
 * [group]'s gate. Installed by the runner around the body and read by
 * [TaskRunner.await] to refuse a self-deadlocking wait.
 *
 * A context element rather than runner state because the question "is this
 * coroutine a task body?" is asked from inside a deeply nested suspend call
 * that may have hopped dispatchers, where no shared field would still be
 * reachable — but the coroutine context is, by construction.
 */
private class TaskBodyContext(
  val projectId: String,
  val group: ConcurrencyGroup,
) : AbstractCoroutineContextElement(TaskBodyContext) {
  companion object Key : CoroutineContext.Key<TaskBodyContext>
}

/**
 * A task reached [TaskStatus.Failed] while [TaskRunner.await] was waiting on it.
 * Carries the whole task so a caller can report its [GenerationTask.error] or
 * inspect [GenerationTask.kind] without going back to the runner.
 */
class TaskFailed(val task: GenerationTask) : Exception(
  task.error ?: "Task ${task.kind.name} failed",
)

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