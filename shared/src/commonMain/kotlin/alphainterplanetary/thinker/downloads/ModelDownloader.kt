package alphainterplanetary.thinker.downloads

import kotlinx.coroutines.flow.StateFlow

/**
 * Manages Ollama model pulls.
 */
interface ModelDownloader {
  /** All current downloads. */
  fun downloads(): StateFlow<List<DownloadState>>

  /** Pull a model from an Ollama instance. */
  fun pullOllama(modelId: String, baseUrl: String): DownloadHandle

  /** Cancel a download by handle id. */
  fun cancel(handleId: String)
}
