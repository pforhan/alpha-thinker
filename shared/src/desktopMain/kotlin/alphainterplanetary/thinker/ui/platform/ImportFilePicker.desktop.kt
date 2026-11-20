package alphainterplanetary.thinker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
actual fun rememberInterchangeFilePicker(
  onPicked: (List<PickedImportFile>) -> Unit,
  onMessage: (String) -> Unit,
): (() -> Unit)? = remember(onPicked, onMessage) {
  {
    // Use AWT FileDialog on the EDT/IO as appropriate; keep simple and blocking in callback.
    val dialog = FileDialog(null as Frame?, "Import projects (.thinker)", FileDialog.LOAD).apply {
      isMultipleMode = true
      filenameFilter = FilenameFilter { _, name ->
        val lower = name.lowercase()
        lower.endsWith(".thinker") || lower.endsWith(".json")
      }
      isVisible = true
    }
    val files = dialog.files?.toList().orEmpty()
    if (files.isEmpty()) return@remember
    val picked = runBlocking(Dispatchers.IO) {
      files.mapNotNull { file -> readPickedFile(file) }
    }
    if (picked.isEmpty()) {
      onMessage("Unable to read selected file(s)")
    } else {
      if (picked.size != files.size) {
        onMessage("Some selected files could not be read")
      }
      onPicked(picked)
    }
  }
}

private fun readPickedFile(file: File): PickedImportFile? = runCatching {
  PickedImportFile(name = file.name, bytes = file.readBytes())
}.getOrNull()
