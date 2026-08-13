package alphainterplanetary.thinker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.isAnswered

@Composable
fun QuestionItem(
    question: Question,
    filter: QuestionFilter,
    onAnswerClick: () -> Unit,
    onIgnore: () -> Unit,
    onUnignore: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onAnswerClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (question.currentAnswer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = question.currentAnswer!!.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (question.isIgnored) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ignored",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (filter == QuestionFilter.Unanswered && !question.isAnswered && !question.isIgnored) {
                    IconButton(onClick = onIgnore) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Ignore")
                    }
                } else if (filter == QuestionFilter.Answered || filter == QuestionFilter.Ignored) {
                    IconButton(onClick = onIgnore) {
                        Icon(
                            if (question.isIgnored) Icons.Default.Edit else Icons.Default.VisibilityOff,
                            contentDescription = if (question.isIgnored) "Unignore" else "Ignore"
                        )
                    }
                }
            }
        }
    }
}
