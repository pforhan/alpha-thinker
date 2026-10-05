package alphainterplanetary.thinker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import alphainterplanetary.thinker.ui.theme.Dimens

/**
 * The app's single indeterminate spinner, at the one size/stroke the design uses
 * for "something is in flight" markers (chips, the task bar, the task manager
 * rows). Material's default is already `colorScheme.primary`, so [color] only
 * exists for the rare surface that needs the indicator to read against a
 * different role.
 */
@Composable
fun TaskSpinner(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
) {
  CircularProgressIndicator(
    modifier = modifier.size(Dimens.ProgressIndicatorSize),
    strokeWidth = Dimens.ProgressStroke,
    color = color,
  )
}

/**
 * [TaskSpinner] followed by a label — the one "work in progress" row the app
 * renders. Callers vary the label's style/colour (a chip is a `labelSmall` on
 * `primary`; the task bar reads `bodyMedium` on its container) and its
 * truncation, but never its parts: spinner, [Dimens.IconLabelGap], text.
 *
 * @param label slot for the text, for the rare caller that needs to weight or
 *   align it inside the row (the task bar gives the label the row's width).
 */
@Composable
fun SpinnerLabel(
  text: String,
  modifier: Modifier = Modifier,
  textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
  textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  spinnerColor: Color = MaterialTheme.colorScheme.primary,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  label: @Composable () -> Unit = {
    Text(
      text = text,
      style = textStyle,
      color = textColor,
      maxLines = maxLines,
      overflow = overflow,
    )
  },
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TaskSpinner(color = spinnerColor)
    Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
    label()
  }
}
