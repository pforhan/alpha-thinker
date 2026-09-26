package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.activitylog.ActivityRecord
import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.ui.viewmodel.SettingsViewModel
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The settings subscreens reachable from the header flyout. Each is presented
 * as a modal sheet over the current screen (see `AppScaffold`), so none of them
 * is a navigation destination — they are app chrome, not routes.
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
 * It also carries the one piece of live data the header reflects, the latest
 * activity, so the status dot can react to a failure. That flow is
 * `WhileSubscribed`, so an app that never opens the status surface never
 * collects from the log.
 */
@Stable
class AppChromeState(
  /** The hoisted settings ViewModel driving the flyout values and the sheets. */
  val settings: SettingsViewModel,
  /** The newest logged activity, or null on an empty log. */
  val latestActivity: StateFlow<ActivityRecord?>,
) {
  /** The open settings subscreen, or null when none is. */
  var sheet: ChromeSheet? by mutableStateOf(null)
    private set

  /** The app's single snackbar host (undo on ProjectDetail, tool results). */
  val snackbarHostState = SnackbarHostState()

  /**
   * Where the flyout's Tools rows navigate. Set by the navigation root, which is
   * the only place that knows the routes: these are app-chrome destinations, so
   * they hang off the chrome controller rather than being threaded through every
   * screen's signature.
   */
  var onOpenActivityLog: () -> Unit = {}
  var onOpenTaskManager: () -> Unit = {}

  /** Whether a settings subscreen is covering the screen. */
  val isSheetOpen: Boolean
    get() = sheet != null

  /** Opens [target]; opening a sheet while one is open just swaps it. */
  fun openSheet(target: ChromeSheet) {
    sheet = target
  }

  fun closeSheet() {
    sheet = null
  }

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
 * Builds the root-owned [AppChromeState] for a navigation host. Both nav roots
 * (commonMain's `StatefulNavApp` and androidMain's `NavGraph`) call this so the
 * platforms stay in step, handing it the destinations the flyout's Tools rows
 * jump to.
 */
@Composable
fun rememberAppChromeState(
  appComponent: AppComponent,
  onOpenActivityLog: () -> Unit = {},
  onOpenTaskManager: () -> Unit = {},
): AppChromeState {
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
  val chrome = remember(appComponent) { AppChromeState(settings, latestActivity) }
  SideEffect {
    chrome.onOpenActivityLog = onOpenActivityLog
    chrome.onOpenTaskManager = onOpenTaskManager
  }
  return chrome
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
