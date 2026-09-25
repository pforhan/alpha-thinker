package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.tasks.TaskKind
import kotlin.time.Duration

/**
 * One logical activity from the log: the [LogEntry] rows sharing an
 * [LogEntry.activityId], ordered by id. Entries without an activity id each
 * become their own single-row activity.
 *
 * This is the read model the viewer builds from (see
 * [groupByActivity]); the "interesting bits" — category, source, a synthesized
 * summary, an elapsed duration, whether anything failed — are derived here so
 * the view stays a thin projection over the raw log. Rows are parsed through
 * the [LogMarkers] conventions the writers ([LogContext], [TaskRunner]) file.
 */
class ActivityRecord private constructor(
  val activityId: String,
  val entries: List<LogEntry>,
) {
  /** The newest row in the activity (highest id), for the headline. */
  val latest: LogEntry
    get() = entries.maxBy { it.id ?: 0L }

  /** The activity's topic — all rows of an activity share one category. */
  val category: LogCategory
    get() = entries.first().category

  /** The activity's project — the first populated project id across its rows, if any. */
  val projectId: String?
    get() = entries.firstNotNullOfOrNull { it.projectId }

  /** The first populated source across the activity's rows. */
  val source: LogSource?
    get() = entries.firstNotNullOfOrNull { it.source }

  /**
   * Elapsed time across the activity, derived solely from the clumped rows'
   * timestamps: null for a single-row activity or one whose rows all share a
   * timestamp (e.g. a synchronous pair written back-to-back).
   */
  val duration: Duration?
    get() {
      val stamps = entries.map { it.timestamp }
      if (stamps.size < 2) return null
      return (stamps.max() - stamps.min()).takeIf { it > Duration.ZERO }
    }

  /** Whether any row failed or was cancelled (`failed:`/`cancelled`). */
  val hasError: Boolean
    get() = entries.any { it.isFailureLine() }

  /**
   * A human one-line summary of the activity's outcome, synthesized from the
   * rows: a terminal failure headlines with its topic ("Initial question
   * generation failed: model exploded"), a known interaction reads as a canned
   * message ("No more questions available in phase", "Generated 4 follow-up
   * questions", "Recommended title: Rocketship"), and anything unrecognized
   * falls back to the last response/terminal row's text — or the newest row
   * when no response surfaced (a plain or in-progress activity). The expanded
   * rows stay verbatim; only this headline is reworded.
   */
  val summary: String
    get() {
      failureHeadline()?.let { return it }

      capability()?.let { answer ->
        val suffix = answer.phase?.let { " ($it)" }.orEmpty()
        return if (answer.can) {
          "More questions available in phase$suffix"
        } else {
          "No more questions available in phase$suffix"
        }
      }

      val kind = taskKind()
      if (kind?.isGeneration == true) {
        batchCount()?.let { count ->
          val flavor = if (kind == TaskKind.InitialQuestions) "initial " else "follow-up "
          return batchSummary(count, flavor)
        }
      } else if (category == LogCategory.QuestionGeneration) {
        batchCount()?.let { count -> return batchSummary(count, "") }
      }

      titleSummary()?.let { return it }

      if (kind != null && succeededRow() != null) {
        return "${kind.activityLabel()} succeeded"
      }

      return lastResponseLine()?.log ?: latest.log
    }

  /** The question count a batch `response:` row reports (`N questions`), if any. */
  private fun batchCount(): Int? {
    val count = batchCountRegex
      .find(entries.lastOrNull { it.isDetailResponse() }?.log.orEmpty())
      ?.groupValues
      ?.get(1)
      ?.toIntOrNull() ?: return null
    return count
  }

  /**
   * The last terminal failure/cancellation as a headline — the topic's phrase
   * when known, the raw line otherwise.
   */
  private fun failureHeadline(): String? {
    val line = entries.lastOrNull { it.isFailureLine() } ?: return null
    val subject = taskKind()?.activityLabel()
    return when {
      line.log == LogMarkers.Cancelled -> if (subject != null) "$subject cancelled" else line.log
      else -> {
        val message = line.log.removePrefix("${LogMarkers.Failed} ")
        if (subject != null) "$subject failed: $message" else line.log
      }
    }
  }

  /** A parsed capability row: its boolean answer plus the phase label, when the row names one. */
  private data class CapabilityAnswer(
    val can: Boolean,
    val phase: String?,
  )

  /**
   * The last capability answer (a `response: canProduceMore=…` or
   * `succeeded: result=…` row), with the phase label when any candidate row
   * carried it (the lifecycle `succeeded` row never does — the detail
   * `response` row does).
   */
  private fun capability(): CapabilityAnswer? {
    val rows = entries.filter { row ->
      row.isDetailResponse() && row.log.startsWith("${LogMarkers.Response} canProduceMore=") ||
        row.isSucceededRow() && row.log.startsWith("${LogMarkers.Succeeded}: result=")
    }
    val can = capabilityRegex
      .find(rows.lastOrNull()?.log ?: return null)
      ?.groupValues
      ?.get(1)
      ?.toBooleanStrictOrNull() ?: return null
    val phase = rows.firstNotNullOfOrNull { row -> phaseInRegex.find(row.log)?.groupValues?.get(1) }
    return CapabilityAnswer(can, phase)
  }

  /** The last outcome row (a response or terminal marker), for the fallback headline. */
  private fun lastResponseLine(): LogEntry? = entries.lastOrNull { it.isResponseLine() }

  /** The last success terminal row, when the activity resolved cleanly. */
  private fun succeededRow(): LogEntry? = entries.lastOrNull { it.isSucceededRow() }

  /**
   * The kind of task this activity carries, parsed from its `started:` lifecycle
   * row; null when the activity has no lifecycle rows (a standalone detail).
   */
  private fun taskKind(): TaskKind? {
    val label = entries.firstNotNullOfOrNull { row ->
      if (row.log.startsWith(LogMarkers.Started)) row.log.substringAfter(LogMarkers.Started).trim() else null
    } ?: return null
    return TaskKind.entries.firstOrNull { it.name == label }
  }

  /** A title recommendation headline from the activity's `response:` row, if any. */
  private fun titleSummary(): String? {
    val isTitleActivity = taskKind() == TaskKind.TitleRecommendation ||
      category == LogCategory.TitleRecommendation
    if (!isTitleActivity) return null
    val text = entries.lastOrNull { it.isDetailResponse() }
      ?.log
      ?.substringAfter(LogMarkers.Response)
      ?.trim()
      .orEmpty()
    if (text.isEmpty()) return null
    return "Recommended title: $text"
  }

  companion object {
    /**
     * Groups [entries] by [LogEntry.activityId] into activities ordered newest
     * first. Entries without an activity id become single-row activities (keyed
     * by their own id) so they still surface as individual log lines.
     */
    fun groupByActivity(entries: List<LogEntry>): List<ActivityRecord> {
      val grouped = LinkedHashMap<String, MutableList<LogEntry>>()
      entries.forEach { entry ->
        val key = entry.activityId ?: "line:${entry.id ?: standaloneKey(entries, entry)}"
        grouped.getOrPut(key) { mutableListOf() }.add(entry)
      }
      return grouped
        .map { (key, rows) -> ActivityRecord(key, rows.sortedBy { it.id ?: 0L }) }
        .sortedByDescending { it.latest.timestamp }
    }

    /**
     * A stable in-memory key for an entry with neither an activity id nor a
     * persisted id yet (test fixtures): the index it appears at in [entries].
     */
    private fun standaloneKey(entries: List<LogEntry>, entry: LogEntry): String =
      "standalone-${entries.indexOf(entry)}"
  }
}

