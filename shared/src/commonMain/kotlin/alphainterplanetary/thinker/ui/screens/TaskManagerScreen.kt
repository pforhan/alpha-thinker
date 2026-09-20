package alphainterplanetary.thinker.ui.screens

import alphainterplanetary.thinker.di.AppComponent
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.ui.format.durationText
import alphainterplanetary.thinker.ui.format.title
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.viewmodel.TaskManagerRow
import alphainterplanetary.thinker.ui.viewmodel.TaskManagerViewModel
import alphainterplanetary.thinker.util.now
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskManagerScreen(
  appComponent: AppComponent,
  onBack: () -> Unit,
) {
  val viewModel = remember {
    TaskManagerViewModel(
      appComponent.projectRepository,
      appComponent.taskRunner,
      appComponent.appScope,
    )
  }
  DisposableEffect(Unit) {
    onDispose { viewModel.close() }
  }

  val rows by viewModel.rows.collectAsState()
  val hasActiveTasks = rows.any { it.task.isActive }
  val at = rememberTickerNow(hasActiveTasks)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Task Manager") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { paddingValues ->
    if (rows.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("No generation tasks yet.")
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
      ) {
        items(rows, key = { it.task.id }) { row ->
          TaskManagerRowItem(row = row, at = at)
        }
      }
    }
  }
}

/** Re-emits the current time each second while any task is still active. */
@Composable
private fun rememberTickerNow(active: Boolean): Instant {
  var at by remember { mutableStateOf(now()) }
  LaunchedEffect(active) {
    while (active) {
      delay(1000)
      at = now()
    }
  }
  return at
}

@Composable
private fun TaskManagerRowItem(row: TaskManagerRow, at: Instant) {
  val task = row.task
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding),
  ) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = row.projectTitle,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (task.status == TaskStatus.Running) {
          Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
          CircularProgressIndicator(
            modifier = Modifier
              .width(Dimens.ProgressIndicatorSize)
              .height(Dimens.ProgressIndicatorSize),
            strokeWidth = Dimens.ProgressStroke,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.LabelChipGap))
        Text(
          text = task.status.title,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = task.kind.title,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = task.durationText(at) ?: "—",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}