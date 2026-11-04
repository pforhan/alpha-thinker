package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.eval.ModelEvalState
import alphainterplanetary.thinker.tools.SimulationState
import alphainterplanetary.thinker.ui.navigation.AppRoute
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier

/**
 * The testing controls: the artificial planning-engine delay, so Task Manager
 * tasks stay visible long enough to watch, and the way into the project
 * simulator.
 *
 * The planning-context budget used to live here too, which was the wrong drawer
 * for it — it shapes what a real generation is sent, not how the app behaves
 * under test. It is [alphainterplanetary.thinker.engine.PlanningContext] now,
 * next to the engine whose window it is a share of.
 *
 * Turning the delay on reveals the per-interaction pickers below the toggle, and
 * this scrolls just enough to bring them into view. The reveal is measured
 * rather than assumed because the pickers' height depends on the screen width
 * (they wrap), so a fixed scroll distance would be wrong on some sizes.
 */
@Composable
internal fun TestingSheetContent(chrome: AppChromeState) {
  val engineDelay by chrome.tools.engineDelay.collectAsState()
  val simulation by chrome.tools.simulation.collectAsState()
  val evalState by chrome.modelEval.state.collectAsState()
  val evalRows by chrome.modelEval.rows.collectAsState()
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
      onEnabledChange = chrome.tools::setEngineDelayEnabled,
      onDelayChange = chrome.tools::setEngineDelay,
      onExpandedHeightChange = { expandedHeight = it },
    )

    HorizontalDivider()

    SimulatorLauncherItem(
      state = simulation,
      onOpen = { chrome.navigate(AppRoute.Simulator) },
    )

    HorizontalDivider()

    ModelEvalLauncherItem(
      state = evalState,
      rowCount = evalRows.size,
      onOpen = { chrome.navigate(AppRoute.ModelEval) },
    )
  }
}

/**
 * The way into the simulator, and a line about whatever the last run did.
 *
 * The simulator's config, its progress and its result all live on
 * [AppRoute.Simulator] rather than here: a run is minutes of real latency on a
 * form with five knobs in it, which is a screen's worth of work, and a
 * half-height sheet is a poor place to be when it finishes. So this is the
 * launcher and the standing answer to "is anything running, and what did the
 * last one leave" — the two questions worth answering without leaving the sheet.
 */
@Composable
private fun SimulatorLauncherItem(
  state: SimulationState,
  onOpen: () -> Unit,
) {
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
        text = when (state) {
          is SimulationState.Running -> "A run is in flight."
          is SimulationState.Finished -> "Last run: ${state.questions} questions across " +
            "${state.phasesCovered} of ${state.phaseCount} phases."

          is SimulationState.Failed -> "The last run failed: ${state.message}"
          SimulationState.Cancelled -> "The last run was cancelled."
          SimulationState.Idle -> "Runs a whole project against the selected engine."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text("Open the simulator")
      }
    }
  }
}

/**
 * The way into the on-device evaluation harness, and a line about what the last
 * sweep left behind.
 *
 * The harness itself is a full screen for the same reason the simulator is:
 * a sweep is minutes of real inference, and its results are worth reading
 * without first finding the sheet they were started from.
 */
@Composable
private fun ModelEvalLauncherItem(
  state: ModelEvalState,
  rowCount: Int,
  onOpen: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
    ) {
      Text(
        text = "Evaluate local models",
        style = MaterialTheme.typography.titleSmall,
      )
      Text(
        text = when (state) {
          is ModelEvalState.Running ->
            "Evaluating ${state.modelLabel} (${state.modelNumber} of ${state.modelCount})."

          is ModelEvalState.Finished ->
            "Last sweep measured ${state.cells} cells across ${state.models} model(s)."

          is ModelEvalState.Failed ->
            if (state.outOfMemory) {
              "The last sweep ran out of memory. Its partial row can be resumed."
            } else {
              "The last sweep failed: ${state.message}"
            }

          ModelEvalState.Cancelled ->
            "The last sweep was cancelled. Its partial row can be resumed."

          ModelEvalState.Idle ->
            if (rowCount > 0) {
              "$rowCount stored row(s) from earlier sweeps."
            } else {
              "Times the planning suite against installed local models and keeps " +
                "a row after every request."
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text("Open model evaluation")
      }
    }
  }
}
