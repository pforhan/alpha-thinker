package alphainterplanetary.thinker.ui.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.repository.ExportRepository
import java.io.OutputStreamWriter
import kotlinx.coroutines.launch

@Composable
actual fun rememberExportSaveAction(
  exportRepository: ExportRepository,
  onMessage: (String) -> Unit,
): ((ExportArtifact) -> Unit)? {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pending by remember { mutableStateOf<PendingExportSave?>(null) }

  val launcher = rememberLauncherForActivityResult(CreateExportDocumentContract()) { uri ->
    val request = pending
    pending = null
    if (uri == null || request == null) {
      if (request != null) onMessage("Save cancelled")
      return@rememberLauncherForActivityResult
    }
    scope.launch {
      val result = writeExportToUri(context, uri, request.content)
      onMessage(
        if (result) "Saved: ${request.filename}" else "Unable to save export",
      )
    }
  }

  return remember(exportRepository, context, launcher) {
    { artifact ->
      scope.launch {
        val contentResult = exportRepository.readExportContent(artifact.id)
        val content = contentResult.getOrNull()
        if (content == null) {
          val message = contentResult.exceptionOrNull()?.message
            ?: "Export is unavailable"
          onMessage(message)
          return@launch
        }
        pending = PendingExportSave(
          filename = artifact.filename,
          content = content,
        )
        launcher.launch(artifact.format.mimeType.ifBlank { "application/octet-stream" } to artifact.filename)
      }
    }
  }
}

private data class PendingExportSave(
  val filename: String,
  val content: String,
)

private class CreateExportDocumentContract : ActivityResultContract<Pair<String, String>, Uri?>() {
  override fun createIntent(context: Context, input: Pair<String, String>): Intent =
    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type = input.first.ifBlank { "application/octet-stream" }
      putExtra(Intent.EXTRA_TITLE, input.second)
    }

  override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
    if (resultCode != Activity.RESULT_OK) return null
    return intent?.data
  }
}

private fun writeExportToUri(context: Context, uri: Uri, content: String): Boolean =
  runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
      OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
        writer.write(content)
      }
    } ?: error("Unable to open destination")
    true
  }.getOrDefault(false)
