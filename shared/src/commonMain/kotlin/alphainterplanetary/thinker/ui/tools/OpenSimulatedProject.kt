package alphainterplanetary.thinker.ui.tools

import alphainterplanetary.thinker.tools.SimulationState
import alphainterplanetary.thinker.ui.navigation.AppRoute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Whether a finished run should take the user to the project it created — the
 * whole rule, as a pure function so the policy is testable without a
 * composition and cannot drift between the two things that could act on it.
 *
 * - a finished run only, and only one the app has not already acted on
 *   ([handledProjectId]). Every run mints a fresh project id, so an id acts once
 *   and once only.
 * - not while the simulator screen is up ([route]). That screen *is* the result:
 *   it has to show the user what the run left behind, and a jump out from under
 *   it would replace the summary with the very thing the summary describes. A
 *   sheet up over it does not count — the sheet is not the result.
 *
 * Note what this does *not* decide: whether the user gets the jump afterwards.
 * Closing the simulator after a held finish leaves them where they were, because
 * the id is marked handled either way.
 */
internal fun shouldOpenSimulatedProject(
  finishedProjectId: String?,
  handledProjectId: String?,
  route: AppRoute,
): Boolean = finishedProjectId != null &&
  finishedProjectId != handledProjectId &&
  route != AppRoute.Simulator

/**
 * Opens the project a simulator run has finished creating, wherever the user is
 * when the run lands. The tools' own half of the simulator: the run's result is
 * reported by the surface that started it, and this is what takes over once that
 * surface is no longer in front.
 *
 * Called by the nav root for the same reason it announces failures: the finished
 * project is a fact about the run rather than about the screen that started it,
 * and the nav root is the one place that outlives every screen. [onOpenProject]
 * is the app's jump (`AppChromeState.openProjectFromList`) — it replaces the
 * stack, so whatever was over the project list closes with it.
 *
 * The one place the jump is withheld is [AppRoute.Simulator]; see
 * [shouldOpenSimulatedProject]. Both cases mark the id handled, so a run is acted
 * on exactly once either way.
 */
@Composable
internal fun ToolsViewModel.OpenSimulatedProject(
  route: AppRoute,
  onOpenProject: (String) -> Unit,
) {
  val state by simulation.collectAsState()
  val finished = (state as? SimulationState.Finished)?.projectId
  var handledProjectId by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(finished) {
    val projectId = finished ?: return@LaunchedEffect
    val jump = shouldOpenSimulatedProject(projectId, handledProjectId, route)
    handledProjectId = projectId
    if (jump) onOpenProject(projectId)
  }
}
