package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.ExportConfig
import alphainterplanetary.thinker.model.ExportStatus
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.util.formatTaskDuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlin.time.Instant

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
          modifier = Modifier.heightIn(max = 520.dp),
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
        Text(
          text = artifact.filename,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (inProgress) {
          CircularProgressIndicator(
            modifier = Modifier.size(Dimens.ProgressIndicatorSize),
            strokeWidth = Dimens.ProgressStroke,
          )
        }
      }
      ExportMetadataRow(artifact = artifact)
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
      val canManage = artifact.status == ExportStatus.Completed ||
        artifact.status == ExportStatus.Failed
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
        verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
      ) {
        if (canManage) {
          TextButton(onClick = onExportAgain) { Text("Prepare again") }
        }
        if (artifact.status == ExportStatus.Completed) {
          if (showOpen) TextButton(onClick = onOpen) { Text("Open") }
          if (showShare) TextButton(onClick = onShare) { Text("Share") }
          if (showSave) TextButton(onClick = onSave) { Text(saveLabel) }
        }
        if (canManage) {
          TextButton(onClick = onDelete) { Text("Delete") }
        }
      }
    }
  }
}

@Composable
private fun ExportMetadataRow(artifact: ExportArtifact) {
  val duration = artifact.completedAt?.let { completed ->
    val delta = completed - artifact.createdAt
    if (delta.isPositive()) formatTaskDuration(delta) else null
  }
  val pills = buildList {
    if (artifact.config.includeSummaries) add("Summaries")
    if (artifact.config.includeDrafts) add("Drafts")
    if (artifact.config.includeIgnoredQuestions) add("Ignored")
    if (!artifact.config.includeUnansweredQuestions) add("No unanswered")
    if (artifact.config.includeAnswerRevisionHistory) add("Revisions")
  }
  val details = buildString {
    append(artifact.format.name)
    append(" • ")
    append(artifact.status.name)
    append(" • ")
    append(formatTimestamp(artifact.createdAt))
    if (duration != null) {
      append(" • ")
      append("Took $duration")
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(Dimens.TightGap)) {
    MetadataText(details)
    if (pills.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
        verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
      ) {
        pills.forEach { label ->
          ConfigPill(label = label)
        }
      }
    }
  }
}

@Composable
private fun MetadataText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun ConfigPill(label: String) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.small,
  ) {
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = Dimens.TightGap, vertical = 2.dp),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

private fun formatTimestamp(instant: Instant): String =
  instant.toString().take(16).replace('T', ' ')
