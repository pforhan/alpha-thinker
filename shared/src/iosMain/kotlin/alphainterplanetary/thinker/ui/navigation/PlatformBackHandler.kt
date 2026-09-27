package alphainterplanetary.thinker.ui.navigation

import androidx.compose.runtime.Composable

/**
 * No-op: iOS has no system back event to intercept. The back arrow in the app
 * bar pops the same stack.
 */
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
