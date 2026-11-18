package alphainterplanetary.thinker.ui.platform

import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.repository.ExportRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** User-facing save action for a prepared export (availability, label, and handler). */
interface ExportSaveAction {
  val supported: Boolean
  val label: String
  operator fun invoke(artifact: ExportArtifact)
}

/**
 * Returns the platform-integrated save action for prepared exports.
 *
 * Android implements this via a UI/activity-result Storage Access Framework flow;
 * other platforms delegate to [ExportRepository.saveExport] (desktop dialog,
 * web download, etc.) so UI code has a single save entry point.
 */
@Composable
expect fun rememberExportSaveAction(
  exportRepository: ExportRepository,
  onMessage: (String) -> Unit,
): ExportSaveAction

/** Default save action backed by [ExportRepository.saveExport]. */
@Composable
fun rememberDefaultExportSaveAction(
  exportRepository: ExportRepository,
  onMessage: (String) -> Unit,
  supported: Boolean = exportRepository.supportsSave,
  label: String = exportRepository.saveActionLabel,
): ExportSaveAction {
  val scope = rememberCoroutineScope()
  return remember(exportRepository, onMessage, supported, label) {
    object : ExportSaveAction {
      override val supported: Boolean = supported
      override val label: String = label

      override fun invoke(artifact: ExportArtifact) {
        if (!supported) {
          onMessage("$label unavailable")
          return
        }
        scope.launch {
          val result = exportRepository.saveExport(artifact.id)
          onMessage(
            when {
              result == null -> "$label cancelled or unavailable"
              else -> "Export ${label.lowercase()}d"
            }
          )
        }
      }
    }
  }
}