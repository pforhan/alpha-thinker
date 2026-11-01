package alphainterplanetary.thinker.downloads

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.tasks.ConcurrencyGroup
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.util.randomUUID
import kotlin.coroutines.cancellation.CancellationException
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
  private val modelFileTransferClient: ModelFileTransferClient,
  private val activityLogger: ActivityLogger,
  scope: CoroutineScope,
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
      val logContext = activityLogger.taskContext(activityId = taskId, projectId = "")
      logContext.started("Pulling $modelId")
      try {
        // A pull reports byte counts per layer, not for the model as a whole.
        // Summing the layers lets the chip's ring track the whole pull instead
        // of restarting from zero with every layer (which would make it look
        // stuck at 100% on the small ones).
        val layers = mutableMapOf<String, Pair<Long, Long>>()
        ollamaPullClient.pull(baseUrl, modelId).collect { event ->
          if (event.error != null) {
            throw Exception(event.error)
          }
          val total = event.total
          if (total != null && total > 0L) {
            layers[event.digest ?: ""] = total to (event.completed ?: 0L).coerceIn(0L, total)
            taskRunner.setDownloadProgress(
              taskId = taskId,
              bytesDone = layers.values.sumOf { it.second },
              bytesTotal = layers.values.sumOf { it.first },
              throughputBps = null,
            )
          }
          if (event.status != null) {
            logContext.append("status: ${event.status}")
          }
        }
        logContext.closeSucceeded()
      } catch (e: CancellationException) {
        logContext.closeCancelled()
        throw e
      } catch (e: Exception) {
        logContext.closeFailed(e.message ?: e.toString())
        throw e
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
        kind = DownloadKind.OllamaPull,
        status = DownloadStatus.Running,
        modelId = modelId,
        source = "ollama",
      )
    }
    return handle
  }

  override fun downloadModel(spec: ModelDownloadSpec): DownloadHandle {
    val handleId = randomUUID()
    val task = taskRunner.enqueue(
      projectId = "",
      kind = TaskKind.ModelFileDownload,
      group = ConcurrencyGroup.Download,
    ) { taskId ->
      val logContext = activityLogger.taskContext(activityId = taskId, projectId = "")
      logContext.started("Downloading ${spec.modelId}")
      try {
        modelFileTransferClient.download(spec).collect { event ->
          if (event.error != null) {
            throw Exception(event.error)
          }
          // Phase-only events (verifying, extracting, a bare "succeeded") carry
          // no byte counts; reporting them would wipe the totals already shown.
          if (event.bytesTotal != null || event.bytesDone > 0L) {
            taskRunner.setDownloadProgress(
              taskId = taskId,
              bytesDone = event.bytesDone,
              bytesTotal = event.bytesTotal,
              throughputBps = event.throughputBps,
            )
          }
          logContext.append("phase: ${event.phase.name}")
        }
        logContext.closeSucceeded()
      } catch (e: CancellationException) {
        logContext.closeCancelled()
        throw e
      } catch (e: Exception) {
        logContext.closeFailed(e.message ?: e.toString())
        throw e
      }
    }
    taskRunner.setDownloadMetadata(
      taskId = task.id,
      modelId = spec.modelId,
      source = "litert",
      downloadId = handleId,
    )
    val handle = DownloadHandle(id = handleId, taskId = task.id)
    _downloads.update {
      it + DownloadState(
        handle = handle,
        kind = DownloadKind.LocalFile,
        status = DownloadStatus.Running,
        modelId = spec.modelId,
        source = "litert",
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
