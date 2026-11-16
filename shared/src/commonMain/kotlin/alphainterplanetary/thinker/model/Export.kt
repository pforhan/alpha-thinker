package alphainterplanetary.thinker.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Options that control what an export includes and how it is rendered.
 * Kept as a data class with defaults so new options can be added without
 * breaking existing callers.
 *
 * [outputName] is the requested base name for the export (without extension).
 */
@Serializable
data class ExportConfig(
  val outputName: String,
  val includeIgnoredQuestions: Boolean = false,
  val includeUnansweredQuestions: Boolean = true,
  val includeDrafts: Boolean = true,
  val includeAnswerRevisionHistory: Boolean = false,
  val includeSummaries: Boolean = true,
  val format: ExportFormat = ExportFormat.Markdown,
)

/** Output format for a generated export. */
@Serializable
enum class ExportFormat {
  Markdown;

  val extension: String
    get() = when (this) {
      Markdown -> "md"
    }

  val mimeType: String
    get() = when (this) {
      Markdown -> "text/markdown"
    }
}

/** Lifecycle state of a generated or persisted export artifact. */
enum class ExportStatus {
  Pending,
  Generating,
  Completed,
  Failed,
}

/**
 * A generated export and the metadata needed to persist, list, and share it.
 *
 * [storageRef] identifies where the exported content lives (for example a file
 * path or platform URI) once [status] is [ExportStatus.Completed]; it is null
 * before the artifact has been saved or when generation failed. [config] is the
 * snapshot of options used to produce this export so it can be inspected or
 * regenerated later. [content] holds the rendered export text for completed,
 * app-persisted exports so it can be retrieved, re-shared, or saved out on any
 * platform.
 */
data class ExportArtifact(
  val id: String,
  val projectId: String,
  val createdAt: Instant,
  val filename: String,
  val format: ExportFormat,
  val config: ExportConfig,
  val status: ExportStatus,
  val storageRef: String? = null,
  val error: String? = null,
  val content: String? = null,
)
