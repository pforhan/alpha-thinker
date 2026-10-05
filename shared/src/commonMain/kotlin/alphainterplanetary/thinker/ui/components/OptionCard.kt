package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

/**
 * The header row an option card is built around: a label over its description,
 * with one control or marker on the trailing edge.
 *
 * The engine picker, the phase-color picker, and the testing delay card all open
 * with this row, so the type scale and the gap in front of the trailing control
 * are one decision rather than three that have to be kept equal by hand. The
 * card itself stays at each call site — only two of the three are clickable, and
 * only two mark a selection — so a shared card would mean two nullable params
 * for the differences rather than for anything the rows have in common.
 */
@Composable
internal fun OptionHeader(
  label: String,
  description: String,
  trailing: @Composable RowScope.() -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
      )
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
    trailing()
  }
}

/**
 * The marker on the selected option in a card of [OptionHeader]s.
 *
 * An unselected option draws the same marker in the outline color, so the eye
 * compares two identical shapes rather than a marker against its absence.
 */
@Composable
internal fun SelectionDot(selected: Boolean) {
  Box(
    modifier = Modifier
      .size(Dimens.SelectionDotSize)
      .clip(BadgeShape)
      .background(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
      ),
  )
}