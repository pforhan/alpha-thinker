package alphainterplanetary.thinker.ui.chrome

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One slot's colors, in one place so the header's pills and the Status sheet
 * cannot disagree about what "in use", "needs setup", and "not used" look like.
 *
 * All of them are existing Material roles — nothing here needed a new
 * `ExtendedColors` entry, and an unused capability is muted through
 * `surfaceContainerHighest` rather than by inventing a dimmed variant.
 */

/** The pill's background. */
@Composable
internal fun CapabilityState.containerColor(): Color =
  when (this) {
    CapabilityState.Active -> MaterialTheme.colorScheme.secondaryContainer
    CapabilityState.Incomplete -> MaterialTheme.colorScheme.errorContainer
    CapabilityState.Unused -> MaterialTheme.colorScheme.surfaceContainerHighest
  }

/** Text drawn on [containerColor]. */
@Composable
internal fun CapabilityState.onContainerColor(): Color =
  when (this) {
    CapabilityState.Active -> MaterialTheme.colorScheme.onSecondaryContainer
    CapabilityState.Incomplete -> MaterialTheme.colorScheme.onErrorContainer
    CapabilityState.Unused -> MaterialTheme.colorScheme.onSurfaceVariant
  }

/** A state named in prose on the sheet's own surface, with no pill behind it. */
@Composable
internal fun CapabilityState.readoutColor(): Color =
  when (this) {
    CapabilityState.Active -> MaterialTheme.colorScheme.onSecondaryContainer
    // A selected capability that is not set up is the one thing worth shouting
    // about, so it takes the error role rather than the container's muted one.
    CapabilityState.Incomplete -> MaterialTheme.colorScheme.error
    CapabilityState.Unused -> MaterialTheme.colorScheme.onSurfaceVariant
  }
