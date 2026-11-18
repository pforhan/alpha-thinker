package alphainterplanetary.thinker.ui.platform

import alphainterplanetary.thinker.export.presentExportPicker
import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.ExportStatus
import alphainterplanetary.thinker.repository.ExportRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun rememberExportSaveAction(
  exportRepository: ExportRepository,
  onMessage: (String) -> Unit,
): ExportSaveAction {
  val scope = rememberCoroutineScope()
  return remember(exportRepository, onMessage) {
    object : ExportSaveAction {
      override val supported: Boolean = true
      override val label: String = "Save"

      override fun invoke(artifact: ExportArtifact) {
        scope.launch {
          if (artifact.status != ExportStatus.Completed) {
            onMessage("Export is not ready to save")
            return@launch
          }
          val path = artifact.storageRef
          if (path.isNullOrBlank()) {
            onMessage("Export file is unavailable")
            return@launch
          }
          // presentExportPicker returns null if the file/presenter is unavailable or the user cancels.
          val destination = presentExportPicker(path)
          onMessage(
            if (destination != null) "Saved export" else "Save cancelled or unavailable",
          )
        }
      }
    }
  }
}
