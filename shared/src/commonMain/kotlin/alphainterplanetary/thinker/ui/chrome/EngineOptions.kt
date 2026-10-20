package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.ui.components.OptionHeader
import alphainterplanetary.thinker.ui.components.SelectionDot
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One selectable planning backend: names and describes the kind, marks the
 * selected one, and gates unavailable backends off (only offline is selectable
 * as a builtin today — the on-device backends land later). Remote has no card
 * of its own: it is configured per connection in [ConnectionsList].
 */
@Composable
internal fun EngineKindOption(
  mode: EngineKind,
  selected: Boolean,
  selectable: Boolean,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    enabled = selectable,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      OptionHeader(label = mode.label, description = mode.description) {
        SelectionDot(selected)
      }
      if (!selectable) {
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        Text(
          text = "Not available on this device yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
