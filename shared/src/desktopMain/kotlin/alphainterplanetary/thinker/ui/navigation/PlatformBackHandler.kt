package alphainterplanetary.thinker.ui.navigation

import androidx.compose.runtime.Composable

/**
 * No-op: a desktop window has no system back event. The back arrow in the app
 * bar pops the same stack, which is the only back affordance the desktop app has
 * ever had.
 */
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
