package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

/**
 * The app's one empty or error state: a centered [message], optionally under an
 * [icon] and over an action button.
 *
 * Every screen's "nothing here" and "that failed" rendering is this — the
 * project list and its error, the project detail's error, the task manager, the
 * activity log — so the copy's prominence and the button's distance from it are
 * one decision rather than five that drift. `ProjectListEmpty` and
 * `ProjectListError` were byte-identical to one another; the rest differed only
 * in whether they carried a glyph.
 *
 * [message] is the whole message, not a title with a body: an empty list has one
 * thing to say, and splitting it would give the app a heading nobody else has.
 * The question list's "answered everything, pick a phase" state is deliberately
 * not one of these — it carries a celebration callout and a row of view links
 * beside the message, so it is its own layout that borrows the same
 * [Dimens.EmptyStateActionGap] rather than a caller of this one.
 *
 * @param modifier applied to the centering [Column], before the fill and the
 *   padding. Callers inside a scaffold pass its `paddingValues` here.
 */
@Composable
fun EmptyState(
  message: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(Dimens.EmptyStatePadding),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(Dimens.IconLabelGap))
    }
    Text(
      text = message,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (actionLabel != null && onAction != null) {
      Spacer(modifier = Modifier.height(Dimens.EmptyStateActionGap))
      Button(onClick = onAction) {
        Text(actionLabel)
      }
    }
  }
}
