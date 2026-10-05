package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

/**
 * The app's one pill: a rounded fill behind a `labelSmall` label, optionally
 * preceded by a glyph.
 *
 * Three things render as a pill — the phase pill, the engine-status capability
 * pills, and the activity log's source chip — and each used to re-state the
 * shape, the type, and the padding, so a change to any of the three had to be
 * made three times and could be made twice. The colors stay with the callers,
 * since what a pill *means* is the caller's: a phase's own palette, a
 * capability's state, an error against a normal source.
 *
 * @param leading slot for a glyph in front of the label. Its presence also
 *   selects the padding: a pill with a glyph reads as padded by the glyph
 *   already, so it takes [Dimens.PillGlyphHorizontalPadding] rather than the
 *   text-only [Dimens.PillHorizontalPadding]. Two of these share one actions
 *   slot, which is what the tighter inset buys.
 * @param borderColor an outline color for a pill that is not filled. Defaults
 *   to transparent so a filled pill has no branch of its own.
 */
@Composable
fun Pill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  borderColor: Color = Color.Transparent,
  leading: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .clip(BadgeShape)
      .background(containerColor)
      .border(Dimens.OutlineStroke, borderColor, BadgeShape)
      .padding(
        horizontal = if (leading != null) {
          Dimens.PillGlyphHorizontalPadding
        } else {
          Dimens.PillHorizontalPadding
        },
        vertical = Dimens.PillVerticalPadding,
      ),
    horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leading != null) {
      leading()
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
    )
  }
}
