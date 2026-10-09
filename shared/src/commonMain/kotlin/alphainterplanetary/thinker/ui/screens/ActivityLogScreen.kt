package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.activitylog.ActivityRecord
import alphainterplanetary.thinker.activitylog.LogEntry
import alphainterplanetary.thinker.activitylog.LogMarkers
import alphainterplanetary.thinker.ui.chrome.AppChromeState
import alphainterplanetary.thinker.ui.chrome.AppScaffold
import alphainterplanetary.thinker.ui.components.EmptyState
import alphainterplanetary.thinker.ui.components.Pill
import alphainterplanetary.thinker.ui.platform.toClipEntry
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.ActivityLogItem
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Diagnostic viewer for the app-wide activity log (ENG-DESIGN.md schema item 4).
 * Launched from Settings → Activity Log.
 *
 * Each card is one activity (the [LogEntry] rows sharing an
 * [LogEntry.activityId]): its collapsed headline summarizes the outcome (a
  * question count + `done` flag, a produced title, or an
  * error), and tapping expands the full ordered rows with timestamps.
  *
 * This addresses IMPLEMENTATION-PLAN.md line 228 and the "why has the backend
 * stopped offering questions" audit: a `FollowUpQuestions` batch that produced
 * zero questions but marked `done`, or a failed/cancelled generation task with
 * its error message all read directly off the collapsed row.
 *
 * The same audit needs the other half: a failure message says what went wrong
 * but not what the model actually said, so a row that carries a raw payload
 * ([LogEntry.raw]) opens its (i) popup straight onto the verbatim reply —
 * including the ones that could not be parsed, which is precisely when the text
 * matters. The popup is selectable and copyable for the same reason: a reply
 * that needs reading twice usually needs pasting somewhere else.
 *
 * It is read-only; the delete action wipes the whole log (no confirmation) and
 * nothing else is mutated.
 */
@Composable
fun ActivityLogScreen(
  viewModel: ActivityLogViewModel,
  chrome: AppChromeState,
  onBack: () -> Unit,
) {
  val items by viewModel.items.collectAsState()
  AppScaffold(
    title = { Text("Activity Log") },
    chrome = chrome,
    onBack = onBack,
    actions = {
      IconButton(onClick = viewModel::clear) {
        Icon(Icons.Default.Delete, contentDescription = "Clear log")
      }
    },
  ) { paddingValues ->
    if (items.isEmpty()) {
      EmptyState(
        message = "No activity yet. Generation tasks and planning-engine interactions will appear here.",
        icon = Icons.Default.Info,
        modifier = Modifier.padding(paddingValues),
      )
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
      ) {
        items(items, key = { it.activity.activityId }) { item ->
          ActivityLogCard(item = item)
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
private fun ActivityLogCard(item: ActivityLogItem) {
  val activity = item.activity
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
            text = secondaryLine(activity, item.projectLabel),
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

/** Compact "project • time [• duration]" line; the project title is resolved
 * at display time (deleted projects fall back to their id). */
private fun secondaryLine(activity: ActivityRecord, projectLabel: String?): String {
  val pieces = mutableListOf<String>()
  projectLabel?.let { pieces += it }
  pieces += formatInstant(activity.latest.timestamp)
  activity.duration?.let { pieces += "duration ${formatTaskDuration(it)}" }
  return pieces.joinToString("  •  ")
}

@Composable
private fun summaryColor(hasError: Boolean): Color =
  if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

/**
 * One immutable log row: timestamp and full text, prompt rows verbatim. A row
 * carrying a raw payload (an unparsed model reply) always offers the (i) popup
 * — the popup then shows that payload instead of the row, which is the only
 * thing this row can't already tell you.
 */
@Composable
private fun EntryRow(entry: LogEntry) {
  var showFull by remember { mutableStateOf(false) }
  val raw = entry.raw
  val isVerbose = entry.log.startsWith(LogMarkers.Prompt) || entry.log.startsWith(LogMarkers.Response)
  val valueColor = when {
    entry.log.startsWith(LogMarkers.Failed) || entry.log == LogMarkers.Cancelled -> {
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
      if (raw != null || entry.log.length > 400) {
        IconButton(onClick = { showFull = true }) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = if (raw != null) {
              "Show the model's raw response"
            } else {
              "Show full entry"
            },
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

/**
 * The row's payload, untruncated: the verbatim model reply where the row has
 * one, otherwise the row's own text (a `prompt:` row has no separate raw).
 * Never both — repeating the row line above the reply is what this replaced,
 * and it is the reply that makes the popup worth opening.
 *
 * The text is selectable as well as copyable, because the point of a raw reply
 * is usually to take it somewhere else (an issue, a prompt-tuning session, a
 * bug report). Selection covers the pointer-driven targets; the Copy button
 * covers Android, and closes the dialog on the way out — closing *is* the
 * confirmation there is no other. The dismiss happens *after* the write, not
 * before it: the coroutine scope belongs to this dialog's composition, so
 * dismissing first would cancel the copy mid-flight.
 */
@Composable
private fun FullEntryDialog(
  entry: LogEntry,
  onDismiss: () -> Unit,
) {
  val raw = entry.raw
  val payload = raw ?: entry.log
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (raw != null) "Raw model response" else "Full entry") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SelectionContainer {
          Text(
            text = payload,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          scope.launch {
            clipboard.setClipEntry(payload.toClipEntry())
            onDismiss()
          }
        },
      ) {
        Text("Copy")
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
  Pill(
    text = label,
    containerColor = container,
    contentColor = content,
  )
}

private fun formatInstant(instant: Instant): String {
  val iso = instant.toString()
  val date = iso.substringBefore('T')
  val time = iso.substringAfter('T').substringBefore('.')
  return "$date $time"
}