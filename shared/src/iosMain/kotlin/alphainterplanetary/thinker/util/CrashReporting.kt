package alphainterplanetary.thinker.util

import platform.Foundation.NSLog
import kotlin.experimental.ExperimentalNativeApi

private var hookInstalled = false

/**
 * Installs a Kotlin/Native uncaught-exception hook so that crashes surface a clear
 * message and stack trace in the Xcode console instead of ending in a bare SIGABRT.
 *
 * The hook is process-global, so this is idempotent: calling it more than once
 * (e.g. one call per window) installs it only once.
 */
@OptIn(ExperimentalNativeApi::class)
fun installUnhandledExceptionHook() {
  if (hookInstalled) return
  hookInstalled = true
  setUnhandledExceptionHook { throwable ->
    NSLog("=== Uncaught Kotlin exception ===")
    NSLog(throwable.stackTraceToString())
  }
}