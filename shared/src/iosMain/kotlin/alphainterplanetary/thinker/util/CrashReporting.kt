package alphainterplanetary.thinker.util

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import platform.Foundation.NSLog

/**
 * Installs a Kotlin/Native uncaught-exception hook so that crashes surface a clear
 * message and stack trace in the Xcode console instead of ending in a bare SIGABRT.
 */
@OptIn(ExperimentalNativeApi::class)
fun installUnhandledExceptionHook() {
  setUnhandledExceptionHook { throwable ->
    NSLog("=== Uncaught Kotlin exception ===")
    NSLog(throwable.stackTraceToString())
  }
}