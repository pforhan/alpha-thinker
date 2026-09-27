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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.DialogProperties

enum class AnswerDialogResult {
  Submitted,
  SavedDraft,
  DeletedAnswer,
  Unignored,
}

/**
 * Answer dialog built around a simple "text + a choice of where it lands" model.
 *
 * - The field is prefilled with the committed answer or the draft text.
 * - "Save Draft" and "Save Answer" are the whole decision, spelled out as the
 *   button you press. There is no switch to remember to flip, so an edit can't
 *   quietly land in the state opposite the one you meant.
 * - Blank text is a clear either way: it drops the draft, or unpoints a
 *   committed answer, which is also what "Delete Answer" does.
 * - The two save buttons are the only paths that write. Tapping outside does
 *   nothing at all, and Cancel (or ESC) discards, so a stray gesture can
 *   neither commit an edit nor wipe an existing answer.
 * - An ignored question is read-only: the field is disabled, since there's no
 *   point writing a new answer for a question you skipped. What it already has
 *   is still yours to clear, though — "Delete Answer" shows whenever there's a
 *   committed answer or a draft, ignored or not.
 * - The question's phase is always shown as a pill below the question text,
 *   above the answer field.
 */
@Composable
fun AnswerDialog(
  question: Question,
  phase: Phase,
  onDismiss: () -> Unit,
  onResult: (AnswerDialogResult, String) -> Unit,
) {
  val initialText = question.currentAnswer?.text ?: question.draftText ?: ""
  var answerText by remember { mutableStateOf(initialText) }
  val focusRequester = remember { FocusRequester() }

  // Anything to clear: a committed answer, a draft, or — ignoring a question
  // doesn't freeze it — both. Deliberately not gated on the question being
  // active, since the point of the button is to undo what a question carries.
  val hasAnswer = question.currentAnswer != null || !question.draftText.isNullOrBlank()

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  fun save(completed: Boolean) {
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

  AlertDialog(
    // The two save buttons are the only things that write. Tapping outside does
    // nothing at all, and the dismiss request that still gets through (ESC on
    // desktop) is a plain discard rather than a save, so an answer can neither
    // be committed nor wiped by a stray gesture.
    onDismissRequest = { onDismiss() },
    properties = DialogProperties(dismissOnClickOutside = false),
    title = {
      Column {
        ScrollableOverflowText(
          text = question.text,
          collapsedMaxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )
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
            TextButton(
              onClick = { onResult(AnswerDialogResult.Unignored, "") },
            ) {
              Text("Unignore")
            }
          }
          Spacer(modifier = Modifier.height(Dimens.SectionGap))
        }

        Box {
          OutlinedTextField(
            value = answerText,
            onValueChange = { answerText = it },
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
              onClick = { answerText = "" },
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                  top = Dimens.FieldOverlayInsetTop,
                  end = Dimens.FieldOverlayInsetEnd,
                )
            ) {
              Icon(Icons.Default.Clear, contentDescription = "Clear answer")
            }
          }
        }

        if (hasAnswer) {
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
      Row(horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap)) {
        TextButton(
          onClick = { save(completed = false) },
          enabled = !question.isIgnored,
        ) {
          Text("Save Draft")
        }
        Button(
          onClick = { save(completed = true) },
          enabled = !question.isIgnored,
        ) {
          Text("Save Answer")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = { onDismiss() }) {
        Text("Cancel")
      }
    },
  )
}