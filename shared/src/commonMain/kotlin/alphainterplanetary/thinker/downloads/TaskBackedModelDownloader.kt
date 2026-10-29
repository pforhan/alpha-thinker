package alphainterplanetary.thinker.downloads

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.tasks.ConcurrencyGroup
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.util.randomUUID
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
  private val remoteModelCatalog: RemoteModelCatalog,
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
            val mappedStatus = when (task.status) {
              TaskStatus.Queued -> DownloadStatus.Queued
              TaskStatus.Running -> DownloadStatus.Running
              TaskStatus.Succeeded -> DownloadStatus.Succeeded
              TaskStatus.Failed -> DownloadStatus.Failed
            }
            state.copy(
              status = mappedStatus,
              bytesDone = task.bytesDone ?: state.bytesDone,
              bytesTotal = task.bytesTotal ?: state.bytesTotal,
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
        ollamaPullClient.pull(baseUrl, modelId).collect { event ->
          if (event.error != null) {
            throw Exception(event.error)
          }
          val total = event.total
          val done = event.completed ?: 0L
          if (total != null && total > 0L) {
            taskRunner.setDownloadProgress(
              taskId = taskId,
              bytesDone = done,
              bytesTotal = total,
              throughputBps = null,
            )
          }
          if (event.status != null) {
            logContext.append("status: ${event.status}")
          }
        }
        // Refresh catalog after successful pull
        runCatching {
          remoteModelCatalog.list(
            alphainterplanetary.thinker.engine.RemoteProvider.Ollama,
            baseUrl,
            "",
          )
        }
        logContext.closeSucceeded()
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
          taskRunner.setDownloadProgress(
            taskId = taskId,
            bytesDone = event.bytesDone,
            bytesTotal = event.bytesTotal,
            throughputBps = event.throughputBps,
          )
          logContext.append("phase: ${event.phase.name}")
        }
        logContext.closeSucceeded()
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
    // Cancellation is handled via task cancellation in the runner; for now
    // mark state as cancelled.
    _downloads.update { list ->
      list.map { state ->
        if (state.handle.id == handleId) {
          state.copy(status = DownloadStatus.Cancelled)
        } else {
          state
        }
      }
    }
  }
}
