package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.ui.format.formatRelativeTime
import alphainterplanetary.thinker.ui.platform.toClipEntry
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboard
import kotlin.time.Clock
import kotlinx.coroutines.launch

@Composable
internal fun AnswerRevisionHistory(
  question: Question,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  currentEditorText: String,
  onUseRevision: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val history = question.revisionHistory()
  if (history.isEmpty()) return

  val now = remember(question.id, history.size, history.firstOrNull()?.id) { Clock.System.now() }
  val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onExpandedChange(!expanded) }
        .padding(vertical = Dimens.TightGap),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      Text(
        text = "History (${history.size})",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.weight(1f))
      Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = if (expanded) "Collapse history" else "Expand history",
        modifier = Modifier.rotate(chevronRotation),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (expanded) {
      history.forEach { answer ->
        RevisionCard(
          answer = answer,
          isCurrent = isCurrentSnapshot(question, answer),
          isInEditor = answer.text == currentEditorText,
          now = now,
          onUseRevision = onUseRevision,
        )
      }
    }
  }
}

@Composable
private fun RevisionCard(
  answer: Answer,
  isCurrent: Boolean,
  isInEditor: Boolean,
  now: kotlin.time.Instant,
  onUseRevision: (String) -> Unit,
) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (isInEditor) {
        MaterialTheme.colorScheme.surfaceContainerLowest
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Dimens.ContentGap),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = formatRelativeTime(answer.createdAt, now),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (isCurrent) {
            Text(
              text = "Saved",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        SelectionContainer {
          Text(
            text = answer.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
      if (isInEditor) {
        Text(
          text = "In editor",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
          modifier = Modifier.padding(horizontal = Dimens.ContentGap),
        )
      } else {
        TextButton(onClick = { onUseRevision(answer.text) }) {
          Text("Use")
        }
      }
      TextButton(
        onClick = {
          scope.launch {
            clipboard.setClipEntry(answer.text.toClipEntry())
          }
        },
      ) {
        Text("Copy")
      }
    }
  }
}

private fun isCurrentSnapshot(question: Question, answer: Answer): Boolean =
  answer.id == question.currentAnswer?.id || answer.id == question.currentDraft?.id
