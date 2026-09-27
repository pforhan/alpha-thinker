package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.activitylog.ActivityRecord
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The status detail behind the header's status cluster: the engine that will
 * run, what it will and will not do, and how the last activity went.
 *
 * It is read-only on purpose, and it is also the only route to it: the flyout's
 * Intelligence item and the header's cluster both point here rather than each
 * rendering the status themselves. The one control that can change any of this —
 * the engine picker — lives in the [ChromeSheet.Intelligence] sheet, one row away,
 * so no second surface grows a switch this one lacks.
 *
 * The last-activity row is the one thing here that navigates, and it leaves
 * rather than opens: the Activity Log is a full screen, so it replaces the sheet
 * rather than stacking on it — leaving this sheet up over a screen that is no
 * longer underneath it would strand it.
 */
@Composable
internal fun StatusSheetContent(
  status: EngineStatus,
  latestActivity: ActivityRecord?,
  onOpenIntelligence: () -> Unit,
  onOpenActivityLog: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    Text(
      text = status.mode.label,
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = status.mode.description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    CapabilityList(status = status)

    if (latestActivity != null) {
      HorizontalDivider()
      LastActivityRow(
        latestActivity = latestActivity,
        onClick = onOpenActivityLog,
      )
    }

    HorizontalDivider()
    SheetLinkRow(
      title = "Change engine…",
      onClick = onOpenIntelligence,
    )
  }
}

/**
 * The last activity as a way into the Activity Log: the sentence the sheet
 * already showed, now tappable, with the same chevron the other rows carry so it
 * reads as a destination rather than a label.
 *
 * It keeps the wrapped sentence instead of becoming a [SheetLinkRow] with the
 * summary as its trailing value, because a failure headline is the one text
 * here that carries its explanation ("Initial question generation failed: …")
 * and a link row's value is single-line by construction — the part of the sheet
 * that explains a failure would be the part that got truncated. The tint
 * therefore stays on the whole sentence rather than on a value slot.
 */
@Composable
private fun LastActivityRow(
  latestActivity: ActivityRecord,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Dimens.ActionRowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      // The one place the header says how the last run *went* rather than what
      // is configured, so a fully set-up mode that still failed is not hidden.
      text = "Last activity: ${latestActivity.summary}",
      style = MaterialTheme.typography.bodyMedium,
      color = if (latestActivity.hasError) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * The sheet's capability rows, as one measured unit: the pill column is reserved
 * as wide as the broadest pill, so every value starts on the same rule, and the
 * value column takes whatever is left.
 *
 * The shared width is the reason this is a [SubcomposeLayout] rather than three
 * rows in a `Column`. Measured independently, each row would size the column to
 * *its own* pill — "LLM" is much narrower than "Network" — and the values would
 * start at three different x positions, which is the raggedness the columns
 * exist to remove. Same measure-then-lay-out shape as [StatusCluster] and
 * `QuestionViewModeBar`, which need a width before they can lay out.
 */
@Composable
private fun CapabilityList(
  status: EngineStatus,
  modifier: Modifier = Modifier,
) {
  SubcomposeLayout(modifier = modifier) { constraints ->
    // Measured unconstrained first: each pill is subcomposed only to learn the
    // width it wants, which is the width every pill is then drawn at.
    val pillWidth = status.slots
      .map { slot ->
        val pill = subcompose("pill-${slot.capability}") { StatusPill(slot = slot) }
        pill.first().measure(Constraints()).width
      }
      .max()
    val pillWidthDp = pillWidth.toDp()
    val gap = Dimens.SectionGap.roundToPx()

    val rowConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val rows = status.slots.map { slot ->
      val row = subcompose("row-${slot.capability}") {
        CapabilityRow(slot = slot, pillWidth = pillWidthDp)
      }
      row.first().measure(rowConstraints)
    }

    // Each row was measured with these constraints already, so clamping the
    // totals against them is all the fitting this needs.
    val width = rows.maxOf { it.width }
      .coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (rows.sumOf { it.height } + gap * (rows.size - 1))
      .coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(width, height) {
      var y = 0
      rows.forEach { row ->
        row.place(0, y)
        y += row.height + gap
      }
    }
  }
}

/**
 * One capability in two columns: the header's own pill for its name and state,
 * and its configured value (the endpoint, the model) filling the rest of the row.
 *
 * The row renders [StatusPill] rather than a second, wider version of it, so
 * the sheet and the header cannot drift apart — a capability that looks live in
 * one and gray in the other is the exact problem this item exists to prevent.
 * The value sits off the pill, on the sheet's own surface, because it is not
 * part of the capability's state: only a fill's own ink is contrast-checked
 * against it, so `onSurfaceVariant` is safe here and not on the accent's
 * container.
 *
 * [pillWidth] reserves the shared column the values align to, but the pill
 * inside it keeps its own width and is pushed to the column's right edge. The
 * pills therefore do not match each other and their left edges are ragged — the
 * alternative, stretching every pill to the column, makes a chip that means
 * "in use" as wide as the longest capability name and reads as a bar rather than
 * a flag. The ragged edge is the cheaper trade: the values below them are what
 * the eye scans down.
 *
 * There is also no prose for the state. [CapabilityStatus.displayDetail] already
 * says "Not used" or "Not set up" whenever there is no value to show, so a
 * subtitle repeating it was two renderings of one fact, and the states it did
 * not repeat are exactly the ones the pill's fill and glyph already carry. The
 * row merges its semantics into the same sentence [StatusPillRow] announces, so
 * the state is still spoken.
 */
@Composable
private fun CapabilityRow(
  slot: CapabilityStatus,
  pillWidth: Dp,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .semantics(mergeDescendants = true) {
        contentDescription = slot.sentence()
      },
    horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.width(pillWidth),
      contentAlignment = Alignment.CenterEnd,
    ) {
      StatusPill(slot = slot)
    }
    Text(
      text = slot.displayDetail(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}

/** A row that opens another sheet: title, optional current value, chevron. */
@Composable
internal fun SheetLinkRow(
  title: String,
  value: String? = null,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Dimens.ActionRowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    if (value != null) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
