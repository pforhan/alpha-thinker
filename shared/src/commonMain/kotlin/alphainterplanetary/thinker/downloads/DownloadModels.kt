package alphainterplanetary.thinker.downloads

/**
 * Kinds of downloads supported by [ModelDownloader].
 */
enum class DownloadKind {
  OllamaPull,
  LocalFile,
}

/**
 * State of a download.
 */
enum class DownloadStatus(val finished: Boolean = false) {
  Queued,
  Running,
  Paused,
  Succeeded(true),
  Failed(true),
  Cancelled(true),
}

/**
 * A handle returned when starting a download. The [taskId] links to
 * [alphainterplanetary.thinker.tasks.GenerationTask].
 */
data class DownloadHandle(
  val id: String,
  val taskId: String,
)

/**
 * Observable state for a download.
 */
data class DownloadState(
  val handle: DownloadHandle,
  val kind: DownloadKind,
  val status: DownloadStatus,
  val modelId: String? = null,
  val source: String? = null,
  val bytesDone: Long = 0L,
  val bytesTotal: Long? = null,
  /** Recent transfer rate, when the source reports one. */
  val throughputBps: Long? = null,
  val progress: Float? = null,
  val message: String? = null,
) {
  /** Returns true if we have a total byte count and it and bytes so far is greater than 0. */
  fun hasProgress(): Boolean = (bytesTotal ?: 0L) > 0L && bytesDone > 0L
}
