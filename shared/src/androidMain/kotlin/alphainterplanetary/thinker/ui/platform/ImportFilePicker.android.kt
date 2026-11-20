package alphainterplanetary.thinker.ui.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberInterchangeFilePicker(
  onPicked: (List<PickedImportFile>) -> Unit,
  onMessage: (String) -> Unit,
): (() -> Unit)? {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val launcher = rememberLauncherForActivityResult(OpenInterchangeDocumentsContract()) { uris ->
    if (uris.isEmpty()) return@rememberLauncherForActivityResult
    scope.launch {
      val files = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> readPickedFile(context, uri) }
      }
      if (files.isEmpty()) {
        onMessage("Unable to read selected file(s)")
      } else {
        if (files.size != uris.size) {
          onMessage("Some selected files could not be read")
        }
        onPicked(files)
      }
    }
  }
  return remember(launcher) {
    { launcher.launch(Unit) }
  }
}

private class OpenInterchangeDocumentsContract : ActivityResultContract<Unit, List<Uri>>() {
  override fun createIntent(context: Context, input: Unit): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type = "*/*"
      putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      putExtra(
        Intent.EXTRA_MIME_TYPES,
        arrayOf("application/octet-stream", "application/gzip", "application/json", "text/plain"),
      )
    }

  override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
    if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
    val clip = intent.clipData
    if (clip != null) {
      return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri }
    }
    return listOfNotNull(intent.data)
  }
}

private fun readPickedFile(context: Context, uri: Uri): PickedImportFile? = runCatching {
  val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "import.thinker"
  val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
  PickedImportFile(name = name, bytes = bytes)
}.getOrNull()

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
  context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor ->
      val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
      if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()
