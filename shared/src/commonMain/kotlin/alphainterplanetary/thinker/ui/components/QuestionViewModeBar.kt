package alphainterplanetary.thinker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QuestionViewModeBar(
  selectedView: QuestionViewMode,
  onViewSelected: (QuestionViewMode) -> Unit,
  shuffleEnabled: Boolean = false,
  onShuffle: () -> Unit = {},
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text("Questions:", style = MaterialTheme.typography.titleMedium)

    Row {
      QuestionViewMode.values().forEach { mode ->
        FilterChip(
          selected = selectedView == mode,
          onClick = { onViewSelected(mode) },
          label = { Text(mode.displayName) }
        )
        Spacer(modifier = Modifier.width(8.dp))
      }
      if (shuffleEnabled) {
        IconButton(onClick = onShuffle) {
          Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
        }
      }
    }
  }
}
