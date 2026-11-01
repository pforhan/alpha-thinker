package alphainterplanetary.thinker.downloads

import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.coroutines.flow.flowOf

/**
 * Builds the platform's direct-to-storage transfer client.
 *
 * Only Android has a real implementation (it can write into app storage and run
 * HTTP); the other targets return a client that reports the unsupported phase,
 * so the download task fails visibly instead of silently succeeding.
 */
expect object ModelFileTransferClientFactory {
  fun create(platformContext: PlatformContext): ModelFileTransferClient
}

/**
 * The client every non-Android target gets: a download is not possible, and
 * saying so as a [LocalDownloadPhase.Failed] event lets the shared task path
 * report it like any other transfer failure.
 */
object UnsupportedModelFileTransferClient : ModelFileTransferClient {
  override fun download(spec: ModelDownloadSpec) = flowOf(
    LocalDownloadEvent(
      phase = LocalDownloadPhase.Failed,
      error = "Model downloads are not supported on this platform",
    ),
  )
}
