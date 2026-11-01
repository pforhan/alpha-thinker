package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.downloads.DownloadHandle
import alphainterplanetary.thinker.downloads.DownloadState
import alphainterplanetary.thinker.downloads.ModelDownloadSpec
import alphainterplanetary.thinker.downloads.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [ModelDownloader] whose download list the test drives directly: [states] is
 * what the ViewModel observes, [specs] records every request, and [cancelled]
 * records the handle ids passed to [cancel].
 */
class FakeModelDownloader : ModelDownloader {
  private val _downloads = MutableStateFlow<List<DownloadState>>(emptyList())

  /** The observable list the code under test reads; set it to publish a phase. */
  val states: MutableStateFlow<List<DownloadState>> = _downloads

  /** Every spec handed to [downloadModel], in call order. */
  val specs: MutableList<ModelDownloadSpec> = mutableListOf()

  /** Handle ids passed to [cancel]. */
  val cancelled: MutableList<String> = mutableListOf()

  override fun downloads(): StateFlow<List<DownloadState>> = _downloads.asStateFlow()

  override fun pullOllama(modelId: String, baseUrl: String): DownloadHandle =
    error("Ollama pulls are not part of this fake")

  override fun downloadModel(spec: ModelDownloadSpec): DownloadHandle {
    specs += spec
    return DownloadHandle(id = "handle-${specs.size}", taskId = "task-${specs.size}")
  }

  override fun cancel(handleId: String) {
    cancelled += handleId
  }
}
