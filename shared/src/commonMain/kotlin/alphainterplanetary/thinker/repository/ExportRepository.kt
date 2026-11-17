package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.activitylog.LogTopic
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.engine.core.PlanningEngineSelector
import alphainterplanetary.thinker.export.ExportFileStore
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
  private val exportFileStore: ExportFileStore,
  private val engineRepository: EngineRepository,
) {
  val supportsSave: Boolean get() = platformExportActions.supportsSave
  val supportsOpen: Boolean get() = platformExportActions.supportsOpen
  val supportsShare: Boolean get() = platformExportActions.supportsShare
  val saveActionLabel: String get() = platformExportActions.saveActionLabel

  fun observeExports(projectId: String): Flow<List<ExportArtifact>> =
    storage.observeExportArtifacts(projectId)

  suspend fun getExports(projectId: String): List<ExportArtifact> =
    storage.getExportArtifacts(projectId)

  suspend fun getExport(id: String): ExportArtifact? =
    storage.getExportArtifact(id)

  /**
   * Reads rendered export content from the artifact's canonical managed file.
   * Returns failure if the artifact is missing, not completed, or its file is
   * unavailable, and records a visible error on the artifact in that case.
   */
  suspend fun readExportContent(artifactId: String): Result<String> {
    val artifact = storage.getExportArtifact(artifactId)
      ?: return Result.failure(IllegalArgumentException("No export artifact with id $artifactId"))
    val ref = artifact.storageRef
    if (artifact.status != ExportStatus.Completed || ref == null) {
      return Result.failure(IllegalStateException("Export is not completed"))
    }
    val content = exportFileStore.readText(ref)
    return if (content != null) {
      Result.success(content)
    } else {
      val message = "Export file is missing or unreadable: $ref"
      markFileUnavailable(artifact, message)
      Result.failure(IllegalStateException(message))
    }
  }

  /**
   * Deletes the artifact's managed file and DB record. File and DB are removed
   * best-effort; failures are surfaced rather than hidden.
   */
  suspend fun deleteExport(id: String): Result<Unit> {
    val artifact = storage.getExportArtifact(id)
      ?: return Result.failure(IllegalArgumentException("No export artifact with id $id"))
    val errors = mutableListOf<String>()
    artifact.storageRef?.let { ref ->
      val deleted = exportFileStore.delete(ref)
      if (!deleted) errors += "Failed to delete export file: $ref"
    }
    runCatching { storage.deleteExportArtifact(id) }
      .onFailure { errors += "Failed to delete export record: ${it.message ?: it}" }
    return if (errors.isEmpty()) Result.success(Unit)
    else Result.failure(IllegalStateException(errors.joinToString("; ")))
  }

  /**
   * Persists a [ExportStatus.Pending] artifact and enqueues generation as a task.
   * The task renders to a canonical managed file and transitions the artifact to
   * [ExportStatus.Completed] with [ExportArtifact.storageRef], or to
   * [ExportStatus.Failed] on error/cancellation. Completed artifacts are never
   * overwritten by a failure. Each call creates a new artifact+file (history).
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

  /**
   * Saves/copies a completed export to a user-chosen or platform destination.
   * Returns the destination ref/path/URI on success, or null if
   * cancelled/unavailable. The canonical internal [ExportArtifact.storageRef] is
   * never changed by this operation.
   */
  suspend fun saveExport(artifactId: String): String? =
    withCompletedContent(artifactId) { artifact, path, content ->
      platformExportActions.save(artifact.filename, artifact.format.mimeType, path, content)
    }

  /** Shares or opens a completed export via platform-native mechanisms. */
  suspend fun shareExport(artifactId: String): Boolean =
    withCompletedContent(artifactId) { artifact, path, content ->
      platformExportActions.share(artifact.filename, artifact.format.mimeType, path, content)
    } ?: false

  /** Opens a completed export in a viewer/default handler when supported. */
  suspend fun openExport(artifactId: String): Boolean =
    withCompletedContent(artifactId) { artifact, path, content ->
      platformExportActions.open(artifact.filename, artifact.format.mimeType, path, content)
    } ?: false

  private suspend fun <T> withCompletedContent(
    artifactId: String,
    block: suspend (ExportArtifact, String, String) -> T,
  ): T? {
    val artifact = storage.getExportArtifact(artifactId) ?: return null
    val path = artifact.storageRef ?: return null
    val content = readExportContent(artifactId).getOrNull() ?: return null
    return block(artifact, path, content)
  }

  private suspend fun markFileUnavailable(artifact: ExportArtifact, message: String) {
    // Keep state honest/visible: a completed record whose file is missing is failed.
    if (artifact.status == ExportStatus.Completed) {
      storage.saveExportArtifact(
        artifact.copy(
          status = ExportStatus.Failed,
          error = message,
          storageRef = artifact.storageRef,
          completedAt = now(),
        )
      )
    }
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
      topic = LogTopic.Export,
      source = LogSource.App,
      projectId = projectId,
    )
    var writtenPath: String? = null
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
          storage = storage,
          engineIdentity = engineRepository.selectedConfiguredIdentity,
        ).also { taskRunner.setProgress(taskId, 0.7f) }
      } else {
        emptyMap()
      }

      taskRunner.setProgress(taskId, 0.85f)
      val content = ProjectExportRenderer(config).render(project, phaseSummaries = summaries)
      val path = exportFileStore.pathFor(artifactId, current.format.extension)
      exportFileStore.writeText(path, content).getOrElse { throw it }
      writtenPath = path

      val completed = (storage.getExportArtifact(artifactId) ?: current).copy(
        status = ExportStatus.Completed,
        storageRef = path,
        error = null,
        completedAt = now(),
      )
      runCatching { storage.saveExportArtifact(completed) }.getOrElse { dbError ->
        // DB failed after file was written: best-effort cleanup, but surface failure.
        exportFileStore.delete(path)
        throw IllegalStateException(
          "Export file written but metadata save failed: ${dbError.message ?: dbError}",
          dbError,
        )
      }
      writtenPath = null
      taskRunner.setProgress(taskId, 1f)
      log.append("Export saved: ${completed.filename}")
    } catch (e: CancellationException) {
      writtenPath?.let { exportFileStore.delete(it) }
      markFailedUnlessCompleted(artifactId, "Cancelled")
      throw e
    } catch (e: Exception) {
      // If file was written but we failed before marking completed, remove orphan file.
      writtenPath?.let { exportFileStore.delete(it) }
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
        storageRef = null,
        completedAt = now(),
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
