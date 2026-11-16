package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.AndroidPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import android.content.Context
import android.content.Intent
import java.io.File

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  AndroidPlatformExportActions((platformContext as? AndroidPlatformContext)?.appContext)

private class AndroidPlatformExportActions(
  private val context: Context?,
) : PlatformExportActions {
  override suspend fun save(filename: String, mimeType: String, content: String): String? {
    val ctx = context ?: return null
    return runCatching {
      val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
      val file = uniqueFile(dir, filename)
      file.writeText(content)
      file.absolutePath
    }.getOrNull()
  }

  override suspend fun share(filename: String, mimeType: String, content: String): Boolean {
    val ctx = context ?: return false
    return runCatching {
      val path = save(filename, mimeType, content) ?: return false
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, content)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(Intent.createChooser(intent, "Share export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      true
    }.getOrDefault(false)
  }
}

private fun uniqueFile(dir: File, filename: String): File {
  var candidate = File(dir, filename)
  if (!candidate.exists()) return candidate
  val base = filename.substringBeforeLast('.', filename)
  val ext = filename.substringAfterLast('.', "")
  var i = 1
  while (candidate.exists()) {
    val name = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
    candidate = File(dir, name)
    i++
  }
  return candidate
}
