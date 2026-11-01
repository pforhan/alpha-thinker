package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.downloads.DownloadKind
import alphainterplanetary.thinker.downloads.DownloadState
import alphainterplanetary.thinker.downloads.DownloadStatus
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.LocalModel
import alphainterplanetary.thinker.engine.LocalModelRuntime
import alphainterplanetary.thinker.ui.format.formatBytes
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.util.formatTaskDuration
import alphainterplanetary.thinker.util.formatTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.time.Duration.Companion.seconds

/**
 * The Local tier's model manager: the curated catalog, each model's download
 * state, and the installed rows.
 *
 * The dialog is opened from the Local card rather than being a sheet of its own,
 * so it can sit over the engine picker without adding a second navigation
 * destination. A download and a selection are separate actions here: a finished
 * transfer installs a row, and "Use" is what makes it the running engine.
 */
@Composable
internal fun LocalModelsDialog(
  models: List<LocalModel>,
  installed: List<EngineInstance>,
  selectedRef: EngineRef,
  downloads: List<DownloadState>,
  onDownload: (LocalModel) -> Unit,
  onUse: (LocalModel) -> Unit,
  onDelete: (LocalModel) -> Unit,
  onCancelDownload: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Local models") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.FormGap),
      ) {
        if (!LocalModelRuntime.downloadsSupported) {
          Text(
            text = "Downloading local models is not supported on this platform.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        models.forEach { model ->
          val instance = installed.firstOrNull { it.model == model.fileName }
          val download = downloads.lastOrNull {
            it.kind == DownloadKind.LocalFile && it.modelId == model.id
          }
          LocalModelRow(
            model = model,
            instance = instance,
            selected = instance != null && selectedRef == EngineRef.Instance(instance.id),
            download = download,
            onDownload = { onDownload(model) },
            onUse = { onUse(model) },
            onDelete = { onDelete(model) },
            onCancelDownload = onCancelDownload,
          )
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
private fun LocalModelRow(
  model: LocalModel,
  instance: EngineInstance?,
  selected: Boolean,
  download: DownloadState?,
  onDownload: () -> Unit,
  onUse: () -> Unit,
  onDelete: () -> Unit,
  onCancelDownload: (String) -> Unit,
) {
  val active = download != null && !download.status.finished
  val failed = download?.status == DownloadStatus.Failed

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
  ) {
    Text(
      text = model.label,
      style = MaterialTheme.typography.titleSmall,
    )
    Text(
      text = model.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "${formatBytes(model.expectedBytes)} · " +
        "${formatTokens(model.contextTokens)} context · ${model.license}",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when {
      instance != null -> InstalledActions(
        selected = selected,
        onUse = onUse,
        onDelete = onDelete,
      )

      active -> ActiveDownload(
        download = download,
        onCancel = { onCancelDownload(download.handle.id) },
      )

      failed -> Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = download.message ?: "Download failed",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDownload) {
          Text("Retry")
        }
      }

      else -> Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
          onClick = onDownload,
          enabled = LocalModelRuntime.downloadsSupported,
        ) {
          Text("Download")
        }
      }
    }
  }
}

@Composable
private fun InstalledActions(
  selected: Boolean,
  onUse: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = if (selected) "In use" else "Installed",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.weight(1f),
    )
    if (!selected) {
      TextButton(onClick = onUse) {
        Text("Use")
      }
    }
    TextButton(onClick = onDelete) {
      Text("Delete")
    }
  }
}

@Composable
private fun ActiveDownload(
  download: DownloadState,
  onCancel: () -> Unit,
) {
  val progress = download.progress
  if (progress != null) {
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = Dimens.TightGap),
    )
  } else {
    LinearProgressIndicator(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = Dimens.TightGap),
    )
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = downloadLabel(download),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onCancel) {
      Text("Cancel")
    }
  }
}

private fun downloadLabel(download: DownloadState): String = when (download.status) {
  DownloadStatus.Queued -> "Queued"
  DownloadStatus.Succeeded -> "Installed"
  DownloadStatus.Failed -> download.message ?: "Download failed"
  DownloadStatus.Cancelled -> "Cancelled"
  DownloadStatus.Paused -> "Paused"
  DownloadStatus.Running -> {
    val done = download.bytesDone
    val total = download.bytesTotal
    if (total != null && total > 0L) {
      "Downloading ${formatBytes(done)} of ${formatBytes(total)}" +
        etaLabel(done = done, total = total, bytesPerSecond = download.throughputBps)
    } else {
      "Downloading ${formatBytes(done)}"
    }
  }
}

/** " · 2m left" while a rate is known, or nothing when it is not. */
private fun etaLabel(done: Long, total: Long, bytesPerSecond: Long?): String {
  if (bytesPerSecond == null || bytesPerSecond <= 0L) return ""
  val remaining = total - done
  if (remaining <= 0L) return ""
  val etaSeconds = (remaining / bytesPerSecond).coerceAtLeast(1L)
  return " · ${formatTaskDuration(etaSeconds.seconds)} left"
}
