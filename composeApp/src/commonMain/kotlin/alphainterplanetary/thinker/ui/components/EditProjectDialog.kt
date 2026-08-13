package alphainterplanetary.thinker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.model.Project

@Composable
fun EditProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (String, String, ProjectUpdateMode) -> Unit
) {
    var title by remember { mutableStateOf(project.editableTitle) }
    var synopsis by remember { mutableStateOf(project.synopsis) }
    var mode by remember { mutableStateOf(ProjectUpdateMode.KEEP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Project") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    label = { Text("Synopsis") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Handling prior answers:")
                RadioButtonList(
                    selectedMode = mode,
                    onModeSelected = { mode = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(title, synopsis, mode)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RadioButtonList(
    selectedMode: ProjectUpdateMode,
    onModeSelected: (ProjectUpdateMode) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedMode == ProjectUpdateMode.KEEP,
                onClick = { onModeSelected(ProjectUpdateMode.KEEP) }
            )
            Text("Keep existing answers")
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedMode == ProjectUpdateMode.CLEAR,
                onClick = { onModeSelected(ProjectUpdateMode.CLEAR) }
            )
            Text("Clear all answers")
        }
    }
}
