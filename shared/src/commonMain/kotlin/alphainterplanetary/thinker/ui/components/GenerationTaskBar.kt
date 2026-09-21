package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.tasks.GenerationTask
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.ui.format.progressLabel
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * A floating bar pinned to the bottom of a screen while any generation task
 * is active: an indeterminate spinner, a summary of what is being produced,
 * and a tap-through to the Task Manager. Mounted once per navigation root so
 * every screen sees it (see StatefulNavApp / NavGraph).
 */
@Composable
fun GenerationTaskBar(
  taskRunner: TaskRunner,
  onTaskManagerClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tasks by taskRunner.tasks.collectAsState()
  val label = activeTaskSummary(tasks)
  if (label == null) return

  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.large,
    shadowElevation = Dimens.TaskBarShadow,
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onTaskManagerClick),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = Dimens.ScreenPadding,
          vertical = Dimens.ActionRowVerticalPadding,
        ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircularProgressIndicator(
        modifier = Modifier
          .width(Dimens.ProgressIndicatorSize)
          .height(Dimens.ProgressIndicatorSize),
        strokeWidth = Dimens.ProgressStroke,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
      Icon(
        Icons.Default.Sync,
        contentDescription = "Task Manager",
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

/** Single-line summary of the running tasks, or null when nothing is active. */
internal fun activeTaskSummary(tasks: List<GenerationTask>): String? {
  val active = tasks.filter { it.isActive }
  return when {
    active.isEmpty() -> null
    active.size == 1 -> "${active.single().kind.progressLabel}…"
    else -> "${active.size} tasks running…"
  }
}