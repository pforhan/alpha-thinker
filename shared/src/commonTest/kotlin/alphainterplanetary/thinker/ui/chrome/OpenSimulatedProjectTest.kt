package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.navigation.AppRoute
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who acts on a finished run: the app's jump to the project it created, or the
 * simulator screen, which is holding the result.
 */
class OpenSimulatedProjectTest {

  private val run = "project-1"

  @Test
  fun `a finished run takes the user to its project`() {
    assertTrue(
      shouldOpenSimulatedProject(
        finishedProjectId = run,
        handledProjectId = null,
        route = AppRoute.ProjectList,
      ),
    )
  }

  @Test
  fun `nothing is opened before a run has finished`() {
    assertFalse(
      shouldOpenSimulatedProject(
        finishedProjectId = null,
        handledProjectId = null,
        route = AppRoute.ProjectList,
      ),
    )
  }

  /**
   * The run is acted on exactly once. Recomposition, a re-entry, or the same
   * result being re-read on the way to another screen must not send the user
   * back to the project a second time.
   */
  @Test
  fun `a run already acted on is not acted on again`() {
    assertFalse(
      shouldOpenSimulatedProject(
        finishedProjectId = run,
        handledProjectId = run,
        route = AppRoute.ProjectList,
      ),
    )
  }

  /**
   * A finish that lands while the simulator screen is up is the screen's to
   * report: it is showing the summary of the very run the jump would navigate
   * away from.
   */
  @Test
  fun `a finish landing on the simulator screen is held there`() {
    assertFalse(
      shouldOpenSimulatedProject(
        finishedProjectId = run,
        handledProjectId = null,
        route = AppRoute.Simulator,
      ),
    )
  }

  /**
   * ...and holding it consumes it. Closing the simulator afterwards leaves the
   * user where they were rather than pulling them into the project they just
   * decided to close out of.
   */
  @Test
  fun `a held finish does not follow the user out of the simulator screen`() {
    assertFalse(
      shouldOpenSimulatedProject(
        finishedProjectId = run,
        handledProjectId = run,
        route = AppRoute.ProjectList,
      ),
    )
  }

  /**
   * Only the simulator screen holds a finish. A sheet over it — or over any
   * screen — is not the result, so the jump still happens underneath it.
   */
  @Test
  fun `a finish is not held for a sheet over the simulator screen`() {
    assertTrue(
      shouldOpenSimulatedProject(
        finishedProjectId = run,
        handledProjectId = null,
        route = AppRoute.ProjectDetail("project-2"),
      ),
    )
  }
}