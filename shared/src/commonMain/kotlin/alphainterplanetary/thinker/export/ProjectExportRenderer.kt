package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.model.ExportConfig
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase

/**
 * Renders a project into an [ExportDocument] using [config].
 *
 * Output is phase-grouped (no round structure) with an opening phase-trail
 * summary. [phaseSummaries] provides optional per-phase summary text rendered
 * when [ExportConfig.includeSummaries] is enabled.
 */
class ProjectExportRenderer(
  private val config: ExportConfig,
) {
  fun render(
    project: Project,
    document: ExportDocument = MarkdownExportDocument(),
    phaseSummaries: Map<Phase, String> = emptyMap(),
  ): String {
    val title = project.title.ifBlank { project.synopsis }
    document.documentHeader(title = title, synopsis = project.synopsis)

    val phaseLookup = project.phaseLookup()
    val questionsByPhase = groupedQuestionsByPhase(project, phaseLookup)
    val traversedPhases = traversedPhases(project, questionsByPhase)

    val blocksByPhase = traversedPhases.associateWith { phase ->
      questionsByPhase[phase].orEmpty().mapNotNull { toBlock(it) }
    }
    val summariesByPhase = traversedPhases.associateWith { phase ->
      phaseSummaries[phase]?.takeIf { config.includeSummaries && it.isNotBlank() }
    }
    val phasesInExport = traversedPhases.filter { phase ->
      blocksByPhase[phase].orEmpty().isNotEmpty() || summariesByPhase[phase] != null
    }

    document.phaseTrail(
      phasesInExport.mapIndexed { index, phase ->
        val blocks = blocksByPhase[phase].orEmpty()
        val resolved = blocks.count {
          it.status == ExportAnswerStatus.Answered || it.status == ExportAnswerStatus.Ignored
        }
        val total = blocks.size
        PhaseTrailEntry(
          phase = phase,
          traversalIndex = index + 1,
          questionCount = total,
          resolvedCount = resolved,
          completionPercent = if (total == 0) 0 else (resolved * 100) / total,
        )
      }
    )

    phasesInExport.forEach { phase ->
      document.beginPhase(phase)
      summariesByPhase[phase]?.let { summary ->
        document.phaseSummary(phase, summary)
      }
      blocksByPhase[phase].orEmpty().forEach { document.question(it) }
      document.endPhase(phase)
    }

    return document.build()
  }

  private fun groupedQuestionsByPhase(
    project: Project,
    phaseLookup: (Question) -> Phase,
  ): Map<Phase, List<Question>> = project.questions
    .sortedBy { it.timestamp }
    .groupBy { phaseLookup(it) }

  /**
   * Phases traversed in this project, in phase-library order. A phase is
   * included when it has at least one round or any question mapped to it.
   */
  private fun traversedPhases(
    project: Project,
    questionsByPhase: Map<Phase, List<Question>>,
  ): List<Phase> {
    val fromRounds = project.rounds.map { it.phase }
    val fromQuestions = questionsByPhase.keys
    val present = (fromRounds + fromQuestions).toSet()
    return present.sortedBy { it.order }
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
      // Compacted copies mark an omitted answer; export stored state semantics
      // by rendering an explicit omitted state when such a question is present.
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
}
