package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.AndroidPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  AndroidPlatformExportActions((platformContext as? AndroidPlatformContext)?.appContext)

private class AndroidPlatformExportActions(
  private val context: Context?,
) : PlatformExportActions {
  override val supportsSave: Boolean = false
  override val supportsOpen: Boolean = true
  override val supportsShare: Boolean = true

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
    val resolvedMime = mimeType.ifBlank { "application/octet-stream" }
    return runCatching {
      val uri = contentUri(ctx, internalPath) ?: return@runCatching false
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = resolvedMime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, filename)
        clipData = ClipData.newRawUri(filename, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      val chooser = Intent.createChooser(intent, "Share export").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(chooser)
      true
    }.getOrDefault(false)
  }

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean {
    val ctx = context ?: return false
    val resolvedMime = mimeType.ifBlank { "application/octet-stream" }
    return runCatching {
      val uri = contentUri(ctx, internalPath) ?: return@runCatching false
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolvedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(intent)
      true
    }.getOrElse {
      // Fall back to share if no viewer handles the type directly.
      share(filename, mimeType, internalPath, content)
    }
  }

  private fun contentUri(ctx: Context, internalPath: String): Uri? {
    val file = File(internalPath)
    if (!file.exists()) return null
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
  }
}
