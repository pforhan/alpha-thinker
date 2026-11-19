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
  AndroidPlatformExportActions((platformContext as AndroidPlatformContext).appContext)

private class AndroidPlatformExportActions(
  private val context: Context,
) : PlatformExportActions {
  // Direct/service-level save is unavailable on Android; user-chosen save is
  // provided via the UI-integrated SAF flow (rememberExportSaveAction).
  override val supportsSave: Boolean = false
  override val supportsOpen: Boolean = true
  override val supportsShare: Boolean = true

  override suspend fun openCapability(
    filename: String,
    mimeType: String,
    internalPath: String,
  ): ExportOpenCapability {
    val resolvedMime = mimeType.resolved()
    val uri = context.contentUri(internalPath) ?: return ExportOpenCapability.Unavailable
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, resolvedMime)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val handlers = context.packageManager.queryIntentActivities(intent, 0)
    return when (handlers.size) {
      0 -> ExportOpenCapability.Unavailable
      1 -> {
        val label = handlers.first().loadLabel(context.packageManager).toString().takeIf { it.isNotBlank() }
        ExportOpenCapability.Single(label)
      }
      else -> ExportOpenCapability.Multiple(handlers.size)
    }
  }

  override suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): String? = null // User-chosen save uses UI/activity-result integration (SAF).

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): Boolean {
    val resolvedMime = mimeType.resolved()
    return runCatching {
      val uri = context.contentUri(internalPath) ?: return@runCatching false
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
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): Boolean {
    val resolvedMime = mimeType.resolved()
    return runCatching {
      val uri = context.contentUri(internalPath) ?: return@runCatching false
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolvedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  private fun String.resolved(): String = ifBlank { "application/octet-stream" }

  private fun Context.contentUri(internalPath: String): Uri? {
    val file = File(internalPath)
    if (!file.exists()) return null
    return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
  }
}
