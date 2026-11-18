package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.IosPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

actual fun provideExportFileStore(platformContext: PlatformContext): ExportFileStore =
  IosExportFileStore(platformContext as IosPlatformContext)

@OptIn(ExperimentalForeignApi::class)
private class IosExportFileStore(
  private val platformContext: IosPlatformContext,
) : ExportFileStore {
  private fun ensureDir(): String {
    val dir = platformContext.exportsDirectoryPath()
    NSFileManager.defaultManager.createDirectoryAtPath(
      dir,
      withIntermediateDirectories = true,
      attributes = null,
      error = null,
    )
    return dir
  }

  override fun filenameFor(artifactId: String, extension: String): String =
    "$artifactId.$extension"

  override fun resolve(ref: String): String =
    if (ref.startsWith("/")) ref else "${ensureDir()}/$ref"

  override suspend fun writeText(ref: String, content: String): Result<Unit> = runCatching {
    val path = resolve(ref)
    ensureDir()
    @Suppress("CAST_NEVER_SUCCEEDS")
    val ok = (content as NSString).writeToFile(
      path,
      atomically = true,
      encoding = NSUTF8StringEncoding,
      error = null,
    )
    if (!ok) error("Failed to write export file")
  }

  override suspend fun readText(ref: String): String? = runCatching {
    val path = resolve(ref)
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@runCatching null
    NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
  }.getOrNull()

  override suspend fun delete(ref: String): Boolean = runCatching {
    val path = resolve(ref)
    val fm = NSFileManager.defaultManager
    !fm.fileExistsAtPath(path) || fm.removeItemAtPath(path, error = null)
  }.getOrDefault(false)
}
