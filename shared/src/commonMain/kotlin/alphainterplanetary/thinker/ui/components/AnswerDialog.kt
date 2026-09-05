package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Question
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

enum class AnswerDialogResult {
  Submitted,
  AskLater,
  DeletedAnswer,
}

@Composable
fun AnswerDialog(
  question: Question,
  onDismiss: () -> Unit,
  onResult: (AnswerDialogResult, String) -> Unit,
) {
  val currentAnswer: Answer? = question.currentAnswer
  var answerText by remember { mutableStateOf(currentAnswer?.text ?: "") }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(question.text) },
    text = {
      Column {
        OutlinedTextField(
          value = answerText,
          onValueChange = { answerText = it },
          label = { Text("Answer") },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
          minLines = 3,
          maxLines = 8,
        )

        if (currentAnswer?.isComplete == true) {
          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(8.dp))
          TextButton(
            onClick = { onResult(AnswerDialogResult.DeletedAnswer, answerText) },
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
      Column {
        TextButton(
          onClick = { onResult(AnswerDialogResult.AskLater, answerText) },
        ) {
          Text("Ask Later")
        }
        TextButton(
          onClick = { onResult(AnswerDialogResult.Submitted, answerText) },
          enabled = answerText.isNotBlank(),
        ) {
          Text("Submit")
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
