package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A per-phase section header for the Answered / Ignored lists: the phase
 * pill plus its resolved count ("Phase 2: Research — 5 answered").
 */
@Composable
fun PhaseSectionHeader(
  phase: Phase,
  count: Int,
  countLabel: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimens.LabelChipGap),
  ) {
    PhasePill(phase = phase)
    Text(
      text = "$count $countLabel",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}