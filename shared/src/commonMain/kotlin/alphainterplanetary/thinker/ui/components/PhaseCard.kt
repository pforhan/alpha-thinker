package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.PhaseStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * A tappable list card carrying its phase: the phase's container color as a
 * diluted wash over the card, plus a solid bar of the same color down the
 * leading edge.
 *
 * Every phase-tinted list row renders through here — the project list card and
 * the project detail question cards — so the tint, the bar, and the card's
 * screen inset are one definition rather than a chain each call site has to
 * keep in agreement. The row's inset is the one place the swipe background in
 * [SwipeableCard] mirrors, and both lists sit at the same offset for that reason.
 */
@Composable
fun PhaseCard(
  phase: Phase,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val style = PhaseStyles.forPhase(phase)
  Card(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(CardDefaults.shape)
        .background(PhaseStyles.rowTint(phase))
        .drawBehind {
          drawRect(
            color = style.container,
            topLeft = Offset.Zero,
            size = Size(Dimens.PhaseRowBarWidth.toPx(), size.height),
          )
        }
        .padding(Dimens.CardPadding),
    ) {
      content()
    }
  }
}