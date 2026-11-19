package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.export.ExportOpenCapability
import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.ExportStatus
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.util.formatTaskDuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlin.time.Instant

/**
 * Prepared-export library/management view for a project. Completed exports show
 * icon actions with short hints (view in an app, send/share, save a copy).
 * [openCapability] is checked per artifact so View is hidden or labeled based on
 * available viewers/handlers.
 */
@Composable
fun ExportLibraryDialog(
  exports: List<ExportArtifact>,
  showOpen: Boolean,
  showShare: Boolean,
  showSave: Boolean,
  saveLabel: String,
  onDismiss: () -> Unit,
  onShare: (ExportArtifact) -> Unit,
  onSave: (ExportArtifact) -> Unit,
  onOpen: (ExportArtifact) -> Unit,
  onDelete: (ExportArtifact) -> Unit,
  openCapability: suspend (ExportArtifact) -> ExportOpenCapability = { ExportOpenCapability.Single() },
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
    properties = DialogProperties(usePlatformDefaultWidth = false),
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
              onShare = { onShare(artifact) },
              onSave = { onSave(artifact) },
              onOpen = { onOpen(artifact) },
              onDelete = { onDelete(artifact) },
              openCapability = openCapability,
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
  onShare: () -> Unit,
  onSave: () -> Unit,
  onOpen: () -> Unit,
  onDelete: () -> Unit,
  openCapability: suspend (ExportArtifact) -> ExportOpenCapability,
) {
  val isCompleted = artifact.status == ExportStatus.Completed
  val capability = produceState<ExportOpenCapability>(
    initialValue = ExportOpenCapability.Unavailable,
    key1 = artifact.id,
    key2 = artifact.status,
    key3 = showOpen,
  ) {
    value = if (showOpen && isCompleted) openCapability(artifact) else ExportOpenCapability.Unavailable
  }.value

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
      val canManage = isCompleted || artifact.status == ExportStatus.Failed
      if (canManage) {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
          verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
        ) {
          if (isCompleted && capability.isAvailable && artifact.format.isDocument) {
            ExportAction(
              icon = Icons.AutoMirrored.Filled.OpenInNew,
              label = "View",
              hint = openHint(capability),
              onClick = onOpen,
            )
          }
          if (isCompleted && showShare) {
            ExportAction(
              icon = Icons.Default.Share,
              label = "Share",
              onClick = onShare,
            )
          }
          if (isCompleted && showSave) {
            ExportAction(
              icon = Icons.Default.SaveAlt,
              label = saveLabel,
              onClick = onSave,
            )
          }
          ExportAction(
            icon = Icons.Default.Delete,
            label = "Delete",
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

private fun openHint(capability: ExportOpenCapability): String? = when (capability) {
  ExportOpenCapability.Unavailable -> null
  is ExportOpenCapability.Single -> {
    val appLabel = capability.appLabel.orEmpty().trim()
    if (appLabel.isNotEmpty()) appLabel else "Default viewer"
  }
  is ExportOpenCapability.Multiple ->
    if (capability.count > 1) "(${capability.count} viewers)" else "Select viewer"
}

@Composable
private fun ExportAction(
  icon: ImageVector,
  label: String,
  hint: String? = null,
  onClick: () -> Unit,
  tint: Color = MaterialTheme.colorScheme.primary,
) {
  Column(
    modifier = Modifier
      .clip(MaterialTheme.shapes.small)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = Dimens.TightGap, vertical = Dimens.TightGap),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = "$label: ${hint.orEmpty()}",
      tint = tint,
      modifier = Modifier.size(Dimens.IconSizeMedium),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
    hint?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
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
    if (artifact.format.isDocument) {
      if (artifact.config.includeSummaries) add("Summaries")
      if (artifact.config.includeDrafts) add("Drafts")
      if (artifact.config.includeIgnoredQuestions) add("Ignored")
      if (!artifact.config.includeUnansweredQuestions) add("No unanswered")
      if (artifact.config.includeAnswerRevisionHistory) add("Revisions")
    } else {
      add("Full data")
    }
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
