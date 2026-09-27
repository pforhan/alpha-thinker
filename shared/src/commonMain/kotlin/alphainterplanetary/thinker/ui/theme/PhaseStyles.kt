package alphainterplanetary.thinker.ui.theme

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.PhaseStyles.RowTintAlpha
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
 * Resolves visual styles from the selectable phase theme (see [PhaseTheme]).
 * Each phase gets its own container/content pair, chosen per the app's
 * light/dark mode; the theme is set app-wide in Settings. [accent] covers the
 * one place outside the phase UI that borrows a theme color.
 */
object PhaseStyles {
  /** How diluted a phase container gets when tinting a whole list row. */
  const val RowTintAlpha = 0.12f

  @Composable
  fun forPhase(phase: Phase): PhaseStyle =
    LocalPhaseTheme.current.style(phase, LocalDarkTheme.current)

  /**
   * The selected theme's [accent] pair — the one non-phase part of the UI that
   * follows the theme, currently the engine status bar's "in use" pill.
   *
   * It resolves to the theme's *first* entry ([BuiltInPhase.ScopeGoals]) and is
   * named for its role rather than its position, so pointing the accent at a
   * different palette entry is a change here alone: a palette reorder cannot
   * silently move a color the chrome depends on.
   *
   * Reusing an existing entry is also what keeps this free. The accent is a
   * validated [PhaseColors] quad, so it already clears WCAG AA against its own
   * ink in both modes (see `PhaseThemeTest`) — no new palette data, and nothing
   * to re-check when a theme is tuned.
   */
  @Composable
  fun accent(): PhaseStyle = forPhase(BuiltInPhase.ScopeGoals)

  /** The phase's container at [RowTintAlpha] — a wash for list-row backgrounds. */
  @Composable
  fun rowTint(phase: Phase): Color =
    forPhase(phase).container.copy(alpha = RowTintAlpha)
}