package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.downloads.DownloadHandle
import alphainterplanetary.thinker.downloads.ModelDownloadSpec
import alphainterplanetary.thinker.downloads.ModelDownloader
import alphainterplanetary.thinker.repository.EngineRepository

/**
 * Connects the curated [LocalModelCatalog] to storage: a model is a file under
 * [PlatformContext.modelDirectory], and an installed model is an
 * [EngineInstance] of kind [EngineKind.LocalFile] that names that file.
 *
 * The store deliberately does not select a model after a download. Downloading
 * and using are separate decisions — a multi-gigabyte transfer may happen over
 * Wi-Fi while the user is doing something else — so the picker's "Use" action is
 * what moves the selection ([use]).
 */
class LocalModelStore(
  private val repository: EngineRepository,
  private val downloader: ModelDownloader,
  private val platformContext: PlatformContext,
  private val downloadsSupported: Boolean = LocalModelRuntime.downloadsSupported,
) {
  val catalog: List<LocalModel>
    get() = LocalModelCatalog.models

  /** The stored instance for [model], whether or not its file is still present. */
  fun instance(model: LocalModel): EngineInstance? =
    repository.instances.value.firstOrNull { instance ->
      instance.kind == EngineKind.LocalFile &&
        (instance.id == instanceId(model) || instance.model == model.fileName)
    }

  /** Whether [model] has a stored instance whose file is on disk. */
  fun isInstalled(model: LocalModel): Boolean {
    val path = instance(model)?.localPath ?: return false
    return platformContext.fileExists(path)
  }

  /** The absolute path a download of [model] would write to. */
  fun localPath(model: LocalModel): String? =
    platformContext.modelDirectory()?.let { directory ->
      "${directory.trimEnd('/')}/${model.fileName}"
    }

  /**
   * Starts [model]'s download when this platform supports one and the model is
   * not already installed. Returns null when there is nothing to start, so a
   * repeated tap on the same row is harmless.
   */
  fun download(model: LocalModel): DownloadHandle? {
    if (!downloadsSupported) return null
    if (isInstalled(model)) return null
    val path = localPath(model) ?: return null
    return downloader.downloadModel(
      ModelDownloadSpec(
        modelId = model.id,
        url = model.downloadUrl,
        destination = path,
        expectedSha256 = model.sha256,
        expectedBytes = model.expectedBytes,
      ),
    )
  }

  /**
   * Records that [model]'s verified file is installed. Called when the download
   * task succeeds; a missing file is a no-op so a failed rename cannot
   * manufacture an instance whose availability check would immediately fail.
   */
  fun install(model: LocalModel) {
    val path = localPath(model) ?: return
    if (!platformContext.fileExists(path)) return
    repository.addOrUpdate(
      EngineInstance(
        id = instanceId(model),
        kind = EngineKind.LocalFile,
        provider = null,
        label = model.label,
        model = model.fileName,
        contextTokens = model.contextTokens,
        localPath = path,
      ),
    )
  }

  /** Selects [model] as the running engine when it is installed. */
  fun use(model: LocalModel) {
    val instance = instance(model) ?: return
    if (!isInstalled(model)) return
    repository.select(EngineRef.Instance(instance.id))
  }

  /** Removes the stored instance and deletes the downloaded file. */
  fun delete(model: LocalModel) {
    instance(model)?.let { repository.delete(it.id) }
    localPath(model)?.let(platformContext::deleteFile)
  }

  companion object {
    /** Deterministic so a re-download updates the same row instead of adding one. */
    fun instanceId(model: LocalModel): String = "local-${model.id}"
  }
}
