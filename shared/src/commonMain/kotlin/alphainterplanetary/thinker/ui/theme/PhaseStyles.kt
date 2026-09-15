package alphainterplanetary.thinker.ui.theme

import alphainterplanetary.thinker.phases.Phase
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Visual style for a planning phase: the container/content colors of its badge and pill. */
@Immutable
data class PhaseStyle(
  val container: Color,
  val content: Color,
)

/**
 * Resolves the visual style for a phase from the app theme. All phases currently
 * share the theme's primary container; a per-phase color scheme lands here later.
 */
object PhaseStyles {
  @Composable
  fun forPhase(phase: Phase): PhaseStyle {
    val colorScheme = MaterialTheme.colorScheme
    return PhaseStyle(
      container = colorScheme.primaryContainer,
      content = colorScheme.onPrimaryContainer,
    )
  }
}