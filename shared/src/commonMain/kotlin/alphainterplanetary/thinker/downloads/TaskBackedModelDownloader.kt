package alphainterplanetary.thinker.downloads

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.tasks.ConcurrencyGroup
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.util.randomUUID
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A Download manager that uses the task infrastructure to track tasks. */
class TaskBackedModelDownloader(
  private val taskRunner: TaskRunner,
  private val ollamaPullClient: OllamaPullClient,
  private val activityLogger: ActivityLogger,
  scope: CoroutineScope,
  private val nanoClock: () -> Long = defaultNanoClock(),
) : ModelDownloader {
  private val _downloads = MutableStateFlow<List<DownloadState>>(emptyList())
  override fun downloads(): StateFlow<List<DownloadState>> = _downloads.asStateFlow()

  init {
    scope.launch {
      taskRunner.tasks.collect { tasks ->
        _downloads.update { list ->
          list.map { state ->
            val task = tasks.firstOrNull { it.id == state.handle.taskId } ?: return@map state
            // A cancellation is the downloader's own terminal state: the task
            // runner reports a cancelled body as failed, which would relabel
            // the row the user just stopped.
            if (state.status == DownloadStatus.Cancelled) return@map state
            val mappedStatus = when (task.status) {
              TaskStatus.Queued -> DownloadStatus.Queued
              TaskStatus.Running -> DownloadStatus.Running
              TaskStatus.Succeeded -> DownloadStatus.Succeeded
              TaskStatus.Failed -> DownloadStatus.Failed
            }
            state.copy(
              status = mappedStatus,
              modelId = task.modelId ?: state.modelId,
              source = task.source ?: state.source,
              bytesDone = task.bytesDone ?: state.bytesDone,
              bytesTotal = task.bytesTotal ?: state.bytesTotal,
              throughputBps = task.throughputBps ?: state.throughputBps,
              progress = task.progress ?: state.progress,
              message = task.error ?: state.message,
            )
          }
        }
      }
    }
  }

  override fun pullOllama(modelId: String, baseUrl: String): DownloadHandle {
    val handleId = randomUUID()
    val task = taskRunner.enqueue(
      projectId = "",
      kind = TaskKind.OllamaModelPull,
      group = ConcurrencyGroup.Download,
    ) { taskId ->
      // Milestone notes join the task's activity under a download/Ollama category so the
      // Activity Log can label the work meaningfully; TaskRunner owns the lifecycle rows.
      val logContext = activityLogger.context(
        activityId = taskId,
        category = LogCategory.ModelDownload,
        source = LogSource.Ollama,
        projectId = "",
      )
      // A pull reports byte counts per layer, not for the model as a whole.
      // Summing the layers lets the chip's ring track the whole pull instead
      // of restarting from zero with every layer (which would make it look
      // stuck at 100% on the small ones).
      val layers = mutableMapOf<String, Pair<Long, Long>>()
      val reportedMilestones = mutableSetOf<Int>()
      val throughput = EmaThroughput()
      ollamaPullClient.pull(baseUrl, modelId).collect { event ->
        event.error?.let { throw Exception(it) }
        val total = event.total
        if (total != null && total > 0L) {
          layers[event.digest ?: ""] = total to (event.completed ?: 0L).coerceIn(0L, total)
          val bytesDone = layers.values.sumOf { it.second }
          val bytesTotal = layers.values.sumOf { it.first }
          taskRunner.setDownloadProgress(
            taskId = taskId,
            bytesDone = bytesDone,
            bytesTotal = bytesTotal,
            throughputBps = throughput.sample(bytesDone, nanoClock()),
          )
          if (bytesTotal > 0L) {
            val percent = ((bytesDone.toDouble() / bytesTotal.toDouble()) * 100).toInt()
            // Log the first time overall progress passes each threshold (a jump can
            // cross more than one). State is per pull; if that ever becomes awkward
            // to maintain, drop progress log entries rather than add complexity.
            for (milestone in PROGRESS_MILESTONES) {
              if (percent >= milestone && reportedMilestones.add(milestone)) {
                logContext.append("progress: $milestone%")
              }
            }
          }
        }
      }
    }
    taskRunner.setDownloadMetadata(
      taskId = task.id,
      modelId = modelId,
      source = "ollama",
      downloadId = handleId,
    )
    val handle = DownloadHandle(id = handleId, taskId = task.id)
    _downloads.update {
      it + DownloadState(
        handle = handle,
        status = DownloadStatus.Running,
        modelId = modelId,
        source = "ollama",
      )
    }
    return handle
  }

  override fun cancel(handleId: String) {
    val state = _downloads.value.firstOrNull { it.handle.id == handleId } ?: return
    // A finished transfer (succeeded or failed) has nothing left to stop, and
    // relabelling it cancelled would discard the outcome the user needs to see.
    if (state.status.finished) return
    // Cancelling the task closes the transfer's stream on the next loop check;
    // marking the row here is what the UI reacts to immediately.
    taskRunner.cancel(state.handle.taskId)
    _downloads.update { list ->
      list.map { current ->
        if (current.handle.id == handleId) {
          current.copy(status = DownloadStatus.Cancelled)
        } else {
          current
        }
      }
    }
  }
}

private fun defaultNanoClock(): () -> Long {
  val mark = TimeSource.Monotonic.markNow()
  return { mark.elapsedNow().inWholeNanoseconds }
}

private val PROGRESS_MILESTONES = listOf(25, 50, 75)
