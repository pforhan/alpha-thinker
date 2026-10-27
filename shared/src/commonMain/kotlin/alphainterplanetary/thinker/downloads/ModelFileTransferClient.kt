package alphainterplanetary.thinker.downloads

import kotlinx.coroutines.flow.Flow

/**
 * Downloader for local model files with resume/checksum/extraction support.
 */
interface ModelFileTransferClient {
  fun download(spec: ModelDownloadSpec): Flow<LocalDownloadEvent>
}

data class LocalDownloadEvent(
  val phase: LocalDownloadPhase = LocalDownloadPhase.Downloading,
  val bytesDone: Long = 0L,
  val bytesTotal: Long? = null,
  val throughputBps: Long? = null,
  val message: String? = null,
  val error: String? = null,
)

enum class LocalDownloadPhase {
  Downloading,
  Verifying,
  Extracting,
  Succeeded,
  Failed,
}
