package alphainterplanetary.thinker.downloads

/**
 * A handle returned when starting a download. The [taskId] links to
 * [alphainterplanetary.thinker.tasks.GenerationTask].
 */
data class DownloadHandle(
  val id: String,
  val taskId: String,
)
