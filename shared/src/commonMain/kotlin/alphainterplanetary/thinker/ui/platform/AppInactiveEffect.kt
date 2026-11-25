package alphainterplanetary.thinker.ui.platform

import androidx.compose.runtime.Composable

/**
 * Invokes [onInactive] when the app becomes inactive/backgrounded or the host window loses focus.
 * Used for best-effort draft persistence; callers must dedupe and keep the callback cheap.
 */
@Composable
expect fun OnAppInactive(onInactive: () -> Unit)
