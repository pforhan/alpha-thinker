package alphainterplanetary.thinker.ui.navigation

import androidx.compose.runtime.Composable

/**
 * No-op: a browser has no back event of its own to intercept — the history
 * back/forward buttons belong to the page, and a single-page app has no history
 * to walk. The back arrow in the app bar pops the same stack.
 */
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
