package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.phases.Phase

/**
 * Fluent sink for building a human-readable export document.
 *
 * Implementations render the same structural events to different formats
 * (Markdown today, HTML later) without changing the export orchestration.
 */
interface ExportDocument {
  /** Sets the document title and opening synopsis/overview text. */
  fun documentHeader(title: String, synopsis: String): ExportDocument

  /** Renders the opening phase-trail summary. */
  fun phaseTrail(entries: List<PhaseTrailEntry>): ExportDocument

  /** Starts a phase section (e.g. `## Scope & Goals`). */
  fun beginPhase(phase: Phase): ExportDocument

  /**
   * Ends the current phase section. Paired with [beginPhase]; provides a
   * close hook for formats that wrap phases in containers (e.g. HTML sections).
   */
  fun endPhase(phase: Phase): ExportDocument

  /** Renders a question and its exported answer state within the current phase. */
  fun question(block: ExportQuestionBlock): ExportDocument

  /** Renders an optional summary block for the current phase. */
  fun phaseSummary(phase: Phase, summary: String): ExportDocument

  /** Returns the rendered document. */
  fun build(): String
}

/** One row in the opening phase-trail summary. */
data class PhaseTrailEntry(
  val phase: Phase,
  /** 1-based traversal order in this export (order the phase appears). */
  val traversalIndex: Int,
  val questionCount: Int,
  val resolvedCount: Int,
  val completionPercent: Int,
)

/**
 * Render-ready question content for export, already filtered/interpreted by
 * [ProjectExportRenderer] according to [alphainterplanetary.thinker.model.ExportConfig].
 */
data class ExportQuestionBlock(
  val questionText: String,
  val status: ExportAnswerStatus,
  /** Committed answer text when [status] is [ExportAnswerStatus.Answered]. */
  val answerText: String? = null,
  /** Draft text when [status] is [ExportAnswerStatus.Draft]. */
  val draftText: String? = null,
  /** Timestamp label for the current committed answer, when available. */
  val answeredAt: String? = null,
  /** Prior committed versions, newest-first excluding the current one, when included. */
  val revisionHistory: List<ExportAnswerRevision> = emptyList(),
)

/** The answer state to render for a question in the export. */
enum class ExportAnswerStatus {
  Answered,
  Draft,
  Unanswered,
  Ignored,
  Omitted,
}

/** One committed answer revision entry for export. */
data class ExportAnswerRevision(
  val text: String,
  val createdAt: String,
)
