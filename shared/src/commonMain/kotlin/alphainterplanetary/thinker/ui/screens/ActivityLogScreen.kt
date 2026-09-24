package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.activitylog.EngineActivityEvent
import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.ui.format.title
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.ActivityLogItem
import alphainterplanetary.thinker.ui.viewmodel.ActivityLogViewModel
import alphainterplanetary.thinker.ui.viewmodel.decodeQuestionCount
import alphainterplanetary.thinker.util.formatTaskDuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Diagnostic viewer for the append-only EngineActivity log (ENG-DESIGN.md schema
 * item 4). Launched from Settings → Activity Log.
 *
 * Each card is one activity ([EngineActivityEvent.activityId]): its collapsed
 * headline summarizes the terminal result (question counts, `done` flag,
 * remaining-in-phase answer, title, or error), and tapping expands the full
 * ordered history plus any child `Lookup` tool-call rows
 * ([EngineActivityEvent.parentActivityId]) with their arguments, results and
 * per-call latency.
 *
 * This addresses IMPLEMENTATION-PLAN.md line 228 and the "why has the backend
 * stopped offering questions" audit: a `RemainingInPhase` activity whose
 * [EngineActivityEvent.result] is `false`, a `FollowUpQuestions` batch that
 * produced zero questions but marked `done`, or a failed/cancelled generation
 * task with its error message all read directly off the collapsed row.
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
          ActivityLogCard(item = item)
        }
      }
    }
  }
}

/**
 * One activity as a tappable card: headline summary up top, full event history
 * and child tool calls revealed when expanded (runs the whole activity list in
 * a flat scroll — each card owns its own expand state via [mutableStateOf]).
 */
@Composable
private fun ActivityLogCard(item: ActivityLogItem) {
  var expanded by remember { mutableStateOf(false) }
  val latest = item.latest
  val terminal = item.terminal

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
              text = item.kind?.title ?: toolLabel(item.logCategory),
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
            EventTypeChip(eventType = terminal?.eventType ?: latest.eventType)
          }
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = secondaryLine(item),
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
        text = summaryLine(item),
        style = MaterialTheme.typography.bodyMedium,
        color = summaryColor(terminal?.eventType ?: latest.eventType),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )

      AnimatedVisibility(visible = expanded) {
        Column {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          item.history.forEach { event ->
            EventDetailRow(event = event)
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
          }
          if (item.children.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.TightGap))
            Text(
              text = "Tool calls",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Dimens.TightGap))
            item.children.forEach { child ->
              ToolCallRow(event = child)
              Spacer(modifier = Modifier.height(Dimens.TightGap))
            }
          }
        }
      }
    }
  }
}

/** Compact "activityId • engine • project/round • time [• duration]" line. */
private fun secondaryLine(item: ActivityLogItem): String {
  val pieces = mutableListOf<String>()
  pieces += item.activityId.take(8)
  item.logCategory?.let { pieces += it.name }
  item.latest.projectId?.let { pieces += "proj ${it.take(6)}" }
  item.latest.roundId?.let { pieces += "round ${it.take(6)}" }
  pieces += formatInstant(item.latest.timestamp)
  item.latest.durationMs?.let { pieces += formatTaskDuration(it.milliseconds) }
  return pieces.joinToString("  •  ")
}

/**
 * One-line default summary of the activity's outcome: question counts plus the
 * `done` flag for question batches, the capability answer for remaining-in-phase,
 * the produced title, or the carried error/progress.
 *
 * The headline is derived from the whole activity, not just its newest row: a
 * generation task writes lifecycle rows (`TaskRunner`) *and* interaction-detail
 * rows (`LoggingPlanningEngine`) under the same [EngineActivityEvent.activityId],
 * and only the detail rows carry the produced questions/[EngineActivityEvent.generationPayload].
 */
