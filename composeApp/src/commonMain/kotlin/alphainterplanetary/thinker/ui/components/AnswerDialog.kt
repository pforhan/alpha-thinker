package alphainterplanetary.thinker.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

@Composable
fun AnswerDialog(
    project: Project,
    question: Question,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
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
