package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.IosPlatformContext
import alphainterplanetary.thinker.util.installUnhandledExceptionHook
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
  installUnhandledExceptionHook()
  App(IosPlatformContext())
}