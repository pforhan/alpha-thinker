package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.export.ImportExecutionResult
import alphainterplanetary.thinker.export.ImportIssue
import alphainterplanetary.thinker.export.ImportIssueSeverity
import alphainterplanetary.thinker.export.ImportPlan
import alphainterplanetary.thinker.export.ImportProjectAction
import alphainterplanetary.thinker.export.ImportProjectCandidate
import alphainterplanetary.thinker.export.ImportProjectResult
import alphainterplanetary.thinker.ui.platform.rememberInterchangeFilePicker
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@Composable
internal fun ImportProjectsSheetContent(chrome: AppChromeState) {
  val scope = rememberCoroutineScope()
  var phase by remember { mutableStateOf<ImportPhase>(ImportPhase.Pick) }
  var message by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  val pickFiles = rememberInterchangeFilePicker(
    onPicked = { files ->
      if (files.isEmpty()) return@rememberInterchangeFilePicker
      error = null
      message = null
      phase = ImportPhase.Analyzing
      scope.launch {
        val analyses = files.map { file ->
          val plan = chrome.projects.analyzeInterchangeImport(file.bytes)
          FileAnalysis(fileName = file.name, plan = plan)
        }
        val candidates = analyses.flatMap { analysis ->
          analysis.plan.projects.map { candidate ->
            EditableCandidate(sourceFile = analysis.fileName, candidate = candidate)
          }
        }
        phase = ImportPhase.Preview(analyses = analyses, candidates = candidates)
      }
    },
    onMessage = { msg ->
      error = msg
    },
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    when (val current = phase) {
      ImportPhase.Pick -> {
        Text(
          text = "Import projects from a portable .thinker file. You can review and adjust the import before applying it.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
          Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (pickFiles != null) {
          Button(onClick = { error = null; pickFiles() }, modifier = Modifier.fillMaxWidth()) {
            Text("Choose file(s)…")
          }
          Text(
            text = "Supports single or multiple .thinker files (including multi-project exports).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            text = "File selection is unavailable on this platform.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      ImportPhase.Analyzing -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.padding(end = Dimens.ContentGap))
          Text("Analyzing file(s)…")
        }
      }

      is ImportPhase.Preview -> {
        PreviewContent(
          analyses = current.analyses,
          candidates = current.candidates,
          onCandidateActionChange = { index, action ->
            val updated = current.candidates.map { editable ->
              if (editable.candidate.index == index && editable.sourceFile == current.candidates.find { it.candidate.index == index }?.sourceFile) {
                // index may collide across files; match by identity in list below
                editable
              } else editable
            }
            // handled via replacement function below
          },
          onUpdateCandidates = { updated ->
            phase = current.copy(candidates = updated)
          },
          onPickDifferent = {
            error = null
            message = null
            phase = ImportPhase.Pick
          },
          onImport = {
            val toImport = current.candidates.map { it.candidate }
            if (toImport.none { it.action != ImportProjectAction.Skip && it.isImportable }) {
              error = "No importable projects selected."
              return@PreviewContent
            }
            error = null
            message = null
            phase = ImportPhase.Importing
            scope.launch {
              val plan = ImportPlan(projects = toImport)
              val result = chrome.projects.executeImportPlan(plan)
              val importedRefs = result.results
                .filter { it.action != ImportProjectAction.Skip && it.importedProjectId != null && it.error == null }
                .mapNotNull { res ->
                  val id = res.importedProjectId ?: return@mapNotNull null
                  val title = runCatching { chrome.projects.getProject(id)?.title.orEmpty() }.getOrDefault("")
                  ImportedProjectRef(id = id, title = title.ifBlank { "Imported project" })
                }
              phase = ImportPhase.Done(result = result, imported = importedRefs)
            }
          },
          error = error,
          onError = { error = it },
        )
      }

      ImportPhase.Importing -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.padding(end = Dimens.ContentGap))
          Text("Importing projects…")
        }
      }

      is ImportPhase.Done -> {
        DoneContent(
          result = current.result,
          imported = current.imported,
          chrome = chrome,
          onImportMore = {
            error = null
            message = null
            phase = ImportPhase.Pick
          },
        )
      }
    }
  }
}

