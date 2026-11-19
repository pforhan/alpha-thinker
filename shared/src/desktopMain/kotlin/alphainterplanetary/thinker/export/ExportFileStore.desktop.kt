package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.DesktopPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun provideExportFileStore(platformContext: PlatformContext): ExportFileStore =
  DesktopExportFileStore(platformContext as DesktopPlatformContext)

private class DesktopExportFileStore(
  private val platformContext: DesktopPlatformContext,
) : ExportFileStore {
  private val dir: File
    get() = File(platformContext.exportsDirectoryPath()).apply { mkdirs() }

  override fun filenameFor(artifactId: String, extension: String): String =
    "$artifactId.$extension"

  override fun resolve(ref: String): String {
    val file = File(ref)
    return if (file.isAbsolute) ref else File(dir, ref).absolutePath
  }

  override suspend fun writeBytes(ref: String, bytes: ByteArray): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(resolve(ref))
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
      }
    }

  override suspend fun readBytes(ref: String): ByteArray? =
    withContext(Dispatchers.IO) {
      runCatching { File(resolve(ref)).takeIf { it.exists() }?.readBytes() }.getOrNull()
    }

  override suspend fun delete(ref: String): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(resolve(ref))
        !file.exists() || file.delete()
      }.getOrDefault(false)
    }
}
