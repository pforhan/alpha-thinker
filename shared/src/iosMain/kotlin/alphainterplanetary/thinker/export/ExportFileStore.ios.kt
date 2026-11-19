package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.IosPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

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

  @OptIn(BetaInteropApi::class)
  override suspend fun writeBytes(ref: String, bytes: ByteArray): Result<Unit> = runCatching {
    val path = resolve(ref)
    ensureDir()
    val data = if (bytes.isEmpty()) {
      NSData()
    } else {
      bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
      }
    }
    val ok = data.writeToFile(path, atomically = true)
    if (!ok) error("Failed to write export file")
  }

  override suspend fun readBytes(ref: String): ByteArray? = runCatching {
    val path = resolve(ref)
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@runCatching null
    val data = NSData.dataWithContentsOfFile(path) ?: return@runCatching null
    val length = data.length.toInt()
    if (length == 0) return@runCatching ByteArray(0)
    val out = ByteArray(length)
    out.usePinned { pinned ->
      memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    out
  }.getOrNull()

  override suspend fun delete(ref: String): Boolean = runCatching {
    val path = resolve(ref)
    val fm = NSFileManager.defaultManager
    !fm.fileExistsAtPath(path) || fm.removeItemAtPath(path, error = null)
  }.getOrDefault(false)
}