private fun summaryLine(item: ActivityLogItem): String {
  val terminal = item.terminal
  val type = terminal?.eventType ?: item.latest.eventType
  val outcome = when (type) {
    EngineActivityEventType.Succeeded -> {
      buildString {
        when (item.kind) {
          TaskKind.InitialQuestions,
          TaskKind.FollowUpQuestions,
          -> {
            val count = item.totalQuestions
            append(
              when {
                count == 0 -> "no questions"
                count == 1 -> "1 question"
                else -> "$count questions"
              }
            )
            item.detailPayload?.let { payload ->
              if (payload.startsWith("done=")) append(" — $payload") else append(" — $payload")
            }
          }

          TaskKind.RemainingInPhase -> append(
            "can produce more: ${terminal?.result ?: item.detailPayload ?: "?"}"
          )

          TaskKind.TitleRecommendation -> append(
            "title: ${item.detailPayload ?: terminal?.result?.toString() ?: "?"}"
          )

          TaskKind.SynopsisRewrite,
          TaskKind.AutoArchive,
          -> append(item.detailPayload ?: terminal?.result?.toString() ?: "done")

          null -> append(item.detailPayload ?: item.latest.parameters ?: "done")
        }
      }
    }

    EngineActivityEventType.Failed,
    EngineActivityEventType.Cancelled,
    -> "Error: ${terminal?.error ?: item.latest.error ?: "unknown"}"

    EngineActivityEventType.Created,
    EngineActivityEventType.Progress,
    -> {
      val params = item.latest.parameters
      val progressLine = item.latest.progress?.let { " — ${(it * 100).toInt()}%" } ?: ""
      params?.let { "started: $it$progressLine" } ?: "in progress$progressLine"
    }
  }
  return outcome.normalizeDisplay()
}

@Composable
private fun summaryColor(eventType: EngineActivityEventType): Color =
  when (eventType) {
    EngineActivityEventType.Failed,
    EngineActivityEventType.Cancelled,
    -> MaterialTheme.colorScheme.error

    EngineActivityEventType.Succeeded -> MaterialTheme.colorScheme.primary
    EngineActivityEventType.Created,
    EngineActivityEventType.Progress,
    -> MaterialTheme.colorScheme.onSurface
  }

