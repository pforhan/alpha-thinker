package alphainterplanetary.thinker.downloads

/**
 * Observable state for a download.
 */
data class DownloadState(
  val handle: DownloadHandle,
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
