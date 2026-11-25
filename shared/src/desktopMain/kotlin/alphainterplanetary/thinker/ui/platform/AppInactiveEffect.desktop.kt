package alphainterplanetary.thinker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
actual fun OnAppInactive(onInactive: () -> Unit) {
  val currentOnInactive by rememberUpdatedState(onInactive)
  val windowInfo = LocalWindowInfo.current
  var hasBeenFocused by remember { mutableStateOf(windowInfo.isWindowFocused) }

  LaunchedEffect(windowInfo) {
    snapshotFlow { windowInfo.isWindowFocused }
      .distinctUntilChanged()
      .collectLatest { focused ->
        if (focused) {
          hasBeenFocused = true
        } else if (hasBeenFocused) {
          currentOnInactive()
        }
      }
  }

  LaunchedEffect(Unit) {
    DesktopCloseCoordinator.closeRequested
      .drop(1)
      .collectLatest {
        currentOnInactive()
        delay(350)
        DesktopCloseCoordinator.exitApplication?.invoke()
      }
  }
}
