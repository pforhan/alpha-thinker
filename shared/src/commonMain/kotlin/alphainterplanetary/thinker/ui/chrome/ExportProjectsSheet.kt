package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.GlobalInterchangeExport
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@Composable
internal fun ExportProjectsSheetContent(chrome: AppChromeState) {
  val scope = rememberCoroutineScope()
  var projects by remember { mutableStateOf<List<Project>?>(null) }
  var loadError by remember { mutableStateOf<String?>(null) }
  var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var outputName by remember { mutableStateOf("projects-export") }
  var generating by remember { mutableStateOf(false) }
  var exportResult by remember { mutableStateOf<GlobalInterchangeExport?>(null) }
  var actionError by remember { mutableStateOf<String?>(null) }
  var actionMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    runCatching { chrome.projects.getAllProjects() }
      .onSuccess { list ->
        projects = list
        selectedIds = list.map { it.id }.toSet()
      }
      .onFailure { e -> loadError = e.message ?: "Failed to load projects" }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    Text(
      text = "Export selected projects as a portable .thinker file for backup.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val loaded = projects
    when {
      loadError != null -> {
        Text(
          text = loadError ?: "Failed to load projects",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      loaded == null -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.padding(end = Dimens.ContentGap))
          Text("Loading projects…")
        }
      }

      loaded.isEmpty() -> {
        Text(
          text = "No projects to export.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      exportResult != null -> {
        val result = exportResult!!
        Text(
          text = "Export ready",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "${result.filename} (${result.projectCount} project${if (result.projectCount == 1) "" else "s"})",
          style = MaterialTheme.typography.bodyMedium,
        )
        actionMessage?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        actionError?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (chrome.exports.supportsSave) {
            Button(onClick = {
              actionError = null
              actionMessage = null
              scope.launch {
                val dest = chrome.exports.saveManagedExport(result.filename, result.mimeType, result.storageRef)
                actionMessage = if (dest == null) "Save cancelled or unavailable"
                else "Saved export"
              }
            }) {
              Text(chrome.exports.saveActionLabel)
            }
          }
          if (chrome.exports.supportsShare) {
            TextButton(onClick = {
              actionError = null
              actionMessage = null
              scope.launch {
                val ok = chrome.exports.shareManagedExport(result.filename, result.mimeType, result.storageRef)
                if (!ok) actionError = "Sharing isn't available"
              }
            }) { Text("Share") }
          }
        }
        if (!chrome.exports.supportsSave && !chrome.exports.supportsShare) {
          Text(
            text = "No save/share actions are available on this platform.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(onClick = {
          val ref = exportResult?.storageRef
          exportResult = null
          actionError = null
          actionMessage = null
          if (ref != null) {
            scope.launch { chrome.exports.deleteManaged(ref) }
          }
        }) { Text("Export different projects") }
      }

      else -> {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = { selectedIds = loaded.map { it.id }.toSet() }) { Text("Select all") }
          TextButton(onClick = { selectedIds = emptySet() }) { Text("Select none") }
          Spacer(modifier = Modifier.weight(1f))
          Text(
            text = "${selectedIds.size}/${loaded.size} selected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        OutlinedTextField(
          value = outputName,
          onValueChange = { outputName = it },
          label = { Text("File name") },
          supportingText = { Text("Base name; .thinker is added automatically.") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        loaded.forEach { project ->
          ProjectSelectRow(
            project = project,
            selected = project.id in selectedIds,
            onToggle = {
              selectedIds = if (project.id in selectedIds) selectedIds - project.id else selectedIds + project.id
            },
          )
        }

        actionError?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Button(
          onClick = {
            if (selectedIds.isEmpty() || generating) return@Button
            generating = true
            actionError = null
            scope.launch {
              val result = chrome.exports.generateGlobalInterchangeExport(
                projectIds = loaded.filter { it.id in selectedIds }.map { it.id },
                outputName = outputName.trim().ifBlank { "projects-export" },
              )
              generating = false
              result.onSuccess { exportResult = it }
                .onFailure { actionError = it.message ?: "Export failed" }
            }
          },
          enabled = selectedIds.isNotEmpty() && !generating,
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (generating) {
            CircularProgressIndicator(
              modifier = Modifier.padding(end = Dimens.ContentGap),
              strokeWidth = Dimens.ProgressStroke,
            )
            Text("Preparing export…")
          } else {
            Text("Export ${selectedIds.size} project${if (selectedIds.size == 1) "" else "s"}")
          }
        }
      }
    }
  }
}

@Composable
private fun ProjectSelectRow(
  project: Project,
  selected: Boolean,
  onToggle: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onToggle)
      .padding(vertical = Dimens.TightGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = selected, onCheckedChange = { onToggle() })
    Spacer(modifier = Modifier.width(Dimens.TightGap))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = project.title.ifBlank { "(Untitled project)" },
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (project.synopsis.isNotBlank()) {
        Text(
          text = project.synopsis,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
