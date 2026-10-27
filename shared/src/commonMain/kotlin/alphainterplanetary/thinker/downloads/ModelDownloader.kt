package alphainterplanetary.thinker.downloads

import kotlinx.coroutines.flow.StateFlow

/**
 * Specification for downloading a local model file.
 */
data class ModelDownloadSpec(
  val modelId: String,
  val url: String,
  val destination: String? = null,
  val expectedSha256: String? = null,
  val extract: Boolean = false,
)

/**
 * Manages downloads (Ollama pulls, local file downloads).
 */
interface ModelDownloader {
  /** All current downloads. */
  fun downloads(): StateFlow<List<DownloadState>>

  /** Pull a model from an Ollama instance. */
  fun pullOllama(modelId: String, baseUrl: String): DownloadHandle

  /** Download a local model file. */
  fun downloadModel(spec: ModelDownloadSpec): DownloadHandle

  /** Cancel a download by handle id. */
  fun cancel(handleId: String)
}
