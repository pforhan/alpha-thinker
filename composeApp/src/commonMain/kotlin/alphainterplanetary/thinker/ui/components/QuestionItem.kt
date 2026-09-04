package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun QuestionItem(
  question: Question,
  filter: QuestionFilter,
  onAnswerClick: () -> Unit,
  onAskLater: () -> Unit,
  onIgnore: () -> Unit,
  onUnignore: () -> Unit,
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
        if (question.currentAnswer!!.isDraft) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.Edit,
              contentDescription = "Draft",
              modifier = Modifier.height(14.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Draft:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Text(
          text = question.currentAnswer!!.text.replace(Regex("\\s+"), " ").trim(),
          maxLines = 2,
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
          IconButton(onClick = onAskLater) {
            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Ask later")
          }
          IconButton(onClick = onIgnore) {
            Icon(Icons.Default.VisibilityOff, contentDescription = "Ignore")
          }
        } else if (filter == QuestionFilter.Answered || filter == QuestionFilter.Ignored) {
          IconButton(onClick = if (question.isIgnored) onUnignore else onIgnore) {
            Icon(
              if (question.isIgnored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
              contentDescription = if (question.isIgnored) "Unignore" else "Ignore"
            )
          }
        }
      }
    }
  }
}
