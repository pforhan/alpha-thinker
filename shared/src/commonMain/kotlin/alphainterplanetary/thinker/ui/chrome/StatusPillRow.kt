package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.components.Pill
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The capability pills, in the stable slot order, with no tap target of their
 * own — each caller supplies the clickable that owns them (the header's
 * [StatusCluster], the flyout's Intelligence item).
 *
 * They are the one rendering of "what will this engine do" that appears in more
 * than one place, so they live here rather than inside the header: the flyout
 * showing its own copy of the same three states as text is exactly how a status
 * display starts disagreeing with itself.
 *
 * The row announces itself as one sentence (see [EngineStatus.summary]). The
 * glyphs are shapes, not words, so without this a screen reader would read the
 * pills as a bare "Network, LLM, Tools" and never hear whether any of them is
 * in use; folding it in here also spares every caller from repeating it.
 */
@Composable
internal fun StatusPillRow(
  status: EngineStatus,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.semantics(mergeDescendants = true) {
      contentDescription = status.summary()
    },
    horizontalArrangement = Arrangement.spacedBy(Dimens.StatusPillGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    status.slots.forEach { slot ->
      StatusPill(slot = slot)
    }
  }
}

/**
 * One capability's pill: its name on a fill colored by state, behind the glyph
 * that says the same thing without relying on color. A state the mode does not
 * have renders outlined rather than disappearing, so the slots never shift as
 * the engine changes.
 */
@Composable
internal fun StatusPill(
  slot: CapabilityStatus,
  modifier: Modifier = Modifier,
) {
  val content = slot.state.onContainerColor()
  Pill(
    text = slot.capability.displayName(),
    containerColor = slot.state.containerColor(),
    contentColor = content,
    // Transparent for a filled state, so the outline is a single call site
    // rather than a branch in every state that draws a pill.
    borderColor = slot.state.borderColor(),
    modifier = modifier,
  ) {
    Icon(
      imageVector = slot.state.glyph(),
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(Dimens.IconSizeSmall),
    )
  }
}
