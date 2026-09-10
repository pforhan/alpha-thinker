package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

/**
 * Text that spans up to [collapsedMaxLines] lines when it fits, but when the
 * content would overflow it collapses to the measured height and becomes
 * vertically scrollable with up/down tap controls.
 */
@Composable
fun ScrollableOverflowText(
  text: String,
  modifier: Modifier = Modifier,
  collapsedMaxLines: Int,
  style: TextStyle = LocalTextStyle.current,
  color: Color = Color.Unspecified,
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  var scrollableHeight by remember { mutableStateOf(Dp.Unspecified) }
  val scrollState = rememberScrollState()
  val scrollable = scrollableHeight != Dp.Unspecified
  val pagePx = with(density) { scrollableHeight.toPx() }
  val hasContentAbove by remember { derivedStateOf { scrollState.value > 0 } }
  val hasContentBelow by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .then(if (scrollable) Modifier.heightIn(max = scrollableHeight) else Modifier),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = text,
        style = style,
        color = color,
        maxLines = if (scrollable) Int.MAX_VALUE else collapsedMaxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
          if (result.hasVisualOverflow) {
            with(density) { scrollableHeight = result.size.height.toDp() }
          }
        },
        modifier = Modifier
          .weight(1f)
          .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
      )
      if (scrollable) {
        Column(
          verticalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier
            .fillMaxHeight()
            .width(Dimens.ScrollControlSize),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(Dimens.ScrollControlSize)
              .align(Alignment.CenterHorizontally)
              .clickable(enabled = hasContentAbove) {
                scope.launch { scrollState.animateScrollBy(-pagePx) }
              },
          ) {
            if (hasContentAbove) {
              ScrollHintIcon(Icons.Default.KeyboardArrowUp)
            }
          }
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(Dimens.ScrollControlSize)
              .align(Alignment.CenterHorizontally)
              .clickable(enabled = hasContentBelow) {
                scope.launch { scrollState.animateScrollBy(pagePx) }
              },
          ) {
            if (hasContentBelow) {
              ScrollHintIcon(Icons.Default.KeyboardArrowDown)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BoxScope.ScrollHintIcon(icon: ImageVector) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    modifier = Modifier.size(Dimens.IconSizeSmall),
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}