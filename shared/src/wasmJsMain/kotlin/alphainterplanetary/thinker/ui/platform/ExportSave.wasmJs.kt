package alphainterplanetary.thinker.ui.platform

import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.repository.ExportRepository
import androidx.compose.runtime.Composable

@Composable
actual fun rememberExportSaveAction(
  exportRepository: ExportRepository,
  onMessage: (String) -> Unit,
): ((ExportArtifact) -> Unit)? = null
