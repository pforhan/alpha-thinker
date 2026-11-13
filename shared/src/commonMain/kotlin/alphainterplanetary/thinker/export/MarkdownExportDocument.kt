package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.phases.Phase

/** Markdown implementation of [ExportDocument]. */
class MarkdownExportDocument : ExportDocument {
  private val out = StringBuilder()
  private var needsSeparator = false

  override fun documentHeader(title: String, synopsis: String): ExportDocument = apply {
    val heading = title.ifBlank { "Project Export" }
    out.appendLine("# ${escapeInline(heading)}")
    out.appendLine()
    out.appendLine("## Overview")
    out.appendLine()
    if (synopsis.isNotBlank()) {
      out.appendLine(synopsis.trim())
      out.appendLine()
    }
    needsSeparator = false
  }

  override fun phaseTrail(entries: List<PhaseTrailEntry>): ExportDocument = apply {
    out.appendLine("## Phase Trail")
    out.appendLine()
    if (entries.isEmpty()) {
      out.appendLine("_No phases traversed yet._")
      out.appendLine()
      return@apply
    }
    entries.forEach { entry ->
      out.append("- ")
      out.append(entry.traversalIndex)
      out.append(". **")
      out.append(escapeInline(entry.phase.label))
      out.append("** — ")
      out.append(entry.questionCount)
      out.append(if (entry.questionCount == 1) " question, " else " questions, ")
      out.append(entry.resolvedCount)
      out.append(" resolved (")
      out.append(entry.completionPercent)
      out.append("%)")
      out.appendLine()
    }
    out.appendLine()
  }

  override fun beginPhase(phase: Phase): ExportDocument = apply {
    separate()
    out.appendLine("## ${escapeInline(phase.label)}")
    out.appendLine()
    needsSeparator = false
  }

  override fun endPhase(phase: Phase): ExportDocument = apply {
    // Markdown needs no closing token; the hook lets other formats close containers.
  }

  override fun phaseSummary(phase: Phase, summary: String): ExportDocument = apply {
    if (summary.isBlank()) return@apply
    separate()
    out.appendLine("**Phase summary:** ${summary.trim()}")
    out.appendLine()
    needsSeparator = false
  }

  override fun question(block: ExportQuestionBlock): ExportDocument = apply {
    separate()
    out.appendLine("### Q: ${escapeInline(block.questionText)}")
    out.appendLine()
    when (block.status) {
      ExportAnswerStatus.Answered -> {
        out.appendLine("**Answer:**")
        out.appendLine()
        out.appendLine(block.answerText.orEmpty().trimEnd())
        if (!block.answeredAt.isNullOrBlank()) {
          out.appendLine()
          out.appendLine("_Answered: ${escapeInline(block.answeredAt)}_")
        }
        if (block.revisionHistory.isNotEmpty()) {
          out.appendLine()
          out.appendLine("**Previous revisions:**")
          block.revisionHistory.forEach { revision ->
            out.appendLine()
            out.appendLine("- _${escapeInline(revision.createdAt)}_")
            out.appendLine()
            out.appendLine(revision.text.trimEnd())
          }
        }
      }
      ExportAnswerStatus.Draft -> {
        out.appendLine("**Draft:**")
        out.appendLine()
        out.appendLine(block.draftText.orEmpty().trimEnd())
      }
      ExportAnswerStatus.Unanswered -> {
        out.appendLine("_Unanswered_")
      }
      ExportAnswerStatus.Ignored -> {
        out.appendLine("_Ignored_")
      }
      ExportAnswerStatus.Omitted -> {
        out.appendLine("_Answer omitted_")
      }
    }
    out.appendLine()
    needsSeparator = false
  }

  override fun build(): String = out.toString().trimEnd() + "\n"

  private fun separate() {
    if (needsSeparator) out.appendLine()
  }

  private fun escapeInline(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n')
}
