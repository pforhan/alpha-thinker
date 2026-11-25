package alphainterplanetary.thinker.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Desktop-only bridge so the window close request can trigger best-effort draft autosave. */
object DesktopCloseCoordinator {
  private val _closeRequested = MutableStateFlow(0)
  val closeRequested: StateFlow<Int> = _closeRequested.asStateFlow()

  var exitApplication: (() -> Unit)? = null

  fun requestClose() {
    _closeRequested.value += 1
  }
}
