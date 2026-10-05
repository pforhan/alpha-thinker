package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.PhaseStyles
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Where a [PhaseBadgeCell] puts its badge, which also decides where the burst originates. */
enum class BadgeCellAnchor {
  /** Badge centered in the cell; the burst explodes from the cell's middle. */
  Centered,

  /**
   * Badge flush to the cell's start, with the burst originating just past the
   * badge's trailing edge so the particles fill the gap toward the row's label
   * instead of padding the badge on both sides.
   */
  Start,
}

/**
 * A [PhaseBadge] in a cell wide enough for a one-shot [ConfettiBurst] to play
 * around it. The burst is always drawn in the phase's own colors —
 * [extraBurstColors] adds to them, which is how the just-completed phase gets
 * the celebration accent — and [horizontalBias] skews the pieces' drift.
 *
 * The burst only plays while [burstVisible] is true, and the cell owns no
 * animation of its own: the caller drives whatever pops the badge in (a scale,
 * a staggered timeline) and flips the flag when that has finished, so the
 * confetti always trails the pop-in. Because the burst leaves composition when
 * the flag drops, raising it again replays the burst.
 */
@Composable
fun PhaseBadgeCell(
  phase: Phase,
  burstVisible: Boolean,
  intensity: Int,
  durationMs: Int,
  cellWidth: Dp,
  modifier: Modifier = Modifier,
  anchor: BadgeCellAnchor = BadgeCellAnchor.Centered,
  extraBurstColors: List<Color> = emptyList(),
  horizontalBias: Float = 0f,
) {
  val style = PhaseStyles.forPhase(phase)
  Box(
    modifier = modifier.size(cellWidth, Dimens.PhaseBadgeCellHeight),
    contentAlignment = when (anchor) {
      BadgeCellAnchor.Centered -> Alignment.Center
      BadgeCellAnchor.Start -> Alignment.CenterStart
    },
  ) {
    PhaseBadge(phase = phase)
    if (burstVisible) {
      ConfettiBurst(
        colors = listOf(style.container, style.content) + extraBurstColors,
        intensity = intensity,
        durationMs = durationMs,
        burstPoint = badgeCenterInCell(anchor, cellWidth),
        horizontalBias = horizontalBias,
        modifier = Modifier.size(cellWidth, Dimens.PhaseBadgeCellHeight),
      )
    }
  }
}

/**
 * The canvas fractions of a [Dimens.BadgeSize]-wide badge's center inside a
 * [cellWidth]-wide cell, i.e. where a [ConfettiBurst] should originate.
 */
private fun badgeCenterInCell(anchor: BadgeCellAnchor, cellWidth: Dp): Offset {
  val centerX = when (anchor) {
    BadgeCellAnchor.Centered -> cellWidth.value / 2f
    BadgeCellAnchor.Start -> Dimens.BadgeSize.value / 2f
  }
  return Offset(x = centerX / cellWidth.value, y = 0.5f)
}