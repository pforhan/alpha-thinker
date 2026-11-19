package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.ExportConfig
import alphainterplanetary.thinker.model.ExportFormat
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Configures a new export before generation. [initialConfig], when provided, prefills
 * the form (e.g. "export again" from an existing artifact); otherwise defaults are
 * derived from [project]. Confirm always requests a new export artifact.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportConfigDialog(
  project: Project,
  initialConfig: ExportConfig? = null,
  onDismiss: () -> Unit,
  onConfirm: (ExportConfig) -> Unit,
  onViewExports: (() -> Unit)? = null,
) {
  val defaultName = remember(project) {
    project.title.ifBlank { project.synopsis }.ifBlank { "export" }
  }
  val base = initialConfig ?: ExportConfig(outputName = defaultName)
  var outputName by remember(base) { mutableStateOf(base.outputName.ifBlank { defaultName }) }
  var format by remember(base) { mutableStateOf(base.format) }
  var includeIgnored by remember(base) { mutableStateOf(base.includeIgnoredQuestions) }
  var includeUnanswered by remember(base) { mutableStateOf(base.includeUnansweredQuestions) }
  var includeDrafts by remember(base) { mutableStateOf(base.includeDrafts) }
  var includeRevisions by remember(base) { mutableStateOf(base.includeAnswerRevisionHistory) }
  var includeSummaries by remember(base) { mutableStateOf(base.includeSummaries) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Prepare export") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
      ) {
        OutlinedTextField(
          value = outputName,
          onValueChange = { outputName = it },
          label = { Text("File name") },
          supportingText = {
            Text("Base name; the .${format.extension} extension is added automatically.")
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.TightGap)) {
          Text("Format")
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
            verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
          ) {
            ExportFormat.entries.forEach { option ->
              FilterChip(
                selected = format == option,
                onClick = {
                  format = option
                  if (!option.isDocument) {
                    includeSummaries = false
                  }
                },
                label = { Text(formatLabel(option)) },
                elevation = null,
              )
            }
          }
        }
        if (format.isDocument) {
          ExportOptionRow(
            checked = includeIgnored,
            onCheckedChange = { includeIgnored = it },
            label = "Include ignored questions",
          )
          ExportOptionRow(
            checked = includeUnanswered,
            onCheckedChange = { includeUnanswered = it },
            label = "Include unanswered questions",
          )
          ExportOptionRow(
            checked = includeDrafts,
            onCheckedChange = { includeDrafts = it },
            label = "Include drafts",
          )
          ExportOptionRow(
            checked = includeRevisions,
            onCheckedChange = { includeRevisions = it },
            label = "Include answer revision history",
          )
          ExportOptionRow(
            checked = includeSummaries,
            onCheckedChange = { includeSummaries = it },
            label = "Include summaries when available",
          )
        } else {
          Text(
            text = "Portable exports include full project data (questions, answers, drafts, rounds, and history) for transfer between Thinker instances.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (onViewExports != null) {
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          TextButton(onClick = onViewExports) {
            Text("View prepared exports")
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm(
            ExportConfig(
              outputName = outputName.trim().ifBlank { defaultName },
              includeIgnoredQuestions = includeIgnored,
              includeUnansweredQuestions = includeUnanswered,
              includeDrafts = includeDrafts,
              includeAnswerRevisionHistory = includeRevisions,
              includeSummaries = includeSummaries,
              format = format,
            )
          )
        }
      ) {
        Text("Prepare")
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
private fun ExportOptionRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  label: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(text = label)
  }
}

private fun formatLabel(format: ExportFormat): String = when (format) {
  ExportFormat.Markdown -> "Markdown (.md)"
  ExportFormat.Html -> "HTML (.html)"
  ExportFormat.Interchange -> "Portable (.thinker)"
}
