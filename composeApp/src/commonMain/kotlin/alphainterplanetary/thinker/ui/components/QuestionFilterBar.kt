package alphainterplanetary.thinker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QuestionFilterBar(
    selectedFilter: QuestionFilter,
    onFilterSelected: (QuestionFilter) -> Unit,
    shuffleEnabled: Boolean = false,
    onShuffle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Questions:", style = MaterialTheme.typography.titleMedium)
        
        Row {
            QuestionFilter.values().forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.displayName) }
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
