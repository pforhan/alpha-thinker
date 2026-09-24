package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.activitylog.LogActivity
import alphainterplanetary.thinker.activitylog.LogEntry
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.ActivityLogViewModel
import alphainterplanetary.thinker.util.formatTaskDuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import kotlin.time.Instant

/**
 * Diagnostic viewer for the app-wide activity log (ENG-DESIGN.md schema item 4).
 * Launched from Settings → Activity Log.
 *
 * Each card is one activity (the [LogEntry] rows sharing an
 * [LogEntry.activityId]): its collapsed headline summarizes the outcome (a
 * question count + `done` flag, a capability answer, a produced title, or an
 * error), and tapping expands the full ordered rows with timestamps.
 *
 * This addresses IMPLEMENTATION-PLAN.md line 228 and the "why has the backend
 * stopped offering questions" audit: a `RemainingInPhase` activity whose answer
 * is `false`, a `FollowUpQuestions` batch that produced zero questions but
 * marked `done`, or a failed/cancelled generation task with its error message
 * all read directly off the collapsed row.
 *
 * It is read-only; the delete action wipes the whole log (no confirmation) and
 * nothing else is mutated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
  viewModel: ActivityLogViewModel,
  onBack: () -> Unit,
) {
  val items by viewModel.items.collectAsState()
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Activity Log") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = viewModel::clear) {
            Icon(Icons.Default.Delete, contentDescription = "Clear log")
          }
        },
      )
    },
  ) { paddingValues ->
    if (items.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(Dimens.EmptyStatePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Default.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Text(
          text = "No activity yet. Generation tasks and planning-engine interactions will appear here.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
      ) {
        items(items, key = { it.activityId }) { item ->
          ActivityLogCard(activity = item)
        }
      }
    }
  }
}

/**
 * One activity as a tappable card: headline summary up top, the full ordered
 * rows revealed when expanded (each card owns its own expand state via
 * [mutableStateOf]). Failed/cancelled activities tint the summary error-red so
 * the audit (IMPLEMENTATION-PLAN.md line 228) works off the collapsed view.
 */
@Composable
private fun ActivityLogCard(activity: LogActivity) {
  var expanded by remember { mutableStateOf(false) }

  Card(
    onClick = { expanded = !expanded },
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding),
  ) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = activity.category.label,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            activity.source?.let { source ->
              Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
              SourceChip(label = source.label, hasError = activity.hasError)
            }
          }
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = secondaryLine(activity),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.ContentGap))
        Icon(
          imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
          contentDescription = if (expanded) "Collapse" else "Expand",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        text = activity.summary,
        style = MaterialTheme.typography.bodyMedium,
        color = summaryColor(activity.hasError),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )

      AnimatedVisibility(visible = expanded) {
        Column {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          activity.entries.forEach { entry ->
            EntryRow(entry = entry)
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
          }
        }
      }
    }
  }
}

/** Compact "activityId • source • time [• duration]" line. */
private fun secondaryLine(activity: LogActivity): String {
  val pieces = mutableListOf<String>()
  pieces += activity.activityId.take(8)
  pieces += formatInstant(activity.latest.timestamp)
  activity.duration?.let { pieces += "duration ${formatTaskDuration(it)}" }
  return pieces.joinToString("  •  ")
}

@Composable
private fun summaryColor(hasError: Boolean): Color =
  if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

/** One immutable log row: timestamp and full text, prompt rows verbatim. */
@Composable
private fun EntryRow(entry: LogEntry) {
  var showFull by remember { mutableStateOf(false) }
  val isVerbose = entry.log.startsWith("prompt:") || entry.log.startsWith("response:")
  val valueColor = when {
    entry.log.startsWith("failed:") || entry.log.startsWith("error:") || entry.log == "cancelled" -> {
      MaterialTheme.colorScheme.error
    }
    isVerbose -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = formatInstant(entry.timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.weight(1f))
      if (entry.log.length > 400) {
        IconButton(onClick = { showFull = true }) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Show full entry",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(Dimens.TightGap))
    Text(
      text = entry.log,
      style = MaterialTheme.typography.bodySmall,
      color = valueColor,
      maxLines = if (isVerbose) 12 else 6,
      overflow = TextOverflow.Ellipsis,
    )
  }
  if (showFull) {
    FullEntryDialog(entry = entry, onDismiss = { showFull = false })
  }
}

/** Modal with the full text of [entry], rendered untruncated. */
@Composable
private fun FullEntryDialog(
  entry: LogEntry,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Full entry") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
      ) {
        Text(
          text = entry.log,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

/** Small rounded chip for an activity's producer, tinted red on failure. */
@Composable
private fun SourceChip(
  label: String,
  hasError: Boolean,
) {
  val (container, content) = if (hasError) {
    MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
  } else {
    MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
  }
  Surface(
    color = container,
    contentColor = content,
    shape = BadgeShape,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(
        horizontal = Dimens.PillHorizontalPadding,
        vertical = Dimens.PillVerticalPadding,
      ),
    )
  }
}

private fun formatInstant(instant: Instant): String {
  val iso = instant.toString()
  val date = iso.substringBefore('T')
  val time = iso.substringAfter('T').substringBefore('.')
  return "$date $time"
}