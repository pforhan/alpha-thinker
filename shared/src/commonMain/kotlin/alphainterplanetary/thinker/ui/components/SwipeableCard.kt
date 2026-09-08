package alphainterplanetary.thinker.ui.components

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()

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
        scope.launch { state.reset() }
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
            .padding(horizontal = 16.dp)
            .clip(CardDefaults.shape)
            .background(action.background),
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
  Row(
    modifier = Modifier.padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (iconAtEdge) {
      Text(
        action.label,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
      )
      Icon(
        action.icon,
        contentDescription = null,
        tint = Color.White,
      )
    } else {
      Icon(
        action.icon,
        contentDescription = null,
        tint = Color.White,
      )
      Text(
        action.label,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
      )
    }
  }
}