private sealed interface ImportPhase {
  data object Pick : ImportPhase
  data object Analyzing : ImportPhase
  data class Preview(
    val analyses: List<FileAnalysis>,
    val candidates: List<EditableCandidate>,
  ) : ImportPhase
  data object Importing : ImportPhase
  data class Done(
    val result: ImportExecutionResult,
    val imported: List<ImportedProjectRef>,
  ) : ImportPhase
}

private data class FileAnalysis(
  val fileName: String,
  val plan: ImportPlan,
)

private data class EditableCandidate(
  val sourceFile: String,
  val candidate: ImportProjectCandidate,
)

private data class ImportedProjectRef(
  val id: String,
  val title: String,
)

@Composable
private fun PreviewContent(
  analyses: List<FileAnalysis>,
  candidates: List<EditableCandidate>,
  onCandidateActionChange: (Int, ImportProjectAction) -> Unit,
  onUpdateCandidates: (List<EditableCandidate>) -> Unit,
  onPickDifferent: () -> Unit,
  onImport: () -> Unit,
  error: String?,
  onError: (String?) -> Unit,
) {
  val hasImportableSelection = candidates.any { it.candidate.action != ImportProjectAction.Skip && it.candidate.isImportable }

  analyses.forEach { analysis ->
    Text(
      text = "File: ${analysis.fileName}",
      style = MaterialTheme.typography.titleMedium,
    )
    if (!analysis.plan.isReadable || analysis.plan.envelopeIssues.isNotEmpty()) {
      IssueList(issues = analysis.plan.envelopeIssues)
    }
    if (analysis.plan.isReadable && analysis.plan.projects.isEmpty() && analysis.plan.envelopeIssues.none { it.severity == ImportIssueSeverity.Error }) {
      Text(
        text = "No projects found in this file.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (candidates.isNotEmpty()) {
    HorizontalDivider()
    Text(
      text = "Projects to import",
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = "Review each project, resolve conflicts, and choose what to do before importing.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    candidates.forEachIndexed { listIndex, editable ->
      CandidateCard(
        sourceFile = editable.sourceFile,
        candidate = editable.candidate,
        showSource = analyses.size > 1,
        onActionChange = { action ->
          onError(null)
          val updated = candidates.toMutableList().also { list ->
            list[listIndex] = editable.copy(candidate = editable.candidate.copy(action = action))
          }
          onUpdateCandidates(updated)
        },
      )
    }
  } else {
    Text(
      text = "Nothing to import. Choose a different file.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  error?.let {
    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
  }

  Spacer(modifier = Modifier.height(Dimens.ContentGap))
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
  ) {
    OutlinedButton(onClick = onPickDifferent, modifier = Modifier.weight(1f)) {
      Text("Choose different file(s)")
    }
    Button(
      onClick = onImport,
      enabled = hasImportableSelection,
      modifier = Modifier.weight(1f),
    ) {
      Text("Import")
    }
  }
}

@Composable
private fun CandidateCard(
  sourceFile: String,
  candidate: ImportProjectCandidate,
  showSource: Boolean,
  onActionChange: (ImportProjectAction) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      val snapshot = candidate.snapshot
      Text(
        text = snapshot.title.ifBlank { snapshot.synopsis.ifBlank { "(Untitled project)" } },
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (snapshot.title.isNotBlank() && snapshot.synopsis.isNotBlank()) {
        Text(
          text = snapshot.synopsis,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (showSource) {
        Text(
          text = "From: $sourceFile",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = "Rounds: ${candidate.stats.rounds} · Questions: ${candidate.stats.questions} · Answers: ${candidate.stats.answers}",
        style = MaterialTheme.typography.bodySmall,
      )

      val conflict = candidate.existingProjectId != null
      if (conflict) {
        Text(
          text = "A local project with the same ID already exists" +
            (candidate.existingProjectTitle?.takeIf { it.isNotBlank() }?.let { ": \"$it\"" } ?: "."),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      if (candidate.unknownPhaseKeys.isNotEmpty()) {
        Text(
          text = "Unassigned phase content: ${candidate.unknownPhaseKeys.joinToString(", ") { "'$it'" }} will be imported as unassigned (not misattributed).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (candidate.issues.isNotEmpty()) {
        IssueList(issues = candidate.issues)
      }

      if (!candidate.isImportable) {
        Text(
          text = "This project cannot be imported due to errors.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      } else {
        HorizontalDivider()
        Text("Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        ActionOption(
          label = "Import as new project",
          description = "Creates a separate copy with new IDs.",
          selected = candidate.action == ImportProjectAction.ImportAsNew,
          enabled = true,
          onSelect = { onActionChange(ImportProjectAction.ImportAsNew) },
        )
        if (conflict) {
          ActionOption(
            label = "Overwrite existing project",
            description = "Replaces the local project with the same ID.",
            selected = candidate.action == ImportProjectAction.Overwrite,
            enabled = true,
            onSelect = { onActionChange(ImportProjectAction.Overwrite) },
          )
        }
        ActionOption(
          label = "Skip",
          description = "Do not import this project.",
          selected = candidate.action == ImportProjectAction.Skip,
          enabled = true,
          onSelect = { onActionChange(ImportProjectAction.Skip) },
        )
      }
    }
  }
}

@Composable
private fun ActionOption(
  label: String,
  description: String,
  selected: Boolean,
  enabled: Boolean,
  onSelect: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, onClick = onSelect)
      .padding(vertical = Dimens.TightGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = if (enabled) onSelect else null, enabled = enabled)
    Spacer(modifier = Modifier.width(Dimens.TightGap))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.bodyMedium)
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun IssueList(issues: List<ImportIssue>) {
  if (issues.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(Dimens.TightGap)) {
    issues.forEach { issue ->
      val isError = issue.severity == ImportIssueSeverity.Error
      val prefix = if (isError) "Error" else "Warning"
      Text(
        text = "$prefix: ${issue.message}",
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun DoneContent(
  result: ImportExecutionResult,
  imported: List<ImportedProjectRef>,
  chrome: AppChromeState,
  onImportMore: () -> Unit,
) {
  val failures = result.results.filter { it.action != ImportProjectAction.Skip && it.error != null }
  val skipped = result.results.count { it.action == ImportProjectAction.Skip }
  val successCount = imported.size

  Text(
    text = "Import complete",
    style = MaterialTheme.typography.titleMedium,
  )
  Text(
    text = "$successCount imported" +
      (if (skipped > 0) ", $skipped skipped" else "") +
      (if (failures.isNotEmpty()) ", ${failures.size} failed" else ""),
    style = MaterialTheme.typography.bodyMedium,
  )

  if (failures.isNotEmpty()) {
    failures.forEach { failure ->
      Text(
        text = "Failed (${failure.sourceProjectId}): ${failure.error ?: "Unknown error"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }

  if (imported.isNotEmpty()) {
    HorizontalDivider()
    Text(
      text = "Imported projects",
      style = MaterialTheme.typography.titleSmall,
    )
    imported.forEach { project ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { chrome.openProjectFromList(project.id) }
          .padding(vertical = Dimens.TightGap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = project.title.ifBlank { "Imported project" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Open project",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }

  Spacer(modifier = Modifier.height(Dimens.ContentGap))
  OutlinedButton(onClick = onImportMore, modifier = Modifier.fillMaxWidth()) {
    Text("Import more")
  }
}
