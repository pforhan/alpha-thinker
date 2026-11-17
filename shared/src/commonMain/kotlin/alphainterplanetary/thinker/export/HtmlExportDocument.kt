package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.phases.Phase

private const val string = """      :root {
        color-scheme: light dark;
        --bg: #ffffff;
        --fg: #1b1b1b;
        --muted: #5f6368;
        --border: #d7dce1;
        --surface: #f7f8fa;
        --accent: #245ea8;
      }
      @media (prefers-color-scheme: dark) {
        :root {
          --bg: #111315;
          --fg: #e7e9ea;
          --muted: #a4adb7;
          --border: #30363d;
          --surface: #191c1f;
          --accent: #7db2ff;
        }
      }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        background: var(--bg);
        color: var(--fg);
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        line-height: 1.6;
      }
      .container {
        max-width: 52rem;
        margin: 0 auto;
        padding: 1.25rem 1rem 2.5rem;
      }
      h1, h2, h3 { line-height: 1.25; margin: 0 0 0.5rem; }
      h1 { font-size: 1.9rem; margin-bottom: 0.75rem; }
      h2 { font-size: 1.35rem; margin-top: 1.5rem; }
      h3 { font-size: 1.05rem; margin-top: 1rem; }
      p { margin: 0.5rem 0; }
      .doc-header, .phase-trail, .phase {
        margin-bottom: 1.5rem;
      }
      .phase {
        border-top: 1px solid var(--border);
        padding-top: 0.5rem;
      }
      .question {
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: 0.5rem;
        padding: 0.75rem 0.9rem;
        margin: 0.9rem 0;
      }
      .trail-list { margin: 0.5rem 0 0; padding-left: 1.4rem; }
      .trail-list li { margin: 0.3rem 0; }
      .label { margin-bottom: 0.2rem; }
      .meta, .muted { color: var(--muted); }
      .revisions {
        margin-top: 0.75rem;
        border-top: 1px dashed var(--border);
        padding-top: 0.5rem;
      }
      .revisions summary {
        cursor: pointer;
        color: var(--accent);
        font-weight: 600;
      }
      .revision {
        margin-top: 0.6rem;
        padding-left: 0.6rem;
        border-left: 2px solid var(--border);
      }
      @media (min-width: 720px) {
        .container { padding: 2rem 1.5rem 3rem; }
      }"""

/** Self-contained HTML implementation of [ExportDocument]. */
class HtmlExportDocument : ExportDocument {
  private val out = StringBuilder()
  private val phaseQuestionCounts = ArrayDeque<Int>()
  private var phaseHasQuestions = false

  override fun beginDocument(snapshot: ExportSnapshot): ExportDocument = apply {
    val title = snapshot.title.ifBlank { snapshot.synopsis }.ifBlank { "Project Export" }
    out.appendLine("<!DOCTYPE html>")
    out.appendLine("""<html lang="en">""")
    out.appendLine("<head>")
    out.appendLine("""  <meta charset="utf-8">""")
    out.appendLine("""  <meta name="viewport" content="width=device-width, initial-scale=1">""")
    out.append("  <title>").append(escapeHtml(title)).appendLine("</title>")
    out.appendLine(STYLE)
    out.appendLine("</head>")
    out.appendLine("<body>")
    out.appendLine("""  <main class="container">""")
  }

  override fun documentHeader(title: String, synopsis: String): ExportDocument = apply {
    val heading = title.ifBlank { "Project Export" }
    out.appendLine("""    <header class="doc-header">""")
    out.append("      <h1>").append(escapeHtml(heading)).appendLine("</h1>")
    out.appendLine("""      <section class="overview" aria-labelledby="overview-heading">""")
    out.appendLine("""        <h2 id="overview-heading">Overview</h2>""")
    if (synopsis.isNotBlank()) {
      appendParagraphs(synopsis.trim(), indent = "        ")
    }
    out.appendLine("      </section>")
    out.appendLine("    </header>")
  }

  override fun phaseTrail(entries: List<PhaseTrailEntry>): ExportDocument = apply {
    out.appendLine("""    <section class="phase-trail" aria-labelledby="phase-trail-heading">""")
    out.appendLine("""      <h2 id="phase-trail-heading">Phase Trail</h2>""")
    if (entries.isEmpty()) {
      out.appendLine("""      <p class="muted"><em>No phases traversed yet.</em></p>""")
    } else {
      out.appendLine("""      <ol class="trail-list">""")
      entries.forEach { entry ->
        val questionLabel = if (entry.questionCount == 1) "question" else "questions"
        out.append("""        <li><strong>""")
          .append(escapeHtml(entry.phase.label))
          .append("</strong> — ")
          .append(entry.questionCount)
          .append(' ')
          .append(questionLabel)
          .append(", ")
          .append(entry.resolvedCount)
          .append(" resolved (")
          .append(entry.completionPercent)
          .append("%)</li>")
          .appendLine()
      }
      out.appendLine("      </ol>")
    }
    out.appendLine("    </section>")
  }

  override fun beginPhase(phase: Phase): ExportDocument = apply {
    val id = "phase-${escapeId(phase.key)}"
    out.appendLine("""    <section class="phase" id="$id">""")
    out.append("""      <h2>""").append(escapeHtml(phase.label)).appendLine("</h2>")
    phaseQuestionCounts.addLast(0)
    phaseHasQuestions = false
  }

  override fun endPhase(phase: Phase): ExportDocument = apply {
    if (phaseQuestionCounts.removeLastOrNull() == 0) {
      out.appendLine("""      <p class="muted"><em>No questions to show.</em></p>""")
    }
    out.appendLine("    </section>")
  }

