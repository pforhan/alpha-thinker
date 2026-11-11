package alphainterplanetary.thinker.downloads

/**
 * State of a download.
 */
enum class DownloadProgress(val finished: Boolean = false) {
  Queued,
  Running,
  Paused,
  Succeeded(true),
  Failed(true),
  Cancelled(true),
}
