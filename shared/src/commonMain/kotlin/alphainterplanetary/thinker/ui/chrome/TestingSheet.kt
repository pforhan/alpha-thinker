package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.repository.ContextCompaction
import alphainterplanetary.thinker.tools.ProjectSimulator
import alphainterplanetary.thinker.tools.SimulationConfig
import alphainterplanetary.thinker.tools.SimulationState
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The testing controls: the artificial planning-engine delay, so Task Manager
 * tasks stay visible long enough to watch, and the project simulator, which runs
 * a whole project against the selected engine.
 *
 * The planning-context budget used to live here too, which was the wrong drawer
 * for it — it shapes what a real generation is sent, not how the app behaves
 * under test. It is [ChromeSheet.PlanningContext] now, next to the engine whose
 * window it is a share of.
 *
 * Turning the delay on reveals the per-interaction pickers below the toggle, and
 * this scrolls just enough to bring them into view. The reveal is measured
 * rather than assumed because the pickers' height depends on the screen width
 * (they wrap), so a fixed scroll distance would be wrong on some sizes.
 */
@Composable
internal fun TestingSheetContent(chrome: AppChromeState) {
  val engineDelay by chrome.settings.engineDelay.collectAsState()
  val simulation by chrome.settings.simulation.collectAsState()
  val scrollState = rememberScrollState()
  var expandedHeight by remember { mutableIntStateOf(0) }
  var previousEnabled by remember { mutableStateOf(engineDelay.enabled) }

  LaunchedEffect(engineDelay.enabled) {
    if (engineDelay.enabled && !previousEnabled) {
      // Wait for the expanded controls to be measured: their onSizeChanged fires
      // during the layout pass right after the toggle turns them on.
      while (expandedHeight == 0) withFrameNanos {}
      scrollState.animateScrollTo(scrollState.value + expandedHeight)
    }
    previousEnabled = engineDelay.enabled
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    DelayControlItem(
      config = engineDelay,
      onEnabledChange = chrome.settings::setEngineDelayEnabled,
      onDelayChange = chrome.settings::setEngineDelay,
      onExpandedHeightChange = { expandedHeight = it },
    )

    HorizontalDivider()

    ProjectSimulatorItem(
      state = simulation,
      onRun = chrome.settings::simulateProject,
      onCancel = chrome.settings::cancelSimulation,
    )
  }
}

/**
 * The project simulator's controls: a synopsis, the two knobs a real project
 * would ask about, and a run button that stays put for the length of the run.
 *
 * The run outlives this sheet — it lives on the app scope so navigating away
 * doesn't kill it — but the button that started it doesn't get to disappear the
 * moment it is pressed. A simulator that closed its own dialog on submit would
 * leave the user with no way back to the run they just started, and the run is
 * exactly the thing worth watching: a remote engine plus the engine-delay setting
 * makes several minutes of real latency. So the panel holds its place and swaps
 * the form for a spinner and a running summary, with Cancel beside it; what it
 * becomes afterwards is the result.
 *
 * The spinner is deliberately indeterminate rather than a progress bar: the
 * simulator knows which phase it is on but not how much of a model call is left,
 * and a bar that guesses would be the more dishonest of the two.
 */
@Composable
private fun ProjectSimulatorItem(
  state: SimulationState,
  onRun: (SimulationConfig) -> Unit,
  onCancel: () -> Unit,
) {
  var synopsis by remember { mutableStateOf("") }
  var compaction by remember { mutableStateOf(ContextCompaction.DropEarlierAnswers) }
  var resolveQuestions by remember { mutableStateOf(true) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
    ) {
      Text(
        text = "Simulate a project",
        style = MaterialTheme.typography.titleSmall,
      )
      Text(
        text = "Runs a whole project against the selected engine: one round of " +
          "questions in every phase, with answers so each phase can be wrapped up. " +
          "Replaces the last simulated project rather than adding another.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      when (state) {
        is SimulationState.Running -> SimulatorRunningRow(state = state, onCancel = onCancel)

        is SimulationState.Finished -> SimulatorResultRow(
          state = state,
          onRunAgain = { onRun(SimulationConfig(synopsis, compaction = compaction, resolveQuestions = resolveQuestions)) },
        )

        is SimulationState.Failed -> Text(
          text = "Simulation failed: ${state.message}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )

        SimulationState.Cancelled -> Text(
          text = "Simulation cancelled. The round it was waiting on still finished, " +
            "so the project is there — partway through.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SimulationState.Idle -> Unit
      }

      // The form stays visible under a finished or failed run so its knobs can be
      // changed and run again without retyping; only a running run takes it away,
      // because there is nothing useful to edit mid-flight.
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

        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Answer the questions", style = MaterialTheme.typography.bodyMedium)
            Text(
              text = "Canned answers and a few ignored questions, so every phase can " +
                "be wrapped up and the filters have something in them.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
          Switch(checked = resolveQuestions, onCheckedChange = { resolveQuestions = it })
        }

        Button(
          onClick = {
            onRun(
              SimulationConfig(
                synopsis = synopsis.trim(),
                compaction = compaction,
                resolveQuestions = resolveQuestions,
              )
            )
          },
          enabled = synopsis.isNotBlank(),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Run simulation")
        }
      }
    }
  }
}

/** The in-flight state: a spinner, the phase, and a way out. */
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
 */
@Composable
private fun SimulatorResultRow(
  state: SimulationState.Finished,
  onRunAgain: () -> Unit,
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
        } else {
          "Simulated ${state.questions} questions across every phase."
        },
        style = MaterialTheme.typography.titleSmall,
      )
      if (state.failures.isNotEmpty()) {
        Text(
          text = "${state.failures.size} round(s) failed: ${state.failures.joinToString("; ")}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      Text(
        text = "Find it in the project list — it is the one marked \"${ProjectSimulator.SimulatedStatus}\".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(onClick = onRunAgain) {
        Text("Run again")
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
