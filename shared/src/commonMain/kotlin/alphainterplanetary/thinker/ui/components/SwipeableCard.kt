package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.LocalExtendedColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCard(
  state: SwipeToDismissBoxState,
  startAction: SwipeAction?,
  endAction: SwipeAction?,
  onSwipeStart: (() -> Unit)?,
  onSwipeEnd: (() -> Unit)?,
  settleAfterDismiss: Boolean = true,
  resetScope: CoroutineScope,
  content: @Composable () -> Unit,
) {
  SwipeToDismissBox(
    state = state,
    enableDismissFromStartToEnd = startAction != null,
    enableDismissFromEndToStart = endAction != null,
    onDismiss = {
      when (it) {
        SwipeToDismissBoxValue.StartToEnd -> onSwipeStart?.invoke()
        SwipeToDismissBoxValue.EndToStart -> onSwipeEnd?.invoke()
        SwipeToDismissBoxValue.Settled -> Unit
      }
      if (settleAfterDismiss) {
        resetScope.launch { state.reset() }
      }
    },
    backgroundContent = {
      val action = when (state.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> startAction
        SwipeToDismissBoxValue.EndToStart -> endAction
        SwipeToDismissBoxValue.Settled -> null
      }
      if (action != null) {
        val alignment = when (state.dismissDirection) {
          SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
          SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
          SwipeToDismissBoxValue.Settled -> Alignment.Center
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding)
            .clip(CardDefaults.shape)
            .background(action.background()),
          contentAlignment = alignment,
        ) {
          val iconAtEdge = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
          SwipeActionRow(action = action, iconAtEdge = iconAtEdge)
        }
      }
    },
  ) {
    content()
  }
}

@Composable
private fun SwipeActionRow(action: SwipeAction, iconAtEdge: Boolean) {
  val contentColor = LocalExtendedColors.current.swipeActionContent
  Row(
    modifier = Modifier.padding(horizontal = Dimens.SwipeActionPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimens.IconLabelGap),
  ) {
    if (iconAtEdge) {
      Text(
        action.label,
        style = MaterialTheme.typography.titleMedium,
        color = contentColor,
      )
      Icon(
        action.icon,
        contentDescription = null,
        tint = contentColor,
      )
    } else {
      Icon(
        action.icon,
        contentDescription = null,
        tint = contentColor,
      )
      Text(
        action.label,
        style = MaterialTheme.typography.titleMedium,
        color = contentColor,
      )
    }
  }
}

@Composable
private fun SwipeAction.background(): Color {
  val extendedColors = LocalExtendedColors.current
  return when (style) {
    SwipeActionStyle.AskLater -> extendedColors.swipeAskLater
    SwipeActionStyle.Ignore -> extendedColors.swipeIgnore
    SwipeActionStyle.Delete -> extendedColors.swipeDelete
    SwipeActionStyle.Unignore -> extendedColors.swipeUnignore
  }
}