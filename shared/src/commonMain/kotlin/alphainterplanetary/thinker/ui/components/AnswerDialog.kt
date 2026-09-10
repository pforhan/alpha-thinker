package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

enum class AnswerDialogResult {
  Submitted,
  SavedDraft,
  DeletedAnswer,
  Unignored,
}

/**
 * Answer dialog built around a simple "text + completed" model:
 *
 * - The field is prefilled with the committed answer or the draft text.
 * - "Completed" is only ever set by the "Marked as complete" switch; edits
 *   never implicitly demote a committed answer or complete a draft.
 * - Submit and tapping off save with the same rules: blank text clears the
 *   draft or deletes the answer; text stores a draft (switch off) or a
 *   committed answer (switch on). Cancel discards any changes. Ignored
 *   questions are not editable and just dismiss.
 */
@Composable
fun AnswerDialog(
  question: Question,
  onDismiss: () -> Unit,
  onResult: (AnswerDialogResult, String) -> Unit,
) {
  val initialText = question.currentAnswer?.text ?: question.draftText ?: ""
  var answerText by remember { mutableStateOf(initialText) }
  var completed by remember { mutableStateOf(question.isAnswered) }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  fun submit() {
    val trimmed = answerText.trim()
    onResult(
      when {
        trimmed.isBlank() -> AnswerDialogResult.DeletedAnswer
        completed -> AnswerDialogResult.Submitted
        else -> AnswerDialogResult.SavedDraft
      },
      trimmed,
    )
  }

  fun close() {
    if (question.isIgnored) {
      onDismiss()
    } else {
      submit()
    }
  }

  AlertDialog(
    onDismissRequest = { close() },
    title = {
      ScrollableOverflowText(
        text = question.text,
        collapsedMaxLines = 4,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    text = {
      Column {
        if (question.isIgnored) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "This question is ignored.",
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = { onResult(AnswerDialogResult.Unignored, "") },
            ) {
              Text("Unignore")
            }
          }
          Spacer(modifier = Modifier.height(Dimens.SectionGap))
        }

        OutlinedTextField(
          value = answerText,
          onValueChange = { answerText = it },
          enabled = !question.isIgnored,
          label = { Text("Answer") },
          trailingIcon = {
            if (answerText.isNotEmpty()) {
              IconButton(onClick = { answerText = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear answer")
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
          minLines = 3,
          maxLines = 8,
        )

        if (!question.isIgnored) {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Switch(
              checked = completed,
              onCheckedChange = { completed = it },
            )
            Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
            Text(
              text = "Marked as complete",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        if (question.isAnswered && !question.isIgnored) {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          TextButton(
            onClick = { onResult(AnswerDialogResult.DeletedAnswer, "") },
          ) {
            Text(
              "Delete Answer",
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { submit() },
        enabled = !question.isIgnored,
      ) {
        Text("Submit")
      }
    },
    dismissButton = {
      TextButton(onClick = { onDismiss() }) {
        Text("Cancel")
      }
    },
  )
}