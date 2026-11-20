package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
 * Text that either collapses to [collapsedMaxLines] with an internal scroll
 * viewport when it would overflow, or — when [fillViewportHeight] is true —
 * fills the available height and scrolls within that viewport on overflow. In
 * both cases, when scrollable, up/down tap controls indicate and navigate hidden
 * content.
 */
@Composable
fun ScrollableOverflowText(
  text: String,
  modifier: Modifier = Modifier,
  collapsedMaxLines: Int = Int.MAX_VALUE,
  fillViewportHeight: Boolean = false,
  style: TextStyle = LocalTextStyle.current,
  color: Color = Color.Unspecified,
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .then(if (fillViewportHeight) Modifier.fillMaxHeight() else Modifier),
  ) {
    val viewportMaxHeight = maxHeight
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var lineCollapsedHeight by remember(text, collapsedMaxLines) { mutableStateOf(Dp.Unspecified) }
    var viewportOverflow by remember(text, fillViewportHeight, viewportMaxHeight) { mutableStateOf(false) }

    val boundedViewport = viewportMaxHeight != Dp.Infinity && viewportMaxHeight.value.isFinite()
    val scrollable = if (fillViewportHeight) {
      viewportOverflow && boundedViewport
    } else {
      lineCollapsedHeight != Dp.Unspecified
    }
    val viewportHeight = when {
      !scrollable -> Dp.Unspecified
      fillViewportHeight -> viewportMaxHeight
      else -> lineCollapsedHeight
    }
    val pagePx = with(density) {
      if (viewportHeight != Dp.Unspecified) viewportHeight.toPx() else 0f
    }
    val hasContentAbove by remember { derivedStateOf { scrollState.value > 0 } }
    val hasContentBelow by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (fillViewportHeight) Modifier.fillMaxHeight() else Modifier)
        .then(
          if (scrollable && viewportHeight != Dp.Unspecified) {
            Modifier.heightIn(max = viewportHeight)
          } else {
            Modifier
          },
        ),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (fillViewportHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
      ) {
        Text(
          text = text,
          style = style,
          color = color,
          maxLines = when {
            scrollable -> Int.MAX_VALUE
            fillViewportHeight -> Int.MAX_VALUE
            else -> collapsedMaxLines
          },
          overflow = TextOverflow.Clip,
          onTextLayout = { result ->
            if (fillViewportHeight) {
              if (boundedViewport && with(density) { result.size.height.toDp() > viewportMaxHeight }) {
                viewportOverflow = true
              }
            } else if (collapsedMaxLines != Int.MAX_VALUE && result.hasVisualOverflow) {
              with(density) { lineCollapsedHeight = result.size.height.toDp() }
            }
          },
          modifier = Modifier
            .weight(1f)
            .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
            .then(if (fillViewportHeight) Modifier.fillMaxHeight() else Modifier),
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
}

@Composable
private fun ScrollHintIcon(icon: ImageVector) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    modifier = Modifier.size(Dimens.IconSizeSmall),
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}