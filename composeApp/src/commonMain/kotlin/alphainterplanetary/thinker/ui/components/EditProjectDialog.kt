package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.model.Project
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

enum class ProjectDialogMode {
  Create,
  Edit,
}

@Composable
fun EditProjectDialog(
  project: Project,
  onDismiss: () -> Unit,
  onSave: (String, String, ProjectUpdateMode) -> Unit,
) {
  var title by remember { mutableStateOf(project.editableTitle) }
  var synopsis by remember { mutableStateOf(project.synopsis) }
  var updateMode by remember { mutableStateOf(ProjectUpdateMode.KEEP) }

  ProjectDialog(
    mode = ProjectDialogMode.Edit,
    title = title,
    onTitleChange = { title = it.take(30) },
    synopsis = synopsis,
    onSynopsisChange = { synopsis = it },
    updateMode = updateMode,
    onUpdateModeChange = { updateMode = it },
    showTitleField = true,
    onToggleTitleField = {},
    confirmLabel = "Save",
    onDismiss = onDismiss,
    onConfirm = { onSave(title.trim(), synopsis.trim(), updateMode) },
  )
}

@Composable
fun CreateProjectDialog(
  onDismiss: () -> Unit,
  onCreate: (title: String, synopsis: String) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var synopsis by remember { mutableStateOf("") }
  var showTitleField by remember { mutableStateOf(false) }

  ProjectDialog(
    mode = ProjectDialogMode.Create,
    title = title,
    onTitleChange = { title = it.take(30) },
    synopsis = synopsis,
    onSynopsisChange = { synopsis = it },
    updateMode = ProjectUpdateMode.KEEP,
    onUpdateModeChange = {},
    showTitleField = showTitleField,
    onToggleTitleField = { showTitleField = !showTitleField },
    confirmLabel = "Create",
    onDismiss = onDismiss,
    onConfirm = { onCreate(title.trim(), synopsis.trim()) },
  )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProjectDialog(
  mode: ProjectDialogMode,
  title: String,
  onTitleChange: (String) -> Unit,
  synopsis: String,
  onSynopsisChange: (String) -> Unit,
  updateMode: ProjectUpdateMode,
  onUpdateModeChange: (ProjectUpdateMode) -> Unit,
  showTitleField: Boolean,
  onToggleTitleField: () -> Unit,
  confirmLabel: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  val isEdit = mode == ProjectDialogMode.Edit
  val synopsisFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    synopsisFocusRequester.requestFocus()
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isEdit) "Edit Project" else "New Project") },
    text = {
      Column {
        if (!isEdit && !showTitleField) {
          TextButton(onClick = onToggleTitleField) {
            Text("Add title (optional)")
          }
        } else {
          OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
          value = synopsis,
          onValueChange = onSynopsisChange,
          label = { Text("Synopsis") },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(synopsisFocusRequester),
          maxLines = 5,
        )

        if (isEdit) {
          Spacer(modifier = Modifier.height(16.dp))
          Text("Handling prior answers:")
          RadioButtonList(
            selectedMode = updateMode,
            onModeSelected = onUpdateModeChange,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun RadioButtonList(
  selectedMode: ProjectUpdateMode,
  onModeSelected: (ProjectUpdateMode) -> Unit,
) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      RadioButton(
        selected = selectedMode == ProjectUpdateMode.KEEP,
        onClick = { onModeSelected(ProjectUpdateMode.KEEP) },
      )
      Text("Keep existing answers")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      RadioButton(
        selected = selectedMode == ProjectUpdateMode.CLEAR,
        onClick = { onModeSelected(ProjectUpdateMode.CLEAR) },
      )
      Text("Clear all answers")
    }
  }
}