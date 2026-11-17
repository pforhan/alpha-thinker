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
  Markdown,
  Html;

  val extension: String
    get() = when (this) {
      Markdown -> "md"
      Html -> "html"
    }

  val mimeType: String
    get() = when (this) {
      Markdown -> "text/markdown"
      Html -> "text/html"
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
 * [storageRef] is the canonical internal managed-file path/ref once [status] is
 * [ExportStatus.Completed]; it is null before generation completes or when it
 * failed. [config] is the snapshot of options used to produce this export so it
 * can be inspected or replayed (e.g. to prefill an export dialog and generate a
 * new export). Rendered export content lives in the managed file, not in the DB.
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
  /** When generation reached a terminal state ([Completed] or [Failed]), if known. */
  val completedAt: Instant? = null,
)
