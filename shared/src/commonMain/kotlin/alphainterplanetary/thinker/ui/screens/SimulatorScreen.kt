package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.repository.ContextCompaction
import alphainterplanetary.thinker.tools.SimulationConfig
import alphainterplanetary.thinker.tools.SimulationState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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

/**
 * The project simulator, full-screen: the synopsis and the five knobs that
 * shape a run, the run's progress while it is in flight, and what it left behind
 * after.
 *
 * A destination rather than a piece of the Testing sheet, which is where it used
 * to live. A run is minutes of real latency on a form worth watching, and it is
 * the one screen that must not be dismissed by accident: it holds the result too,
 * because a finished run is worth reading before it is worth walking into.
 *
 * While a run is in flight the screen is deliberately hard to leave — no back
 * glyph, and back itself does nothing ([PlatformBackHandler] below). Cancel is
 * the only way out, so the user cannot walk away from a run they meant to watch
 * and find it finished with nobody watching. The run itself lives on the app
 * scope, so cancelling stops the run advancing rather than killing a model call
 * mid-flight, and the app-level jump to the finished project
 * (`AppChromeState.openSimulatedProject`) is what takes over once this screen
 * is no longer in front.
 */
@Composable
fun SimulatorScreen(
  chrome: AppChromeState,
) {
  val simulation by chrome.settings.simulation.collectAsState()
  val running = simulation is SimulationState.Running

  // Registered after the nav root's own handler, so it takes precedence over it
  // while this screen is composed: it consumes back without acting on it during a
  // run, rather than being disabled — a disabled handler would hand the event
  // straight back to the root, which would pop this screen mid-run. A sheet up
  // over the screen is still one entry to pop, run or no run.
  PlatformBackHandler(enabled = chrome.canGoBack) {
    if (!running || chrome.isSheetOpen) chrome.goBack()
  }

  AppScaffold(
    title = { Text("Simulate a project") },
    chrome = chrome,
    onBack = if (running) null else ({ chrome.goBack() }),
  ) { paddingValues ->
    SimulatorForm(
      state = simulation,
      chrome = chrome,
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
    )
  }
}

/**
 * The whole simulator, in one scrolling column: what the last run is doing (or
 * did), then the form.
 *
 * The form stays visible under a finished or failed run so its knobs can be
 * changed and run again without retyping; only a running run takes it away,
 * because there is nothing useful to edit mid-flight.
 */
