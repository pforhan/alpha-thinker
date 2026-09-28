package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.activitylog.ActivityRecord
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.navigation.AppRoute
import alphainterplanetary.thinker.ui.navigation.NavStack
import alphainterplanetary.thinker.ui.viewmodel.SettingsViewModel
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The settings subscreens reachable from the header flyout. Each is presented
 * as a modal sheet over the current screen (see `AppScaffold`).
 *
 * A sheet is an entry in the back stack rather than a route of its own, so back
 * closes it and a sheet that replaced another sheet returns to the one it came
 * from. What they are *not* is destinations in the sense the Activity Log is: a
 * sheet is always modal over whatever screen is beneath it, and opening one
 * never changes which screen that is.
 */
enum class ChromeSheet {
  /**
   * The larger status detail behind the header's status cluster: the active
   * engine, what it does, and how the last activity went. Read-only; the one
   * control that changes any of it lives in [Intelligence].
   */
  Status,

  /** The phase-color theme picker. */
  PhaseColors,

  /** The planning-backend picker, with the remote connection nested in it. */
  Intelligence,

  /** The Task-Manager testing controls (artificial engine delay). */
  Testing,
}

/**
 * App-wide header state, owned by the navigation root and threaded into every
 * screen's `AppScaffold`.
 *
 * It exists because three things outlive any single screen: the open settings
 * sheet, the app-wide snackbar (so there is exactly one host), and the settings
 * ViewModel — hoisted here so a long-running tool action's progress survives
 * navigating away from the screen that started it.
 *
 * It also owns the [stack], because the stack and the sheet are the same state:
 * a sheet is an entry in it, so the flyout's rows and the header's status cluster
 * can open one without knowing how the stack is stored, and back means the same
 * thing on every screen. The stack itself lives in `ui.navigation`; only this
 * controller writes to it, so nothing else has to reason about a sheet outliving
 * the screen that opened it.
 *
 * It carries the one piece of live data the header reflects, the latest
 * activity, so the status dot can react to a failure and so the failure can be
 * raised against the sheet that explains it (see [announceFailures]). That flow
 * is `WhileSubscribed`; the nav root subscribes for the life of the app, which
 * is what lets a failure be announced wherever it happens to be noticed.
 */
@Stable
class AppChromeState internal constructor(
  /** The hoisted settings ViewModel driving the flyout values and the sheets. */
  val settings: SettingsViewModel,
  /** The newest logged activity, or null on an empty log. */
  val latestActivity: StateFlow<ActivityRecord?>,
  private val stack: NavStack,
) {
  /** The app's single snackbar host (undo on ProjectDetail, tool results). */
  val snackbarHostState = SnackbarHostState()

  /** The screen being shown, under any open sheet. */
  internal val route: AppRoute
    get() = stack.route

  /** The open settings subscreen, or null when none is. */
  internal val sheet: ChromeSheet?
    get() = stack.sheet

  /** Whether a settings subscreen is covering the screen. */
  internal val isSheetOpen: Boolean
    get() = stack.sheet != null

  /**
   * Whether the open sheet replaced another sheet — the difference between the
   * sheet's back glyph (return to the sheet underneath) and its close one
   * (return to the screen).
   */
  internal val hasSheetBelow: Boolean
    get() = stack.hasSheetBelow

  /** Whether there is anywhere to go back to. */
  internal val canGoBack: Boolean
    get() = stack.canGoBack

  /**
   * Opens [target]; opening a sheet while one is open stacks it on top, so back
   * from the new sheet returns to the old one.
   */
  internal fun openSheet(target: ChromeSheet) {
    stack.openSheet(target)
  }

  /**
   * Pushes a screen, dropping any open sheet. This is the flyout's Tools rows'
   * route to the Activity Log and the Task Manager: they are app-chrome
   * destinations, so they hang off the chrome controller rather than being
   * threaded through every screen's signature.
   */
  internal fun navigate(route: AppRoute) {
    stack.navigate(route)
  }

  /** Pops one entry — a sheet if one is up, else the current screen. */
  internal fun goBack(): Boolean = stack.pop()

  /**
   * Shows an app-wide message, optionally with an undo-style action, and waits
   * for the user to act or dismiss it (so a caller can commit on dismissal).
   */
  suspend fun showMessage(
    text: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
  ): SnackbarResult = snackbarHostState.showSnackbar(
    message = text,
    actionLabel = actionLabel,
    duration = duration,
  )
}

/**
 * Builds the root-owned [AppChromeState] for a navigation host. The single nav
 * root calls this, so the back stack survives process death along with the
 * hoisted settings ViewModel and the activity flow.
 */
