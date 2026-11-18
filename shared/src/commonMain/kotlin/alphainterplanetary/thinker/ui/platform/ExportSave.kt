package alphainterplanetary.thinker.ui.platform

import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.repository.ExportRepository
import androidx.compose.runtime.Composable

/**
 * Returns a platform-integrated save action for prepared exports, or null to use
 * the default repository/platform flow ([ExportRepository.saveExport]).
 *
 * Android implements this via the Storage Access Framework document picker so save
 * is integrated with UI/activity-result handling; other platforms return null.
 */
@Composable
expect fun rememberExportSaveAction(
  exportRepository: ExportRepository,
  onMessage: (String) -> Unit,
): ((ExportArtifact) -> Unit)?