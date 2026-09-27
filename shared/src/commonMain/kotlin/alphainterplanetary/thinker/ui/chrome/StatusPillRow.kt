package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * The capability pills, in the stable slot order, with no tap target of their
 * own — each caller supplies the clickable that owns them (the header's
 * [StatusCluster], the flyout's Intelligence item).
 *
 * They are the one rendering of "what will this engine do" that appears in more
 * than one place, so they live here rather than inside the header: the flyout
 * showing its own copy of the same three states as text is exactly how a status
 * display starts disagreeing with itself.
 */
@Composable
internal fun StatusPillRow(
  status: EngineStatus,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(Dimens.StatusPillGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    status.slots.forEach { slot ->
      StatusPill(slot = slot)
    }
  }
}

/**
 * One capability's pill: its name on a container colored by state. A state the
 * mode does not have renders muted rather than disappearing, so the slots never
 * shift as the engine changes.
 */
@Composable
internal fun StatusPill(
  slot: CapabilityStatus,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(BadgeShape)
      .background(slot.state.containerColor())
      .padding(
        horizontal = Dimens.PillHorizontalPadding,
        vertical = Dimens.PillVerticalPadding,
      ),
  ) {
    Text(
      text = slot.capability.displayName(),
      style = MaterialTheme.typography.labelSmall,
      color = slot.state.onContainerColor(),
    )
  }
}
