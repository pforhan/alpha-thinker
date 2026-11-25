package alphainterplanetary.thinker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

@Composable
actual fun OnAppInactive(onInactive: () -> Unit) {
  val currentOnInactive by rememberUpdatedState(onInactive)
  DisposableEffect(Unit) {
    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
      name = UIApplicationDidEnterBackgroundNotification,
      `object` = null,
      queue = NSOperationQueue.mainQueue,
    ) {
      currentOnInactive()
    }
    onDispose {
      NSNotificationCenter.defaultCenter.removeObserver(observer)
    }
  }
}
