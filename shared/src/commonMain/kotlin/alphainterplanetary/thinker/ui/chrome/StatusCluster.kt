package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * The header's read-only engine status: one pill per capability in a fixed slot
 * order, so switching engines recolors the row instead of reflowing it, and an
 * unused capability renders muted rather than disappearing.
 *
 * When the pills would not fit alongside a usable title — a long project title
 * on a narrow bar — the whole cluster collapses to a single status button
 * carrying a dot that summarizes the same [status], so the title is never
 * squeezed to nothing. Both forms open the Status sheet via [onClick].
 *
 * The state is a projection of the selected engine, never a probe of the device
 * (see [engineStatus]), which is why there is no switch here.
 */
@Composable
fun StatusCluster(
  status: EngineStatus,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  showHint: Boolean = false,
  onDismissHint: () -> Unit = {},
) {
  Box(modifier = modifier) {
    SubcomposeLayout { constraints ->
      val reserve = Dimens.HeaderTitleReserve.roundToPx()

      // Measured unconstrained first: the wide form is only worth subcomposing if
      // it would actually fit.
      val pillsWidth = subcompose("pills") { StatusPillRow(status = status) }
        .first()
        .measure(Constraints())
        .width

      val wide = pillsWidth + reserve <= constraints.maxWidth
      val placeable = subcompose(if (wide) "wide" else "compact") {
        if (wide) {
          // One tap target for the whole row, described as one sentence by
          // [StatusPillRow] itself: three separately labeled pills would read as
          // three unrelated toggles.
          StatusPillRow(
            status = status,
            modifier = Modifier.clickable(onClick = onClick),
          )
        } else {
          CompactStatusButton(status = status, onClick = onClick)
        }
      }.first().measure(constraints)

      layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
      }
    }

    if (showHint) {
      EngineStatusHint(onDismiss = onDismissHint)
    }
  }
}

@Composable
private fun EngineStatusHint(onDismiss: () -> Unit) {
  Popup(
    alignment = Alignment.TopEnd,
    offset = IntOffset(x = 0, y = 56),
    properties = PopupProperties(focusable = false),
    onDismissRequest = onDismiss,
  ) {
    Column(horizontalAlignment = Alignment.End) {
      // A small pointer aimed up toward the status cluster in the app bar.
      Box(
        modifier = Modifier
          .padding(end = 20.dp)
          .size(width = 16.dp, height = 8.dp)
          .clip(TriangleUpShape)
          .background(MaterialTheme.colorScheme.inverseSurface),
      )
      Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 280.dp),
      ) {
        Column(
          modifier = Modifier.padding(Dimens.CardPadding),
        ) {
          Text(
            text = "This shows how your data moves",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "The engine status tells you whether planning stays on this " +
              "device or goes to a connection. Tap it anytime to review or " +
              "change your engine.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Dimens.TightGap),
          )
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
          ) {
            Text("Got it")
          }
        }
      }
    }
  }
}

private val TriangleUpShape = GenericShape { size, _ ->
  moveTo(size.width / 2f, 0f)
  lineTo(size.width, size.height)
  lineTo(0f, size.height)
  close()
}

@Composable
private fun CompactStatusButton(
  status: EngineStatus,
  onClick: () -> Unit,
) {
  Box(contentAlignment = Alignment.Center) {
    IconButton(onClick = onClick) {
      Icon(
        imageVector = Icons.Filled.GraphicEq,
        contentDescription = status.summary(),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    // The dot carries the collapsed row's state: a muted one means nothing is in
    // use, a filled one means capabilities are live, and error means a selected
    // capability is not set up.
    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(
          end = Dimens.StatusDotInset,
          top = Dimens.StatusDotInset,
        )
        .size(Dimens.StatusDotSize)
        .clip(CircleShape)
        .background(status.dotColor())
    )
  }
}
