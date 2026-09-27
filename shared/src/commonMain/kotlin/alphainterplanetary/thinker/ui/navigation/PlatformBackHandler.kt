package alphainterplanetary.thinker.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Feeds the platform's own back event into the app's back stack.
 *
 * Only Android has one: the system back button and gesture. The other targets
 * have no back event to intercept, so their actuals do nothing and the app bar's
 * back arrow — which calls the same [NavStack.pop] — is the affordance, exactly
 * as it was before this seam existed. The seam is here so a target that gains a
 * back key later is a one-file change rather than a second nav root.
 *
 * [enabled] is false at the root of the stack, so back there falls through to
 * the platform (on Android, leaving the app).
 */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
