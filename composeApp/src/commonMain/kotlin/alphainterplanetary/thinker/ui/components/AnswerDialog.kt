package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnswerDialog(
  project: Project,
  question: Question,
  onDismiss: () -> Unit,
  onSubmit: (String) -> Unit,
) {
  var answerText by remember { mutableStateOf(question.currentAnswer?.text ?: "") }
  var showAskLater by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(question.text) },
    text = {
      Column {
        OutlinedTextField(
          value = answerText,
          onValueChange = { answerText = it },
          label = { Text("Answer") },
          modifier = Modifier.fillMaxWidth(),
          maxLines = 5
        )

        if (showAskLater) {
          Spacer(modifier = Modifier.height(16.dp))
          TextButton(onClick = { showAskLater = false }) {
            Text("Ask Later")
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSubmit(answerText)
        }
      ) {
        Text("Submit")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
