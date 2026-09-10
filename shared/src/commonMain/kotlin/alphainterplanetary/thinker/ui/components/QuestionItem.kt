package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.util.normalizeWhitespace
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionItem(
  question: Question,
  view: QuestionViewMode,
  dismissState: SwipeToDismissBoxState,
  onAnswerClick: () -> Unit,
) {
  val scope = rememberCoroutineScope()

  fun swipeTo(target: SwipeToDismissBoxValue) {
    scope.launch { dismissState.dismiss(target) }
  }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding),
    onClick = onAnswerClick
  ) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding)
    ) {
      Text(
        text = question.text,
        style = MaterialTheme.typography.bodyLarge
      )

      if (question.currentAnswer != null) {
        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Text(
          text = question.currentAnswer!!.text.normalizeWhitespace(),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else if (question.isDraft) {
        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Edit,
            contentDescription = "Draft",
            modifier = Modifier.height(Dimens.IconSizeSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.width(Dimens.TightGap))
          Text(
            text = "Draft:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = question.draftText!!.normalizeWhitespace(),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else if (question.isIgnored) {
        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Text(
          text = "Ignored",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
      }

      Spacer(modifier = Modifier.height(Dimens.ContentGap))

      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
      ) {
        if (view == QuestionViewMode.Unanswered && !question.isAnswered && !question.isIgnored) {
          IconButton(onClick = { swipeTo(SwipeToDismissBoxValue.StartToEnd) }) {
            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Ask later")
          }
          IconButton(onClick = { swipeTo(SwipeToDismissBoxValue.EndToStart) }) {
            Icon(Icons.Default.VisibilityOff, contentDescription = "Ignore")
          }
        } else if (
          view == QuestionViewMode.Answered ||
          view == QuestionViewMode.Draft ||
          view == QuestionViewMode.Ignored
        ) {
          IconButton(onClick = { swipeTo(SwipeToDismissBoxValue.StartToEnd) }) {
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
