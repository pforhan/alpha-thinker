package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.LocalModelRuntime
import alphainterplanetary.thinker.eval.EvalReport
import alphainterplanetary.thinker.eval.EvalRow
import alphainterplanetary.thinker.eval.EvalRollup
import alphainterplanetary.thinker.eval.EvalShareState
import alphainterplanetary.thinker.eval.ModelEvalState
import alphainterplanetary.thinker.ui.chrome.AppChromeState
import alphainterplanetary.thinker.ui.chrome.AppScaffold
import alphainterplanetary.thinker.ui.navigation.PlatformBackHandler
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/**
 * The on-device evaluation harness, full-screen.
 *
 * It is the app's port of `tools/eval_ollama_models.py`: each installed model
 * is run through the same fixtures, one row per `(model, run)` is written to
 * the platform's eval directory after every request, and the results are
 * grouped by hardware and OS so a recommendation for one phone is never
 * borrowed from a laptop's faster row.
 *
 * The sweep deliberately runs against the same `LocalBackendFactory` a real
 * planning request uses, one fresh backend per model. Measuring a backend that
 * a real request would not use would produce a number no one can act on.
 */
@Composable
fun ModelEvalScreen(
  chrome: AppChromeState,
) {
  val state by chrome.modelEval.state.collectAsState()
  val rows by chrome.modelEval.rows.collectAsState()
  val shareState by chrome.modelEval.share.collectAsState()
  val instances by chrome.engines.instances.collectAsState()
  val running = state is ModelEvalState.Running

  LaunchedEffect(Unit) {
    chrome.modelEval.refresh()
  }

  // Same rule as the simulator: a running sweep cannot be walked away from by
  // back, because the user meant to watch it; Cancel is the way out.
  PlatformBackHandler(enabled = chrome.canGoBack) {
    if (!running || chrome.isSheetOpen) chrome.goBack()
  }

  AppScaffold(
    title = { Text("Evaluate local models") },
    chrome = chrome,
    backEnabled = !running,
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SectionGap),
      verticalArrangement = Arrangement.spacedBy(Dimens.FormGap),
    ) {
      Text(
        text = "Runs the planning suite against installed on-device models and keeps " +
          "a JSON row after every request, so a sweep that runs out of memory can be " +
          "resumed instead of repeated.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (!LocalModelRuntime.inferenceSupported) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Local inference is not available on this platform. Rows can still " +
              "be read and shared, but a sweep has to run on a device with a LiteRT runtime.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Dimens.CardPadding),
          )
        }
      } else {
        ModelEvalRunControls(
          chrome = chrome,
          state = state,
          instances = instances,
          rows = rows,
          running = running,
        )
      }

      HorizontalDivider()
      ModelEvalShareSection(
        chrome = chrome,
        shareState = shareState,
        rowCount = rows.size,
      )
      HorizontalDivider()

      val byDevice = remember(rows) { EvalReport.byDevice(rows) }
      ModelEvalResults(byDevice)
    }
  }
}

