package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.activitylog.LogTopic
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.engine.core.PlanningEngineSelector
import alphainterplanetary.thinker.export.PlatformExportActions
import alphainterplanetary.thinker.export.ProjectExportRenderer
import alphainterplanetary.thinker.export.ProjectExportSummarizer
import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.ExportConfig
import alphainterplanetary.thinker.model.ExportStatus
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import me.tatarka.inject.annotations.Inject

@AppScope
class ExportRepository @Inject constructor(
  private val storage: Storage,
  private val engineSelector: PlanningEngineSelector,
  private val taskRunner: TaskRunner,
  private val activityLogger: ActivityLogger,
  private val platformExportActions: PlatformExportActions,
) {
  fun observeExports(projectId: String): Flow<List<ExportArtifact>> =
    storage.observeExportArtifacts(projectId)

  suspend fun getExports(projectId: String): List<ExportArtifact> =
    storage.getExportArtifacts(projectId)

  suspend fun getExport(id: String): ExportArtifact? =
    storage.getExportArtifact(id)

  suspend fun deleteExport(id: String) {
    storage.deleteExportArtifact(id)
  }

  /**
   * Persists a [ExportStatus.Pending] artifact and enqueues generation as a task.
   * The task transitions it to [ExportStatus.Generating], then to
   * [ExportStatus.Completed] with rendered content (and `storageRef = "db:<id>"`)
   * or to [ExportStatus.Failed] on error/cancellation. A completed artifact is
   * never overwritten by a failure.
   *
   * Each call creates a new artifact record (history is retained). The resolved
   * filename is made unique among the project's existing export filenames.
   */
  suspend fun generateExport(projectId: String, config: ExportConfig): ExportArtifact {
    val project = storage.getProject(projectId)
      ?: throw IllegalArgumentException("No project with id $projectId")
    val artifact = pendingArtifact(project, config)
    storage.saveExportArtifact(artifact)

    taskRunner.enqueue(projectId, TaskKind.Export) { taskId ->
      runExportTask(taskId, artifact.id, projectId, artifact.config)
    }
    return artifact
  }

  /** Regenerates an export as a new artifact using [artifactId]'s config snapshot. */
  suspend fun regenerateExport(artifactId: String): ExportArtifact {
    val existing = storage.getExportArtifact(artifactId)
      ?: throw IllegalArgumentException("No export artifact with id $artifactId")
    return generateExport(existing.projectId, existing.config)
  }

  /**
   * Saves a completed export via the platform save flow. Returns the platform
   * storage ref/path/URI on success, or null if unavailable/cancelled/not ready.
   * On success, persists the returned ref on the artifact when it differs.
   */
  suspend fun saveExport(artifactId: String): String? {
    val artifact = storage.getExportArtifact(artifactId) ?: return null
    val content = artifact.content ?: return null
    val ref = platformExportActions.save(artifact.filename, artifact.format.mimeType, content) ?: return null
    if (ref != artifact.storageRef) {
      storage.saveExportArtifact(artifact.copy(storageRef = ref))
    }
    return ref
  }

  /**
   * Shares/opens a completed export via the platform share flow. Returns true if
   * initiated successfully.
   */
  suspend fun shareExport(artifactId: String): Boolean {
    val artifact = storage.getExportArtifact(artifactId) ?: return false
    val content = artifact.content ?: return false
    return platformExportActions.share(artifact.filename, artifact.format.mimeType, content)
  }

  private suspend fun pendingArtifact(project: Project, config: ExportConfig): ExportArtifact {
    val resolved = config.snapshotFor(project)
    val filename = uniqueFilename(project.id, exportFilename(resolved))
    val createdAt = now()
    val id = randomUUID()
    return ExportArtifact(
      id = id,
      projectId = project.id,
      createdAt = createdAt,
      filename = filename,
      format = resolved.format,
      config = resolved,
      status = ExportStatus.Pending,
    )
  }

  private suspend fun uniqueFilename(projectId: String, filename: String): String {
    val existing = storage.getExportArtifacts(projectId).map { it.filename }.toSet()
    if (filename !in existing) return filename
    val base = filename.substringBeforeLast('.', filename)
    val ext = filename.substringAfterLast('.', "")
    var i = 2
    while (true) {
      val candidate = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
      if (candidate !in existing) return candidate
      i++
    }
  }

  private fun ExportConfig.snapshotFor(project: Project): ExportConfig {
    val baseName = outputName.ifBlank { project.title.ifBlank { project.synopsis } }
      .ifBlank { "export" }
    return copy(outputName = sanitizeExportName(baseName))
  }

  private suspend fun runExportTask(
    taskId: String,
    artifactId: String,
    projectId: String,
    config: ExportConfig,
  ) {
    val log = activityLogger.context(
      activityId = taskId,
      topic = LogTopic.Info,
      source = LogSource.App,
      projectId = projectId,
    )
    try {
      val current = storage.getExportArtifact(artifactId) ?: return
      if (current.status == ExportStatus.Completed) return
      storage.saveExportArtifact(current.copy(status = ExportStatus.Generating, error = null))
      taskRunner.setProgress(taskId, 0.05f)
      log.append("Generating export: ${current.filename}")

      val project = storage.getProject(projectId)
        ?: throw IllegalStateException("Project $projectId no longer exists")

      val summaries = if (config.includeSummaries) {
        taskRunner.setProgress(taskId, 0.2f)
        log.append("Summarizing phases for export")
        ProjectExportSummarizer.summarizePhases(
          project = project,
          engine = engineSelector.selectedEngine(),
          activityId = taskId,
        ).also { taskRunner.setProgress(taskId, 0.7f) }
      } else {
        emptyMap()
      }

      taskRunner.setProgress(taskId, 0.85f)
      val content = ProjectExportRenderer(config).render(project, phaseSummaries = summaries)
      val completed = (storage.getExportArtifact(artifactId) ?: current).copy(
        status = ExportStatus.Completed,
        storageRef = "db:$artifactId",
        content = content,
        error = null,
      )
      storage.saveExportArtifact(completed)
      taskRunner.setProgress(taskId, 1f)
      log.append("Export saved: ${completed.filename}")
    } catch (e: CancellationException) {
      markFailedUnlessCompleted(artifactId, "Cancelled")
      throw e
    } catch (e: Exception) {
      markFailedUnlessCompleted(artifactId, e.message ?: e.toString())
      throw e
    }
  }

  private suspend fun markFailedUnlessCompleted(artifactId: String, message: String) {
    val current = storage.getExportArtifact(artifactId) ?: return
    if (current.status == ExportStatus.Completed) return
    storage.saveExportArtifact(
      current.copy(
        status = ExportStatus.Failed,
        error = message,
        content = null,
        storageRef = null,
      )
    )
  }
}

private fun exportFilename(config: ExportConfig): String =
  "${config.outputName}.${config.format.extension}"

private fun sanitizeExportName(name: String): String {
  val cleaned = name
    .trim()
    .replace(Regex("[^A-Za-z0-9._ -]+"), "_")
    .replace(Regex("\\s+"), " ")
    .trim(' ', '.', '_')
    .take(80)
  return cleaned.ifBlank { "export" }
}
