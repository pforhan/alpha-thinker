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
  override val supportsSave: Boolean = false
  override val supportsOpen: Boolean = false

  override suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): String? = null // User-chosen save (SAF) requires UI/activity integration; degrade gracefully.

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean {
    val ctx = context ?: return false
    return runCatching {
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType.ifBlank { "text/plain" }
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, content)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(Intent.createChooser(intent, "Share export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      true
    }.getOrDefault(false)
  }

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean = share(filename, mimeType, internalPath, content)
}