/** The sweep form: which models, how many runs, whether to resume or start over. */
@Composable
private fun ModelEvalRunControls(
  chrome: AppChromeState,
  state: ModelEvalState,
  instances: List<EngineInstance>,
  rows: List<EvalRow>,
  running: Boolean,
) {
  val installed = remember(instances) { chrome.modelEval.installedModels() }
  var selected by remember(installed) { mutableStateOf(installed.map { it.id }.toSet()) }
  var runs by remember { mutableStateOf(1) }
  var startOver by remember { mutableStateOf(false) }

  when (state) {
    is ModelEvalState.Running -> ModelEvalRunningCard(
      state = state,
      onCancel = chrome.modelEval::cancel,
    )

    is ModelEvalState.Finished -> Text(
      text = "Sweep finished: ${state.cells} cells across ${state.models} model(s).",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    is ModelEvalState.Failed -> Text(
      text = if (state.outOfMemory) {
        "The sweep ran out of memory after saving its partial row. Run the model again " +
          "to resume from the cells it reached."
      } else {
        "The sweep stopped: ${state.message}"
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
    )

    ModelEvalState.Cancelled -> Text(
      text = "Sweep cancelled. Every cell that landed is saved; run the same model again " +
        "to resume where it stopped.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ModelEvalState.Idle -> Unit
  }

  if (running) return

  if (installed.isEmpty()) {
    Text(
      text = "No local models are installed yet. Download one from the Local engine tier, " +
        "then come back to measure it.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  Text(text = "Models", style = MaterialTheme.typography.titleSmall)
  installed.forEach { model ->
    val interrupted = rows.any { it.modelId == model.id && it.inProgress }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        checked = model.id in selected,
        onCheckedChange = { checked ->
          selected = if (checked) selected + model.id else selected - model.id
        },
      )
      Spacer(modifier = Modifier.width(Dimens.ContentGap))
      Column(modifier = Modifier.weight(1f)) {
        Text(text = model.label, style = MaterialTheme.typography.bodyMedium)
        Text(
          text = buildString {
            append(model.quantization ?: "unknown quant")
            append(" · ")
            append(model.parameterCount?.let { "${oneDecimal(it / 1_000_000_000.0)}B" } ?: "unknown size")
            if (interrupted) append(" · interrupted, will resume")
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  Text(text = "Runs per cell", style = MaterialTheme.typography.titleSmall)
  RunCountChips(selected = runs, onSelect = { runs = it })

  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = "Start over", style = MaterialTheme.typography.bodyMedium)
      Text(
        text = "Ignore interrupted rows and measure every cell again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.width(Dimens.ContentGap))
    Switch(checked = startOver, onCheckedChange = { startOver = it })
  }

  Button(
    onClick = {
      chrome.modelEval.start(
        models = installed.filter { it.id in selected },
        runs = runs,
        force = startOver,
      )
    },
    enabled = selected.isNotEmpty(),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(if (startOver) "Run selected models" else "Run or resume selected models")
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunCountChips(selected: Int, onSelect: (Int) -> Unit) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
    verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
  ) {
    listOf(1, 3).forEach { count ->
      FilterChip(
        selected = selected == count,
        onClick = { onSelect(count) },
        label = { Text("$count") },
        elevation = null,
      )
    }
  }
}

/** The in-flight sweep: a determinate bar, the current cell, and the only way out. */
@Composable
private fun ModelEvalRunningCard(
  state: ModelEvalState.Running,
  onCancel: () -> Unit,
) {
  val fraction = if (state.cellsTotal == 0) {
    0f
  } else {
    (state.cellsDone.toFloat() / state.cellsTotal).coerceIn(0f, 1f)
  }
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(Dimens.IconSizeMedium))
        Spacer(modifier = Modifier.width(Dimens.ContentGap))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Model ${state.modelNumber} of ${state.modelCount} · ${state.modelLabel}",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = state.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(onClick = onCancel) {
          Text("Cancel")
        }
      }
      LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        text = "${state.cellsDone} of ${state.cellsTotal} cells measured",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The LAN hand-off: one URL, one POST, and what the receiver said. */
@Composable
private fun ModelEvalShareSection(
  chrome: AppChromeState,
  shareState: EvalShareState,
  rowCount: Int,
) {
  var receiver by remember { mutableStateOf("") }

  Text(text = "Share with a computer", style = MaterialTheme.typography.titleSmall)
  Text(
    text = "Run tools/eval_receiver.py on the computer, then enter its address. " +
      "The receiver files the rows in docs/model-evals; nothing is sent until you tap Share.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  OutlinedTextField(
    value = receiver,
    onValueChange = {
      receiver = it
      if (shareState !is EvalShareState.Sending) chrome.modelEval.clearShareState()
    },
    label = { Text("Receiver address") },
    placeholder = { Text("192.168.1.20:8765") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedButton(
      onClick = { chrome.modelEval.share(receiver) },
      enabled = receiver.isNotBlank() &&
        rowCount > 0 &&
        shareState !is EvalShareState.Sending,
    ) {
      Text("Share rows")
    }
    Spacer(modifier = Modifier.width(Dimens.ContentGap))
    when (shareState) {
      is EvalShareState.Sending -> {
        CircularProgressIndicator(modifier = Modifier.size(Dimens.IconSizeSmall))
        Spacer(modifier = Modifier.width(Dimens.TightGap))
        Text(
          text = "Sending…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is EvalShareState.Sent -> Text(
        text = shareState.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      is EvalShareState.Failed -> Text(
        text = shareState.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )

      EvalShareState.Idle -> Unit
    }
  }
}

/** Every stored row, grouped and ranked separately for each hardware/OS pair. */
@Composable
private fun ModelEvalResults(byDevice: Map<String, List<EvalRollup>>) {
  Text(text = "Results by device", style = MaterialTheme.typography.titleSmall)
  if (byDevice.isEmpty()) {
    Text(
      text = "No rows yet. A sweep writes its first row as soon as the model is warm.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  byDevice.forEach { (_, rollups) ->
    val first = rollups.first()
    Text(
      text = EvalReport.deviceLabel(first.hardware),
      style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(Dimens.TightGap))
    rollups.forEach { rollup ->
      ModelEvalResultRow(rollup)
    }
  }
}

@Composable
private fun ModelEvalResultRow(rollup: EvalRollup) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      Text(text = rollup.displayName, style = MaterialTheme.typography.titleSmall)
      Text(
        text = "${rollup.tier.description} · quality ${rollup.quality?.let(::oneDecimal) ?: "—"} · " +
          "suite ${rollup.suiteS?.let { "${oneDecimal(it)}s" } ?: "—"} · ${rollup.sizeLabel}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "ok ${rollup.okRate.percent()} · clean ${rollup.cleanRate.percent()} · " +
          "json ${rollup.jsonOkRate.percent()} · ${rollup.cells} cells · " +
          "${rollup.timeouts} timeout(s) · ${rollup.errors} error(s)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (rollup.estimatedGb != null) {
        Text(
          text = "Estimated footprint ${oneDecimal(rollup.estimatedGb)} GB " +
            "(${rollup.quantization ?: "unknown quant"}, ${rollup.parameterSize ?: "unknown params"})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (rollup.inProgress) {
        Text(
          text = "In progress — run again to resume.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      if (rollup.notes.isNotEmpty()) {
        Text(
          text = rollup.notes.joinToString("; "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = "${rollup.runIds.size} run(s) measured",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun Double?.percent(): String =
  if (this == null) "—" else "${(this * 100).roundToInt()}%"

private fun oneDecimal(value: Double): String {
  val rounded = (value * 10).roundToInt() / 10.0
  return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