  override fun phaseSummary(phase: Phase, summary: String): ExportDocument = apply {
    if (summary.isBlank()) return@apply
    out.appendLine("""      <section class="phase-summary">""")
    out.append("""        <p><strong>Phase summary:</strong> """)
      .append(escapeHtml(summary.trim()))
      .appendLine("</p>")
    out.appendLine("      </section>")
  }

  override fun question(block: ExportQuestionBlock): ExportDocument = apply {
    if (phaseQuestionCounts.isNotEmpty()) {
      val count = phaseQuestionCounts.removeLast() + 1
      phaseQuestionCounts.addLast(count)
    }
    out.appendLine("""      <article class="question">""")
    out.append("""        <h3>Q: """).append(escapeHtml(block.questionText)).appendLine("</h3>")
    when (block.status) {
      ExportAnswerStatus.Answered -> {
        out.appendLine("""        <div class="answer">""")
        out.appendLine("""          <p class="label"><strong>Answer</strong></p>""")
        appendParagraphs(block.answerText.orEmpty().trimEnd(), indent = "          ")
        if (!block.answeredAt.isNullOrBlank()) {
          out.append("""          <p class="meta"><em>Answered: """)
            .append(escapeHtml(block.answeredAt))
            .appendLine("</em></p>")
        }
        if (block.revisionHistory.isNotEmpty()) {
          out.appendLine("""          <details class="revisions">""")
          out.appendLine("""            <summary>Previous revisions</summary>""")
          block.revisionHistory.forEach { revision ->
            out.appendLine("""            <div class="revision">""")
            out.append("""              <p class="meta"><em>""")
              .append(escapeHtml(revision.createdAt))
              .appendLine("</em></p>")
            appendParagraphs(revision.text.trimEnd(), indent = "              ")
            out.appendLine("            </div>")
          }
          out.appendLine("          </details>")
        }
        out.appendLine("        </div>")
      }
      ExportAnswerStatus.Draft -> {
        out.appendLine("""        <div class="draft">""")
        out.appendLine("""          <p class="label"><strong>Draft</strong></p>""")
        appendParagraphs(block.draftText.orEmpty().trimEnd(), indent = "          ")
        out.appendLine("        </div>")
      }
      ExportAnswerStatus.Unanswered -> {
        out.appendLine("""        <p class="muted"><em>Unanswered</em></p>""")
      }
      ExportAnswerStatus.Ignored -> {
        out.appendLine("""        <p class="muted"><em>Ignored</em></p>""")
      }
      ExportAnswerStatus.Omitted -> {
        out.appendLine("""        <p class="muted"><em>Answer omitted</em></p>""")
      }
    }
    out.appendLine("      </article>")
  }

  override fun endDocument(): ExportDocument = apply {
    out.appendLine("  </main>")
    out.appendLine("</body>")
    out.appendLine("</html>")
  }

  override fun build(): String = out.toString()

  private fun appendParagraphs(text: String, indent: String) {
    if (text.isBlank()) return
    val paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
    paragraphs.forEach { paragraph ->
      val lines = paragraph.trimEnd().lines()
      if (lines.all { it.isBlank() }) return@forEach
      out.append(indent).append("<p>")
      lines.forEachIndexed { index, line ->
        if (index > 0) out.append("<br>")
        out.append(escapeHtml(line))
      }
      out.appendLine("</p>")
    }
  }

  private fun escapeHtml(text: String): String = buildString(text.length) {
    text.forEach { ch ->
      when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(ch)
      }
    }
  }

  private fun escapeId(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').ifBlank { "section" }

  private companion object {
    val STYLE = """
      <style>
      :root {
        color-scheme: light dark;
        --bg: #ffffff;
        --fg: #1b1b1b;
        --muted: #5f6368;
        --border: #d7dce1;
        --surface: #f7f8fa;
        --accent: #245ea8;
      }
      @media (prefers-color-scheme: dark) {
        :root {
          --bg: #111315;
          --fg: #e7e9ea;
          --muted: #a4adb7;
          --border: #30363d;
          --surface: #191c1f;
          --accent: #7db2ff;
        }
      }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        background: var(--bg);
        color: var(--fg);
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        line-height: 1.6;
      }
      .container {
        max-width: 52rem;
        margin: 0 auto;
        padding: 1.25rem 1rem 2.5rem;
      }
      h1, h2, h3 { line-height: 1.25; margin: 0 0 0.5rem; }
      h1 { font-size: 1.9rem; margin-bottom: 0.75rem; }
      h2 { font-size: 1.35rem; margin-top: 1.5rem; }
      h3 { font-size: 1.05rem; margin-top: 1rem; }
      p { margin: 0.5rem 0; }
      .doc-header, .phase-trail, .phase {
        margin-bottom: 1.5rem;
      }
      .phase {
        border-top: 1px solid var(--border);
        padding-top: 0.5rem;
      }
      .question {
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: 0.5rem;
        padding: 0.75rem 0.9rem;
        margin: 0.9rem 0;
      }
      .trail-list { margin: 0.5rem 0 0; padding-left: 1.4rem; }
      .trail-list li { margin: 0.3rem 0; }
      .label { margin-bottom: 0.2rem; }
      .meta, .muted { color: var(--muted); }
      .revisions {
        margin-top: 0.75rem;
        border-top: 1px dashed var(--border);
        padding-top: 0.5rem;
      }
      .revisions summary {
        cursor: pointer;
        color: var(--accent);
        font-weight: 600;
      }
      .revision {
        margin-top: 0.6rem;
        padding-left: 0.6rem;
        border-left: 2px solid var(--border);
      }
      @media (min-width: 720px) {
        .container { padding: 2rem 1.5rem 3rem; }
      }
      </style>
    """.trimIndent()
  }
}