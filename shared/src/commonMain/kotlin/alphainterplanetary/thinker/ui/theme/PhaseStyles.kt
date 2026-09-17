package alphainterplanetary.thinker.ui.theme

import alphainterplanetary.thinker.phases.Phase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Visual style for a planning phase: the container/content colors of its badge and pill. */
@Immutable
data class PhaseStyle(
  val container: Color,
  val content: Color,
)

/** The currently selected phase-color theme, provided by [AlphaThinkerTheme]. */
val LocalPhaseTheme = compositionLocalOf { PhaseTheme.Default }

/** The app's current light/dark mode, provided by [AlphaThinkerTheme]. */
val LocalDarkTheme = compositionLocalOf { false }

/**
 * Resolves the visual style for a phase from the selectable phase theme
 * (see [PhaseTheme]). Each phase gets its own container/content pair, chosen
 * per the app's light/dark mode; the theme is set app-wide in Settings.
 */
object PhaseStyles {
  /** How diluted a phase container gets when tinting a whole list row. */
  const val RowTintAlpha = 0.12f

  @Composable
  fun forPhase(phase: Phase): PhaseStyle =
    LocalPhaseTheme.current.style(phase, LocalDarkTheme.current)

  /** The phase's container at [RowTintAlpha] — a wash for list-row backgrounds. */
  @Composable
  fun rowTint(phase: Phase): Color =
    forPhase(phase).container.copy(alpha = RowTintAlpha)
}