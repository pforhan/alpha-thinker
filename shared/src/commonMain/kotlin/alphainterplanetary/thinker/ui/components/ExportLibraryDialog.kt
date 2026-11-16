package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.ExportConfig
import alphainterplanetary.thinker.model.ExportStatus
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Minimal prepared-export library/management view for a project. Shows prepared
 * export files and provides basic actions; full management UX is expanded in 4A.3/4A.4.
 */
@Composable
fun ExportLibraryDialog(
  exports: List<ExportArtifact>,
  showOpen: Boolean,
  showShare: Boolean,
  showSave: Boolean,
  saveLabel: String,
  onDismiss: () -> Unit,
  onExportAgain: (ExportConfig) -> Unit,
  onShare: (ExportArtifact) -> Unit,
  onSave: (ExportArtifact) -> Unit,
  onOpen: (ExportArtifact) -> Unit,
  onDelete: (ExportArtifact) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Prepared exports") },
    text = {
      if (exports.isEmpty()) {
        Text(
          text = "No prepared exports yet for this project.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        LazyColumn(
          modifier = Modifier.heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
        ) {
          items(exports, key = { it.id }) { artifact ->
            ExportArtifactRow(
              artifact = artifact,
              showOpen = showOpen,
              showShare = showShare,
              showSave = showSave,
              saveLabel = saveLabel,
              onExportAgain = { onExportAgain(artifact.config) },
              onShare = { onShare(artifact) },
              onSave = { onSave(artifact) },
              onOpen = { onOpen(artifact) },
              onDelete = { onDelete(artifact) },
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

@Composable
private fun ExportArtifactRow(
  artifact: ExportArtifact,
  showOpen: Boolean,
  showShare: Boolean,
  showSave: Boolean,
  saveLabel: String,
  onExportAgain: () -> Unit,
  onShare: () -> Unit,
  onSave: () -> Unit,
  onOpen: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      val inProgress = artifact.status == ExportStatus.Pending ||
        artifact.status == ExportStatus.Generating
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = artifact.filename,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${artifact.format.name} · ${artifact.status}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (inProgress) {
          CircularProgressIndicator(
            modifier = Modifier.size(Dimens.ProgressIndicatorSize),
            strokeWidth = Dimens.ProgressStroke,
          )
        }
      }
      artifact.error?.takeIf { it.isNotBlank() }?.let { error ->
        Text(
          text = error,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
      HorizontalDivider()
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val canManage = artifact.status == ExportStatus.Completed ||
          artifact.status == ExportStatus.Failed
        if (canManage) {
          TextButton(onClick = onExportAgain) { Text("Prepare again") }
        }
        if (artifact.status == ExportStatus.Completed) {
          if (showOpen) TextButton(onClick = onOpen) { Text("Open") }
          if (showShare) TextButton(onClick = onShare) { Text("Share") }
          if (showSave) TextButton(onClick = onSave) { Text(saveLabel) }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (canManage) {
          TextButton(onClick = onDelete) { Text("Delete") }
        }
      }
    }
  }
}
