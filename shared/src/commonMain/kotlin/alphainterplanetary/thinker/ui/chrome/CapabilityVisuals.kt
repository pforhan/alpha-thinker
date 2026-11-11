package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.theme.PhaseStyles
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One [CapabilityReadiness]'s entire visual treatment, resolved in one place so the
 * header's pills, the flyout's pills, and the Status sheet's rows cannot
 * disagree about what "in use", "needs setup", and "not used" look like.
 *
 * **Fill carries the state, glyph repeats it.** Exactly one state is filled —
 * [CapabilityReadiness.Active] — and the other two are outlined, so a capability
 * reads as "on" or "off" before any text is parsed. The glyph is not decoration:
 * the fills are all light tints of similar lightness, so hue alone would leave
 * the state unreadable in grayscale and to a colorblind user, and [glyph] gives
 * each state its own shape (see [alphainterplanetary.thinker.ui.theme.PhaseTheme]'s
 * own rule that color is always a redundant cue).
 *
 * The fills and glyphs resolve from this one mapping, and the Status sheet's
 * rows are the header's own [StatusPill] with a value beside them, so the sheet
 * and the header cannot render the same capability differently.
 */
private val Unfilled = Color.Transparent

/** The fill behind a capability's name, or [Unfilled] for a state that is outlined. */
@Composable
internal fun CapabilityReadiness.containerColor(): Color =
  when (this) {
    // The app's color identity, so the one live capability is also the one place
    // the selected theme shows through in the chrome. Validated with the palette
    // (see `PhaseStyles.accent`).
    CapabilityReadiness.Active -> PhaseStyles.accent().container
    CapabilityReadiness.Incomplete -> MaterialTheme.colorScheme.errorContainer
    CapabilityReadiness.Unused -> Unfilled
  }

/**
 * The outline around a capability's name, or [Unfilled] for a state that is
 * filled. Only [CapabilityReadiness.Unused] is outlined, in a neutral role: an
 * unused capability is a normal state, not a problem to flag.
 */
@Composable
internal fun CapabilityReadiness.borderColor(): Color =
  when (this) {
    CapabilityReadiness.Active, CapabilityReadiness.Incomplete -> Unfilled
    CapabilityReadiness.Unused -> MaterialTheme.colorScheme.outlineVariant
  }

/**
 * Glyph and text drawn on [containerColor], or on the surface behind it when the
 * state is outlined.
 *
 * Both ends of a fill are one color, so a filled capability has no second,
 * quieter ink to draw its detail in: a row differentiates its name from its
 * value by type style alone rather than reaching for an `onSurfaceVariant` that
 * is not contrast-checked against the accent's container.
 */
@Composable
internal fun CapabilityReadiness.onContainerColor(): Color =
  when (this) {
    CapabilityReadiness.Active -> PhaseStyles.accent().content
    CapabilityReadiness.Incomplete -> MaterialTheme.colorScheme.onErrorContainer
    CapabilityReadiness.Unused -> MaterialTheme.colorScheme.onSurfaceVariant
  }

/**
 * The shape that says this state without relying on color: a check for what is in
 * use, an X for a selected capability that is not set up, a dash for one the
 * engine does not have.
 */
internal fun CapabilityReadiness.glyph(): ImageVector =
  when (this) {
    CapabilityReadiness.Active -> Icons.Filled.Check
    CapabilityReadiness.Incomplete -> Icons.Filled.Close
    CapabilityReadiness.Unused -> Icons.Filled.Remove
  }

/**
 * The collapsed cluster's dot: the whole status as one fill, for the wide form's
 * pills when there is no room for them.
 *
 * Lives beside the per-slot mapping rather than in `StatusCluster` so collapsing
 * the cluster cannot change what a color means — a `primary` dot here would read
 * as a different state from a theme-accent pill one resize away. The three
 * answers are the per-slot ones rolled up rather than a new palette: an
 * unconfigured capability's error (which is what [EngineStatus.needsAttention]
 * asks), the accent of a capability that is live, and otherwise the neutral ink.
 */
@Composable
internal fun EngineStatus.dotColor(): Color =
  when {
    needsAttention -> MaterialTheme.colorScheme.error
    slots.any { it.readiness == CapabilityReadiness.Active } -> PhaseStyles.accent().container
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
