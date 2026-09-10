package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.util.normalizeWhitespace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionItem(
  question: Question,
  view: QuestionViewMode,
  onAnswerClick: () -> Unit,
  onAskLater: (String) -> Unit,
  onIgnore: (String) -> Unit,
  onUnignore: (String) -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding)
      .clickable(onClick = onAnswerClick),
  ) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding)
    ) {
      Row(
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          text = question.text,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f),
        )
        val showMenu = when {
          view == QuestionViewMode.Unanswered && !question.isAnswered && !question.isIgnored -> true
          view == QuestionViewMode.Answered || view == QuestionViewMode.Draft || view == QuestionViewMode.Ignored -> true
          else -> false
        }
        if (showMenu) {
          Box {
            IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier.height(Dimens.IconSizeMedium),
            ) {
              Icon(
                Icons.Default.MoreVert,
                contentDescription = "Actions",
                modifier = Modifier.size(Dimens.IconSizeMedium),
              )
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false },
            ) {
              if (view == QuestionViewMode.Unanswered && !question.isAnswered && !question.isIgnored) {
                DropdownMenuItem(
                  text = { Text("Ask later") },
                  onClick = {
                    menuExpanded = false
                    onAskLater(question.id)
                  },
                )
                DropdownMenuItem(
                  text = { Text("Ignore") },
                  onClick = {
                    menuExpanded = false
                    onIgnore(question.id)
                  },
                )
              } else if (
                view == QuestionViewMode.Answered ||
                view == QuestionViewMode.Draft
              ) {
                DropdownMenuItem(
                  text = { Text("Ignore") },
                  onClick = {
                    menuExpanded = false
                    onIgnore(question.id)
                  },
                )
              } else if (view == QuestionViewMode.Ignored) {
                DropdownMenuItem(
                  text = { Text("Unignore") },
                  onClick = {
                    menuExpanded = false
                    onUnignore(question.id)
                  },
                )
              }
            }
          }
        }
      }

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

    }
  }
}