/** One full history event: type chip, time, duration and every populated payload field. */
@Composable
private fun EventDetailRow(event: EngineActivityEvent) {
  var showFull by remember { mutableStateOf(false) }
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      EventTypeChip(eventType = event.eventType)
      Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
      Text(
        text = formatInstant(event.timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      event.durationMs?.let {
        Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
        Text(
          text = formatTaskDuration(it.milliseconds),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (event.hasFullDetail()) {
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { showFull = true }) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Show full event details",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(Dimens.TightGap))
    val labels = mutableListOf<Pair<String, String>>()
    event.progress?.let { labels += "progress" to "${(it * 100).toInt()}%" }
    event.result?.let { labels += "result" to it.toString() }
    event.error?.let { labels += "error" to it }
    event.promptUsed?.let { labels += "prompt" to it }
    event.parameters?.let { labels += "params" to it }
    event.generationPayload?.let { labels += "payload" to it }
    event.suggestedQuestions?.let { labels += "questions" to it }
    labels.forEach { (label, value) ->
      LabeledValueRow(label = label, value = value, verbose = label == "prompt")
    }
  }
  if (showFull) {
    FullEventDialog(event = event, onDismiss = { showFull = false })
  }
}

/** Whether a detail row has payload worth opening the full-read dialog for. */
private fun EngineActivityEvent.hasFullDetail(): Boolean =
  promptUsed != null || parameters != null || generationPayload != null || suggestedQuestions != null

/** Modal with every populated field of [event], rendered in full (no truncation). */
@Composable
private fun FullEventDialog(
  event: EngineActivityEvent,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Full event") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
      ) {
        FullFieldRow(label = "event", value = event.eventType.name)
        FullFieldRow(label = "time", value = formatInstant(event.timestamp))
        event.durationMs?.let { FullFieldRow(label = "duration", value = formatTaskDuration(it.milliseconds)) }
        event.progress?.let { FullFieldRow(label = "progress", value = "${(it * 100).toInt()}%") }
        event.result?.let { FullFieldRow(label = "result", value = it.toString()) }
        event.error?.let { FullFieldRow(label = "error", value = it) }
        event.promptUsed?.let { FullFieldRow(label = "prompt", value = it) }
        event.parameters?.let { FullFieldRow(label = "params", value = it) }
        event.generationPayload?.let { FullFieldRow(label = "payload", value = it) }
        event.suggestedQuestions?.let { FullFieldRow(label = "questions", value = it) }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

/** One label+value pair rendered completely, for the full-read dialog. */
@Composable
private fun FullFieldRow(
  label: String,
  value: String,
) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

/** A child `Lookup` tool call: label, arguments, result, per-call latency. */
@Composable
private fun ToolCallRow(event: EngineActivityEvent) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(BadgeShape)
      .background(MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(
      modifier = Modifier.padding(Dimens.PillHorizontalPadding, Dimens.PillVerticalPadding),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = toolName(event),
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      event.durationMs?.let {
        Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
        Text(
          text = formatTaskDuration(it.milliseconds),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    event.error?.let { error ->
      Text(
        text = "error: $error",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = Dimens.PillHorizontalPadding),
      )
    }
    event.generationPayload?.let { result ->
      LabeledValueRow(
        label = "result",
        value = result,
        horizontalPadding = Dimens.PillHorizontalPadding,
      )
    }
    event.parameters?.let { arguments ->
      LabeledValueRow(
        label = "args",
        value = arguments,
        horizontalPadding = Dimens.PillHorizontalPadding,
      )
    }
  }
}

@Composable
private fun LabeledValueRow(
  label: String,
  value: String,
  horizontalPadding: Dp = 0.dp,
  verbose: Boolean = false,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      maxLines = if (verbose) 12 else 6,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(Dimens.TightGap))
  }
}

/** Small rounded chip for an event lifecycle stage, colored by its semantics. */
@Composable
private fun EventTypeChip(eventType: EngineActivityEventType) {
  val (container, content) = when (eventType) {
    EngineActivityEventType.Succeeded -> {
      MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    EngineActivityEventType.Failed,
    EngineActivityEventType.Cancelled,
    -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    EngineActivityEventType.Progress -> {
      MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    EngineActivityEventType.Created -> {
      MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
  }
  Surface(
    color = container,
    contentColor = content,
    shape = BadgeShape,
  ) {
    Text(
      text = eventType.name,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(
        horizontal = Dimens.PillHorizontalPadding,
        vertical = Dimens.PillVerticalPadding,
      ),
    )
  }
}

private fun toolLabel(category: LogCategory?): String {
  if (category == LogCategory.Lookup) return "Tool call"
  return category?.name ?: "Activity"
}

/** The tool name for a Lookup row, best-effort from its parameters. */
private fun toolName(event: EngineActivityEvent): String {
  val params = event.parameters ?: return "lookup"
  // Parameters are recorded as "name=..., args=..." (or free text) by future
  // tool-calling engines; fall back to the raw parameters when no name crops up.
  val after = params.substringAfter("name=", missingDelimiterValue = "")
  if (after.isNotEmpty()) {
    val end = after.indexOfFirst { it == ',' || it == '&' || it == ';' }
    return if (end == -1) after.trim() else after.take(end).trim()
  }
  return params.take(40)
}

private fun formatInstant(instant: Instant): String {
  val iso = instant.toString()
  val date = iso.substringBefore('T')
  val time = iso.substringAfter('T').substringBefore('.')
  return "$date $time"
}

private fun String.normalizeDisplay(): String = replace(Regex("\\s+"), " ").trim()