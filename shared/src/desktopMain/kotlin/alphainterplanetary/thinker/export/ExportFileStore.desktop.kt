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

  override fun pathFor(artifactId: String, extension: String): String =
    File(dir, "$artifactId.$extension").absolutePath

  override suspend fun writeText(path: String, content: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
      }
    }

  override suspend fun readText(path: String): String? =
    withContext(Dispatchers.IO) {
      runCatching { File(path).takeIf { it.exists() }?.readText() }.getOrNull()
    }

  override suspend fun delete(path: String): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(path)
        !file.exists() || file.delete()
      }.getOrDefault(false)
    }
}