@Composable
fun rememberAppChromeState(appComponent: AppComponent): AppChromeState {
  val settings = remember(appComponent) {
    SettingsViewModel(
      settingsRepository = appComponent.settingsRepository,
      sampleProjectGenerator = appComponent.sampleProjectGenerator,
      scope = appComponent.appScope,
    )
  }
  val latestActivity = remember(appComponent) {
    appComponent.activityLogger.latestActivity()
      .stateIn(
        scope = appComponent.appScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
      )
  }
  val stack = rememberSaveable(saver = NavStack.Saver) { NavStack() }
  return remember(appComponent, settings, latestActivity, stack) {
    AppChromeState(
      settings = settings,
      latestActivity = latestActivity,
      stack = stack,
    )
  }
}

/**
 * The status the chrome displays right now: the selected engine and the remote
 * connection settings, run through the pure [engineStatus] derivation. Collected
 * here rather than passed in, so every screen's header reads the same status
 * from the same hoisted ViewModel.
 */
@Composable
fun AppChromeState.engineStatus(): EngineStatus {
  val mode by settings.engineMode.collectAsState()
  val baseUrl by settings.remoteLlmBaseUrl.collectAsState()
  val model by settings.remoteLlmModel.collectAsState()
  return remember(mode, baseUrl, model) { engineStatus(mode, baseUrl, model) }
}

/**
 * Whether [latest] is a failure the user still has to be told about — the whole
 * rule for when a failure interrupts, kept as a pure function so the policy is
 * testable without a composition and cannot drift between the three places it
 * would otherwise be re-derived.
 *
 * The four conditions, in the order they are cheapest to reject:
 * - a failure only. A success is the header dot's business, not a sheet's.
 * - not already announced ([announcedActivityId] is the consumed id). This is
 *   what makes re-entering an errored project quiet: the announcement is keyed
 *   on the activity that will be shown, so a failure is raised once however many
 *   times its project is opened, and the ledger persists across launches.
 * - no sheet up already. Skipping leaves the failure unconsumed, so it is still
 *   pending once the sheet closes.
 * - the failure belongs to where the user is. A projectless activity (a bare
 *   capability failure) or the project currently open qualifies; one belonging
 *   to some other project does not, because the user cannot act on it from
 *   here and the Project List already offers it as a chip. The Activity Log and
 *   Task Manager match no project, so a background failure never interrupts
 *   them either.
 *
 * Not consuming a skipped failure is deliberate: a later, newer failure simply
 * supersedes it, so nothing is ever announced long after the fact.
 */
internal fun shouldAnnounceFailure(
  latest: ActivityRecord?,
  announcedActivityId: String?,
  sheetIsOpen: Boolean,
  currentProjectId: String?,
): Boolean {
  if (latest == null || !latest.hasError) return false
  if (latest.activityId == announcedActivityId) return false
  if (sheetIsOpen) return false
  val failedProjectId = latest.projectId
  return failedProjectId == null || failedProjectId == currentProjectId
}

/**
 * Raises the [ChromeSheet.Status] sheet on a generation failure, once per
 * failure, app-wide. Called by the nav root — the one place that outlives every
 * screen — because the failure is a fact about the log rather than about the
 * screen the user happens to be on, and a screen-local raise re-runs on every
 * re-entry of a project that is still broken.
 *
 * The decision itself is [shouldAnnounceFailure]; this only supplies it, and
 * consumes the id it acts on so the next emission of the *same* failure — the
 * effect below re-runs on route and sheet changes — finds nothing to do.
 *
 * Keyed on the activity id, the project, and whether a sheet is up, so all three
 * ways a decision can become answerable re-evaluate it: the failure arriving,
 * the user navigating to (or away from) its project, and a sheet closing over a
 * failure that was skipped while it was open. The announced id is deliberately
 * not a key — it is an output, and keying on it would re-run the effect on the
 * write that the effect itself made.
 */
@Composable
internal fun AppChromeState.announceFailures() {
  val latestActivity by latestActivity.collectAsState()
  val announcedActivityId by settings.announcedFailureActivityId.collectAsState()
  val currentProjectId = (route as? AppRoute.ProjectDetail)?.projectId
  val sheetIsOpen = isSheetOpen

  LaunchedEffect(latestActivity?.activityId, currentProjectId, sheetIsOpen) {
    val activity = latestActivity ?: return@LaunchedEffect
    if (!shouldAnnounceFailure(activity, announcedActivityId, sheetIsOpen, currentProjectId)) {
      return@LaunchedEffect
    }
    settings.markFailureAnnounced(activity.activityId)
    openSheet(ChromeSheet.Status)
  }
}
