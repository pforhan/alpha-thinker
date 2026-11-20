package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

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
 * - "Research" performs a lightweight browser handoff: review/edit search terms
 *   and launch the query in the system browser. No in-app search/fetch/extraction
 *   or automatic draft fill.
 */
@Composable
fun AnswerDialog(
  question: Question,
  phase: Phase,
  onDismiss: () -> Unit,
  onResearch: (String) -> Unit,
  onResult: (AnswerDialogResult, String) -> Unit,
) {
  val initialText = question.currentAnswer?.text ?: question.draftText ?: ""
  var answerText by remember { mutableStateOf(initialText) }
  var showResearchTerms by remember { mutableStateOf(false) }
  var researchTerms by remember { mutableStateOf(question.text) }
  val focusRequester = remember { FocusRequester() }
  val uriHandler = LocalUriHandler.current

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

  fun launchResearch(terms: String) {
    val trimmedTerms = terms.trim().ifBlank { question.text }
    onResearch(trimmedTerms)
    uriHandler.openUri(searchUrlFor(trimmedTerms))
  }

  BoxWithConstraints {
    val compactLandscape = maxWidth > maxHeight && maxHeight < 500.dp

    if (compactLandscape) {
      CompactLandscapeAnswerDialog(
        question = question,
        phase = phase,
        answerText = answerText,
        onAnswerTextChange = { answerText = it },
        hasAnswer = hasAnswer,
        focusRequester = focusRequester,
        maxDialogHeight = maxHeight,
        onDismiss = onDismiss,
        onUnignore = { onResult(AnswerDialogResult.Unignored, "") },
        onDelete = { onResult(AnswerDialogResult.DeletedAnswer, "") },
        onResearch = { showResearchTerms = true },
        onSaveDraft = { save(completed = false) },
        onSaveAnswer = { save(completed = true) },
      )
    } else {
      StandardAnswerDialog(
        question = question,
        phase = phase,
        answerText = answerText,
        onAnswerTextChange = { answerText = it },
        hasAnswer = hasAnswer,
        focusRequester = focusRequester,
        onDismiss = onDismiss,
        onUnignore = { onResult(AnswerDialogResult.Unignored, "") },
        onDelete = { onResult(AnswerDialogResult.DeletedAnswer, "") },
        onResearch = { showResearchTerms = true },
        onSaveDraft = { save(completed = false) },
        onSaveAnswer = { save(completed = true) },
      )
    }
  }

  if (showResearchTerms) {
    AlertDialog(
      onDismissRequest = { showResearchTerms = false },
      title = { Text("Research") },
      text = {
        Column {
          Text(
            text = "Review or edit the search terms, then open them in your browser to research this question.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          OutlinedTextField(
            value = researchTerms,
            onValueChange = { researchTerms = it },
            label = { Text("Search terms") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showResearchTerms = false
            launchResearch(researchTerms)
          },
        ) {
          Text("Open in browser")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResearchTerms = false }) {
          Text("Cancel")
        }
      },
    )
  }
}

private fun searchUrlFor(query: String): String =
  "https://duckduckgo.com/?q=" + encodeQueryComponent(query.trim())

private fun encodeQueryComponent(value: String): String = buildString {
  for (char in value.encodeToByteArray()) {
    val b = char.toInt() and 0xFF
    val c = b.toChar()
    when {
      c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
      c == ' ' -> append('+')
      else -> {
        append('%')
        append(b.toString(16).uppercase().padStart(2, '0'))
      }
    }
  }
}