@Composable
private fun SimulatorForm(
  state: SimulationState,
  chrome: AppChromeState,
  modifier: Modifier = Modifier,
) {
  var synopsis by remember { mutableStateOf("") }
  var compaction by remember { mutableStateOf(ContextCompaction.DropEarlierAnswers) }
  var resolveQuestions by remember { mutableStateOf(true) }
  var stopOnEmptyPhase by remember { mutableStateOf(false) }
  var replacePrevious by remember { mutableStateOf(true) }
  var previousTitleState by remember { mutableStateOf<String?>(null) }
  var hasPreviousState by remember { mutableStateOf(false) }

  LaunchedEffect(state is SimulationState.Finished) {
    // Probe whether there's a previous simulated project we own, on entry and
    // again once a run finishes — that run is what writes the setting. Keyed on
    // the *finished* part of the state rather than the whole state, because a
    // running state carries a progress tick on every emission and this would
    // then be a settings read per tick.
    hasPreviousState = chrome.settings.hasPreviousSimulation()
    previousTitleState = chrome.settings.previousSimulationTitle()
  }

  val config = {
    SimulationConfig(
      synopsis = synopsis.trim(),
      compaction = compaction,
      resolveQuestions = resolveQuestions,
      replacePrevious = replacePrevious,
      stopOnEmptyPhase = stopOnEmptyPhase,
    )
  }

  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SectionGap),
    verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
  ) {
    Text(
      text = "Runs a whole project against the selected engine: one round of " +
        "questions in every phase, with answers so each phase can be wrapped up. " +
        "Replaces the last simulated project rather than adding another.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when (state) {
      is SimulationState.Running -> SimulatorRunningRow(
        state = state,
        onCancel = chrome.settings::cancelSimulation,
      )

      is SimulationState.Finished -> SimulatorResultRow(
        state = state,
        onRunAgain = { chrome.settings.simulateProject(config()) },
        onOpenProject = chrome::openProjectFromList,
        onClose = chrome::goBack,
      )

      is SimulationState.Failed -> Text(
        text = "Simulation failed: ${state.message}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )

      SimulationState.Cancelled -> Text(
        text = "Simulation cancelled. The round it was waiting on is still finishing; " +
          "the run stopped advancing at this point.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      SimulationState.Idle -> Unit
    }

    if (state !is SimulationState.Running) {
      OutlinedTextField(
        value = synopsis,
        onValueChange = { synopsis = it },
        label = { Text("Synopsis") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
      )

      Text(
        text = "When the transcript no longer fits the engine's context:",
        style = MaterialTheme.typography.bodyMedium,
      )
      CompactionChoiceRow(selected = compaction, onSelect = { compaction = it })

      ToggleRow(
        label = "Answer the questions",
        description = "Canned answers and a few ignored questions, so every phase can " +
          "be wrapped up and the filters have something in them.",
        checked = resolveQuestions,
        onCheckedChange = { resolveQuestions = it },
      )

      ToggleRow(
        label = "Stop at an empty phase",
        description = "A phase the engine returns nothing for is asked a second " +
          "time. Off, the run carries on past one it still cannot fill; on, it " +
          "ends there.",
        checked = stopOnEmptyPhase,
        onCheckedChange = { stopOnEmptyPhase = it },
      )

      ToggleRow(
        label = if (replacePrevious && hasPreviousState) {
          previousTitleState?.let { "Replace \"$it\"" } ?: "Replace the previous simulated project"
        } else if (replacePrevious) {
          "Replace the previous simulated project"
        } else {
          "Keep the previous simulated project"
        },
        description = if (hasPreviousState) {
          "Your last simulation is still in the project list."
        } else {
          null
        },
        checked = replacePrevious,
        onCheckedChange = { replacePrevious = it },
      )

      Button(
        onClick = { chrome.settings.simulateProject(config()) },
        enabled = synopsis.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Run simulation")
      }
    }
  }
}

/** A labeled switch with the explanation under the label, as the simulator's three knobs read. */
@Composable
private fun ToggleRow(
  label: String,
  description: String?,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.bodyMedium)
      if (description != null) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

/** The in-flight state: a spinner, the phase, and the only way out. */
@Composable
private fun SimulatorRunningRow(
  state: SimulationState.Running,
  onCancel: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(Dimens.CardPadding),
      horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircularProgressIndicator(modifier = Modifier.size(Dimens.IconSizeMedium))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (state.phase == null) {
            state.detail
          } else {
            "Phase ${state.phaseNumber} of ${state.phaseCount} · ${state.phase.label}"
          },
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
  }
}

/**
 * What a finished run left behind. A cap that stopped the run early says so,
 * rather than reporting a project across fewer phases as if it had been planned
 * that way.
 *
 * The app's jump to the project is held while this screen is up
 * (`AppChromeState.openSimulatedProject`), so this is where the finish is
 * reported: Open project for the run's outcome, Close for the user's own way out
 * of it.
 */
@Composable
private fun SimulatorResultRow(
  state: SimulationState.Finished,
  onRunAgain: () -> Unit,
  onOpenProject: (String) -> Unit,
  onClose: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      Text(
        text = if (state.stoppedAtQuestionCap) {
          "Stopped at the question cap with ${state.questions} questions."
        } else if (state.stoppedAtEmptyPhase) {
          "Stopped at a phase the engine had nothing for, with ${state.questions} questions across ${state.phasesCovered} of ${state.phaseCount} phases."
        } else if (state.failures.isNotEmpty() && state.phasesCovered == 0) {
          "Simulated ${state.questions} questions, no complete phases."
        } else if (state.failures.isNotEmpty()) {
          "Simulated ${state.questions} questions across ${state.phasesCovered} of ${state.phaseCount} phases."
        } else {
          "Simulated ${state.questions} questions across every phase."
        },
        style = MaterialTheme.typography.titleSmall,
      )
      if (state.failures.isNotEmpty()) {
        Text(
          text = "${state.failures.size} phase(s) came up empty: ${state.failures.joinToString("; ")}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      TextButton(onClick = { onOpenProject(state.projectId) }) {
        Text("Open project")
      }
      TextButton(onClick = onRunAgain) {
        Text("Run again")
      }
      TextButton(onClick = onClose) {
        Text("Close")
      }
    }
  }
}

/** The three compaction choices, as chips; see [ContextCompactionDialog] for what they do. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactionChoiceRow(
  selected: ContextCompaction,
  onSelect: (ContextCompaction) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
    verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
  ) {
    ContextCompaction.entries.forEach { option ->
      FilterChip(
        selected = option == selected,
        onClick = { onSelect(option) },
        label = { Text(option.simulatorLabel) },
        elevation = null,
      )
    }
  }
}

/**
 * The chip label for a compaction choice. The enum's own names are written for
 * the near-limit dialog, where the surrounding prose explains them; a chip has
 * no room for that, so the two phrasings are separate — shortening the shared
 * name instead would make the dialog read worse.
 */
private val ContextCompaction.simulatorLabel: String
  get() = when (this) {
    ContextCompaction.KeepEverything -> "Keep everything"
    ContextCompaction.DropEarlierAnswers -> "Drop old answers"
    ContextCompaction.SummarizeEarlierPhases -> "Summarize phases"
  }