private val RESPONSE_PREFIXES = listOf(
  LogMarkers.Response,
  LogMarkers.Succeeded,
  LogMarkers.Failed,
  LogMarkers.Cancelled,
)

/** A row that reads as an outcome: a response or a terminal marker. */
private fun LogEntry.isResponseLine(): Boolean =
  RESPONSE_PREFIXES.any { log.startsWith(it) }

/** A row that reads as a failure or cancellation. */
private fun LogEntry.isFailureLine(): Boolean =
  log.startsWith(LogMarkers.Failed) ||
    log == LogMarkers.Cancelled

/** An engine-produced detail row (`response: …`), not a lifecycle terminal. */
private fun LogEntry.isDetailResponse(): Boolean = log.startsWith(LogMarkers.Response)

/** A plain success terminal row (`succeeded` or `succeeded: …`). */
private fun LogEntry.isSucceededRow(): Boolean =
  log == LogMarkers.Succeeded || log.startsWith("${LogMarkers.Succeeded}:")

private val TaskKind.isGeneration: Boolean
  get() = this == TaskKind.InitialQuestions || this == TaskKind.FollowUpQuestions

/** Human phrase for the kind, used in failure/success headlines. */
private fun TaskKind.activityLabel(): String = when (this) {
  TaskKind.InitialQuestions -> "Initial question generation"
  TaskKind.FollowUpQuestions -> "Follow-up question generation"
  TaskKind.TitleRecommendation -> "Title recommendation"
  TaskKind.RemainingInPhase -> "Phase capacity check"
  TaskKind.SynopsisRewrite -> "Synopsis rewrite"
  TaskKind.AutoArchive -> "Auto-archive"
}

/** Canned summary for a produced batch, e.g. "Generated 3 initial questions". */
private fun batchSummary(count: Int, flavor: String): String = when {
  count == 0 -> "No further ${flavor}questions generated"
  count == 1 -> "Generated 1 ${flavor}question"
  else -> "Generated $count ${flavor}questions"
}

private val batchCountRegex = Regex("""(\d+) questions?""")

/** Extracts the boolean from a capability row, e.g. `canProduceMore=true, phase=Scope & Goals`. */
private val capabilityRegex = Regex("""(?:canProduceMore|result)=(true|false)""")

/** Extracts the phase label trailing a capability row, e.g. the `Scope & Goals` in `phase=Scope & Goals`. */
private val phaseInRegex = Regex("""phase=(.+)""")