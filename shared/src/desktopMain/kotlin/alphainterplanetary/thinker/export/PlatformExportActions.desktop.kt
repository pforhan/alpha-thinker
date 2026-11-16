package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  DesktopPlatformExportActions()

private class DesktopPlatformExportActions : PlatformExportActions {
  override suspend fun save(filename: String, mimeType: String, content: String): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        val dialog = FileDialog(null as Frame?, "Save export", FileDialog.SAVE).apply {
          file = filename
          isVisible = true
        }
        val dir = dialog.directory ?: return@withContext null
        val selected = dialog.file ?: return@withContext null
        val file = File(dir, selected)
        file.parentFile?.mkdirs()
        file.writeText(content)
        file.absolutePath
      }.getOrNull()
    }

  override suspend fun share(filename: String, mimeType: String, content: String): Boolean =
    withContext(Dispatchers.IO) {
      val path = save(filename, mimeType, content) ?: return@withContext false
      runCatching {
        if (java.awt.Desktop.isDesktopSupported()) {
          java.awt.Desktop.getDesktop().open(File(path).parentFile ?: File(path))
          true
        } else {
          false
        }
      }.getOrDefault(true)
    }
}
