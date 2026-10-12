package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.IosPlatformContext
import alphainterplanetary.thinker.util.installUnhandledExceptionHook
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS entry point the Swift app hosts. The crash hook is installed before the
 * first frame rather than from inside the content lambda, so it runs once per
 * controller instead of on every recomposition.
 */
fun MainViewController(): UIViewController {
  installUnhandledExceptionHook()
  return ComposeUIViewController {
    App(IosPlatformContext())
  }
}
