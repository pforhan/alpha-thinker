package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  DesktopPlatformExportActions()

private class DesktopPlatformExportActions : PlatformExportActions {
  override val supportsShare: Boolean = false

  override suspend fun openCapability(
    filename: String,
    mimeType: String,
    internalPath: String,
  ): ExportOpenCapability {
    val file = File(internalPath)
    if (!supportsOpen || !file.exists() || !Desktop.isDesktopSupported()) {
      return ExportOpenCapability.Unavailable
    }
    return ExportOpenCapability.Single()
  }

  override suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): String? = withContext(Dispatchers.IO) {
    runCatching {
      val dialog = FileDialog(null as Frame?, "Save export", FileDialog.SAVE).apply {
        file = filename
        isVisible = true
      }
      val dir = dialog.directory ?: return@withContext null
      val selected = dialog.file ?: return@withContext null
      val target = File(dir, selected)
      target.parentFile?.mkdirs()
      val source = File(internalPath)
      if (source.exists()) source.copyTo(target, overwrite = true) else target.writeText(content)
      target.absolutePath
    }.getOrNull()
  }

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean = open(filename, mimeType, internalPath, content)

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      val file = File(internalPath)
      if (!file.exists()) return@withContext false
      if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.OPEN)) {
          desktop.open(file)
        } else {
          desktop.open(file.parentFile ?: file)
        }
      } else {
        return@withContext false
      }
      true
    }.getOrDefault(false)
  }
}
