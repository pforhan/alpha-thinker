package alphainterplanetary.thinker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.events.Event

@Composable
actual fun OnAppInactive(onInactive: () -> Unit) {
  val currentOnInactive by rememberUpdatedState(onInactive)
  DisposableEffect(Unit) {
    val listener: (Event) -> Unit = {
      if (document.asDynamic().visibilityState == "hidden") {
        currentOnInactive()
      }
    }
    document.addEventListener("visibilitychange", listener)
    onDispose { document.removeEventListener("visibilitychange", listener) }
  }
}
