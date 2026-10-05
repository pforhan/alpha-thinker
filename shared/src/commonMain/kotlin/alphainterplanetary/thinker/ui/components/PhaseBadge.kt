package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.PhaseStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

/** Numeric badge for a phase's display order (e.g. the "3" in "Phase 3 of N"). */
@Composable
fun PhaseBadge(
  phase: Phase,
  modifier: Modifier = Modifier,
) {
  val style = PhaseStyles.forPhase(phase)
  Box(
    modifier = modifier
      .size(Dimens.BadgeSize)
      .clip(BadgeShape)
      .background(style.container),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = phase.order.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = style.content,
    )
  }
}

/** Pill-shaped label for a phase (e.g. "Phase 3: Design"). */
@Composable
fun PhasePill(
  phase: Phase,
  modifier: Modifier = Modifier,
) {
  val style = PhaseStyles.forPhase(phase)
  Pill(
    text = "Phase ${phase.order}: ${phase.label}",
    containerColor = style.container,
    contentColor = style.content,
    modifier = modifier,
  )
}
