package alphainterplanetary.thinker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android's back button and back gesture, via the activity's back dispatcher.
 *
 * Registering here rather than through Jetpack Navigation is what keeps the
 * system back gesture working after the graph was replaced with the shared
 * [NavStack]; the Activity's `OnBackPressedDispatcher` is the same thing
 * `NavHost` was registering with, and `ComponentActivity` only finishes on back
 * when nothing has claimed the event, which is what `enabled = false` at the
 * root of the stack relies on.
 */
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
  BackHandler(enabled = enabled, onBack = onBack)
}
