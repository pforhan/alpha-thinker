package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.model.ExportConfig
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Instant

/**
 * Renders an export into an [ExportDocument] using [config].
 *
 * Prefers rendering from a canonical [ExportSnapshot] (build once, render many)
 * with resolved per-phase summaries; [render] from [Project] builds a snapshot for
 * compatibility. Output is phase-grouped with an opening phase-trail summary.
 * [phaseSummaries] provides optional per-phase summary text (Export intent by
 * default) rendered when [ExportConfig.includeSummaries] is enabled.
 */
class ProjectExportRenderer(
  private val config: ExportConfig,
) {
  fun render(
    project: Project,
    document: ExportDocument = MarkdownExportDocument(),
    phaseSummaries: Map<Phase, String> = emptyMap(),
  ): String = render(ExportSnapshot.from(project), document, phaseSummaries)

  fun render(
    snapshot: ExportSnapshot,
    document: ExportDocument = MarkdownExportDocument(),
    phaseSummaries: Map<Phase, String> = emptyMap(),
    phaseSummariesByKey: Map<String, String> = emptyMap(),
  ): String {
    val title = snapshot.title.ifBlank { snapshot.synopsis }
    document.documentHeader(title = title, synopsis = snapshot.synopsis)

    val phases = snapshot.phases.mapNotNull { phaseSnapshot ->
      val phase = Phase.fromKey(phaseSnapshot.phaseKey) ?: return@mapNotNull null
      ResolvedPhase(
        phase = phase,
        snapshot = phaseSnapshot,
        blocks = phaseSnapshot.questions.mapNotNull { toBlock(it) },
        summary = resolveSummary(phase, phaseSnapshot.phaseKey, phaseSummaries, phaseSummariesByKey),
      )
    }

    val phasesInExport = phases.filter { it.blocks.isNotEmpty() || it.summary != null }

    document.phaseTrail(
      phasesInExport.mapIndexed { index, entry ->
        val resolved = entry.blocks.count {
          it.status == ExportAnswerStatus.Answered || it.status == ExportAnswerStatus.Ignored
        }
        val total = entry.blocks.size
        PhaseTrailEntry(
          phase = entry.phase,
          traversalIndex = index + 1,
          questionCount = total,
          resolvedCount = resolved,
          completionPercent = if (total == 0) 0 else (resolved * 100) / total,
        )
      }
    )

    phasesInExport.forEach { entry ->
      document.beginPhase(entry.phase)
      entry.summary?.let { document.phaseSummary(entry.phase, it) }
      entry.blocks.forEach { document.question(it) }
      document.endPhase(entry.phase)
    }

    return document.build()
  }

  private fun resolveSummary(
    phase: Phase,
    phaseKey: String,
    phaseSummaries: Map<Phase, String>,
    phaseSummariesByKey: Map<String, String>,
  ): String? {
    if (!config.includeSummaries) return null
    val summary = phaseSummaries[phase] ?: phaseSummariesByKey[phaseKey]
    return summary?.takeIf { it.isNotBlank() }
  }

  private fun toBlock(question: ExportQuestionSnapshot): ExportQuestionBlock? {
    if (question.ignoredAtEpochMs != null) {
      if (!config.includeIgnoredQuestions) return null
      return ExportQuestionBlock(
        questionText = question.text,
        status = ExportAnswerStatus.Ignored,
      )
    }

    if (question.compacted) {
      return ExportQuestionBlock(
        questionText = question.text,
        status = ExportAnswerStatus.Omitted,
      )
    }

    if (question.answerId != null) {
      val current = question.answers.find { it.id == question.answerId }
        ?: question.answers.lastOrNull()
      if (current != null) {
        val revisions = if (config.includeAnswerRevisionHistory) {
          question.answers
            .filter { it.id != current.id }
            .sortedByDescending { it.createdAtEpochMs }
            .map { ExportAnswerRevision(text = it.text, createdAt = formatEpochMs(it.createdAtEpochMs)) }
        } else {
          emptyList()
        }
        return ExportQuestionBlock(
          questionText = question.text,
          status = ExportAnswerStatus.Answered,
          answerText = current.text,
          answeredAt = formatEpochMs(current.createdAtEpochMs),
          revisionHistory = revisions,
        )
      }
    }

    if (!question.draftText.isNullOrBlank()) {
      if (!config.includeDrafts) {
        return if (config.includeUnansweredQuestions) {
          ExportQuestionBlock(questionText = question.text, status = ExportAnswerStatus.Unanswered)
        } else {
          null
        }
      }
      return ExportQuestionBlock(
        questionText = question.text,
        status = ExportAnswerStatus.Draft,
        draftText = question.draftText,
      )
    }

    if (!config.includeUnansweredQuestions) return null
    return ExportQuestionBlock(
      questionText = question.text,
      status = ExportAnswerStatus.Unanswered,
    )
  }

  private fun toBlock(question: Question): ExportQuestionBlock? {
    if (question.isIgnored) {
      if (!config.includeIgnoredQuestions) return null
      return ExportQuestionBlock(
        questionText = question.text,
        status = ExportAnswerStatus.Ignored,
      )
    }

    if (question.compacted) {
      return ExportQuestionBlock(
        questionText = question.text,
        status = ExportAnswerStatus.Omitted,
      )
    }

    val current = question.currentAnswer
    if (current != null) {
      val revisions = if (config.includeAnswerRevisionHistory) {
        question.answers
          .filter { it.id != current.id }
          .sortedByDescending { it.createdAt }
          .map { ExportAnswerRevision(text = it.text, createdAt = it.createdAt.toString()) }
      } else {
        emptyList()
      }
      return ExportQuestionBlock(
        questionText = question.text,
        status = ExportAnswerStatus.Answered,
        answerText = current.text,
        answeredAt = current.createdAt.toString(),
        revisionHistory = revisions,
      )
    }

    if (question.isDraft) {
      if (!config.includeDrafts) {
        return if (config.includeUnansweredQuestions) {
          ExportQuestionBlock(questionText = question.text, status = ExportAnswerStatus.Unanswered)
        } else {
          null
        }
      }
      return ExportQuestionBlock(
        questionText = question.text,
        status = ExportAnswerStatus.Draft,
        draftText = question.draftText,
      )
    }

    if (!config.includeUnansweredQuestions) return null
    return ExportQuestionBlock(
      questionText = question.text,
      status = ExportAnswerStatus.Unanswered,
    )
  }

  private fun formatEpochMs(epochMs: Long): String =
    Instant.fromEpochMilliseconds(epochMs).toString()

  private data class ResolvedPhase(
    val phase: Phase,
    val snapshot: ExportPhaseSnapshot,
    val blocks: List<ExportQuestionBlock>,
    val summary: String?,
  )
}
