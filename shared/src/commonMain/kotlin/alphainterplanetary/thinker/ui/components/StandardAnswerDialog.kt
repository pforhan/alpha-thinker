package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun StandardAnswerDialog(
  question: Question,
  phase: Phase,
  answerText: String,
  onAnswerTextChange: (String) -> Unit,
  questionText: String,
  onQuestionTextChange: (String) -> Unit,
  isEditingQuestion: Boolean,
  onStartEditingQuestion: () -> Unit,
  hasAnswer: Boolean,
  focusRequester: FocusRequester,
  onDismiss: () -> Unit,
  onUnignore: () -> Unit,
  onDelete: () -> Unit,
  onResearch: () -> Unit,
  onSaveDraft: () -> Unit,
  onSaveAnswer: () -> Unit,
) {
  AlertDialog(
    // The two save buttons are the only things that write. Tapping outside does
    // nothing at all, and the dismiss request that still gets through (ESC on
    // desktop) is a plain discard rather than a save, so an answer can neither
    // be committed nor wiped by a stray gesture.
    onDismissRequest = onDismiss,
    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnClickOutside = false,
    ),
    title = {
      Column {
        if (isEditingQuestion) {
          OutlinedTextField(
            value = questionText,
            onValueChange = onQuestionTextChange,
            label = { Text("Question") },
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
          ) {
            ScrollableOverflowText(
              text = question.text,
              collapsedMaxLines = 4,
              fillWidth = false,
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = onStartEditingQuestion) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit question",
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        PhasePill(phase = phase)
      }
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
            TextButton(onClick = onUnignore) {
              Text("Unignore")
            }
          }
          Spacer(modifier = Modifier.height(Dimens.SectionGap))
        }

        Box {
          OutlinedTextField(
            value = answerText,
            onValueChange = onAnswerTextChange,
            enabled = !question.isIgnored,
            label = { Text("Answer") },
            modifier = Modifier
              .fillMaxWidth()
              .focusRequester(focusRequester),
            minLines = 3,
            maxLines = 8,
          )

          if (answerText.isNotEmpty() && !question.isIgnored) {
            IconButton(
              onClick = { onAnswerTextChange("") },
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                  top = Dimens.FieldOverlayInsetTop,
                  end = Dimens.FieldOverlayInsetEnd,
                ),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
              ),
            ) {
              Icon(Icons.Default.Clear, contentDescription = "Clear answer")
            }
          }
        }

        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onResearch) {
            Text("Research")
          }
        }

        if (hasAnswer) {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          TextButton(onClick = onDelete) {
            Text(
              "Delete Answer",
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap)) {
        TextButton(
          onClick = onSaveDraft,
          enabled = !question.isIgnored,
        ) {
          Text("Save Draft")
        }
        Button(
          onClick = onSaveAnswer,
          enabled = !question.isIgnored,
        ) {
          Text("Save Answer")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
