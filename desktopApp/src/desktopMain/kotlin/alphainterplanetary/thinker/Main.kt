package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.DesktopPlatformContext
import alphainterplanetary.thinker.ui.platform.DesktopCloseCoordinator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
  DisposableEffect(Unit) {
    DesktopCloseCoordinator.exitApplication = ::exitApplication
    onDispose { DesktopCloseCoordinator.exitApplication = null }
  }
  Window(
    onCloseRequest = { DesktopCloseCoordinator.requestClose() },
    title = "Alpha Thinker",
    state = rememberWindowState(width = 1100.dp, height = 760.dp),
  ) {
    App(DesktopPlatformContext())
  }
}
