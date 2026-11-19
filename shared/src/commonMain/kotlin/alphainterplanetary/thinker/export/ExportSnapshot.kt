package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.model.Project
import kotlinx.serialization.Serializable

/**
 * Schema version for [ExportSnapshot]. Bump when the snapshot shape or semantics
 * change in a way that affects storage or interchange compatibility.
 */
const val EXPORT_SNAPSHOT_SCHEMA_VERSION: Int = 2

/**
 * Canonical, versioned snapshot of a project's export-relevant state at a known
 * revision.
 *
 * This is rich project content (not a rendered document): render-time
 * [alphainterplanetary.thinker.model.ExportConfig] filters (ignored/unanswered/
 * drafts/revision history), output format, and output name are applied when
 * rendering so they can change without rebuilding the snapshot. A snapshot is
 * reusable while the project revision is unchanged; project content changes
 * (answers, drafts, questions, rounds/phases, revisions) invalidate it.
 *
 * Per-phase summaries are intentionally not embedded: they are expensive,
 * intent/model-specific derived artifacts resolved via the summary cache/pipeline.
 *
 * Compatibility: additive fields carry defaults so older snapshots that omit them
 * (or readers that ignore unknown fields) remain usable; [schemaVersion] records
 * the writer's snapshot shape.
 */
@Serializable
data class ExportSnapshot(
  val schemaVersion: Int = EXPORT_SNAPSHOT_SCHEMA_VERSION,
  val projectId: String,
  val title: String,
  val synopsis: String,
  val status: String = "Draft",
  val createdAtEpochMs: Long = 0L,
  /** Project revision at capture time ([Project.updatedAt], epoch milliseconds). */
  val revisionUpdatedAtEpochMs: Long,
  /** Traversed phases in export order with their canonical question records. */
  val phases: List<ExportPhaseSnapshot>,
  /** Rounds in project order, included for portable interchange fidelity. */
  val rounds: List<ExportRoundSnapshot> = emptyList(),
) {
  companion object {
    /** Builds a canonical snapshot of [project]'s current export-relevant state. */
    fun from(project: Project): ExportSnapshot {
      val phaseLookup = project.phaseLookup()
      val questionsByPhase = project.questions
        .sortedBy { it.timestamp }
        .groupBy { phaseLookup(it) }
        .toMutableMap()

      val phaseContent = project.allPhaseContent().sortedBy { it.phase.order }
      // Prefer PhaseQuestions as the visited-phase source, but include any phases
      // evidenced by rounds or question phase resolution, matching export traversal
      // semantics (e.g. round-only phases with no questions, and defensive coverage
      // for questions not represented by round-derived content, such as future
      // global/user-defined questions without normal round linkage).
      phaseContent.forEach { content ->
        questionsByPhase[content.phase] = questionsByPhase[content.phase]
          ?: content.questions.sortedBy { it.timestamp }
      }

      val traversedPhases = (
        phaseContent.map { it.phase } +
          project.rounds.map { it.phase } +
          questionsByPhase.keys
        ).distinct().sortedBy { it.order }

      return ExportSnapshot(
        projectId = project.id,
        title = project.title,
        synopsis = project.synopsis,
        status = project.status,
        createdAtEpochMs = project.createdAt.toEpochMilliseconds(),
        revisionUpdatedAtEpochMs = project.updatedAt.toEpochMilliseconds(),
        phases = traversedPhases.map { phase ->
          ExportPhaseSnapshot(
            phaseKey = phase.key,
            phaseLabel = phase.label,
            phaseOrder = phase.order,
            questions = questionsByPhase[phase].orEmpty().map { it.toSnapshotRecord() },
          )
        },
        rounds = project.rounds.map { it.toSnapshotRecord() },
      )
    }
  }
}

/** One round's canonical state, for portable interchange and fidelity. */
@Serializable
data class ExportRoundSnapshot(
  val id: String,
  val projectId: String,
  val phaseKey: String,
  val roundNumber: Int,
  val origin: String,
  val startedAtEpochMs: Long,
  val completedAtEpochMs: Long? = null,
  val outcome: String,
  val outcomeDetail: String? = null,
)

/** One traversed phase's canonical export content, in stable question order. */
@Serializable
data class ExportPhaseSnapshot(
  val phaseKey: String,
  val phaseLabel: String,
  val phaseOrder: Int,
  val questions: List<ExportQuestionSnapshot>,
)

/**
 * Canonical export state for one question. Stores enough information for renderers
 * to apply [alphainterplanetary.thinker.model.ExportConfig] filters and include/exclude
 * drafts or answer revision history without consulting live project state.
 */
@Serializable
data class ExportQuestionSnapshot(
  val id: String,
  val text: String,
  val timestampEpochMs: Long,
  val roundId: String,
  val ignoredAtEpochMs: Long? = null,
  val compacted: Boolean = false,
  /** Committed answer id, when the question has a current committed answer. */
  val answerId: String? = null,
  val draftText: String? = null,
  val draftUpdatedAtEpochMs: Long? = null,
  /** All committed answer versions (immutable history), in storage order. */
  val answers: List<ExportAnswerSnapshot> = emptyList(),
)

/** One committed answer version in the snapshot. */
@Serializable
data class ExportAnswerSnapshot(
  val id: String,
  val questionId: String,
  val text: String,
  val createdAtEpochMs: Long,
)

private fun alphainterplanetary.thinker.model.Question.toSnapshotRecord(): ExportQuestionSnapshot =
  ExportQuestionSnapshot(
    id = id,
    text = text,
    timestampEpochMs = timestamp.toEpochMilliseconds(),
    roundId = roundId,
    ignoredAtEpochMs = ignoredAt?.toEpochMilliseconds(),
    compacted = compacted,
    answerId = answerId,
    draftText = draftText,
    draftUpdatedAtEpochMs = draftUpdatedAt?.toEpochMilliseconds(),
    answers = answers.map { answer ->
      ExportAnswerSnapshot(
        id = answer.id,
        questionId = answer.questionId,
        text = answer.text,
        createdAtEpochMs = answer.createdAt.toEpochMilliseconds(),
      )
    },
  )

private fun alphainterplanetary.thinker.model.Round.toSnapshotRecord(): ExportRoundSnapshot =
  ExportRoundSnapshot(
    id = id,
    projectId = projectId,
    phaseKey = phase.key,
    roundNumber = roundNumber,
    origin = origin.name,
    startedAtEpochMs = startedAt.toEpochMilliseconds(),
    completedAtEpochMs = completedAt?.toEpochMilliseconds(),
    outcome = outcome.name,
    outcomeDetail = outcomeDetail,
  )
