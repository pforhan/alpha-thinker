package alphainterplanetary.thinker.activitylog

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One logical activity from the log: the [LogEntry] rows sharing an
 * [LogEntry.activityId], ordered by id. Entries without an activity id each
 * become their own single-row activity.
 *
 * This is the read model the viewer builds from (see
 * [groupByActivity]); the "interesting bits" — category, source, a synthesized
 * summary, an elapsed duration, whether anything failed — are derived here so
 * the view stays a thin projection over the raw log.
 */
class LogActivity private constructor(
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

  /** Whether any row failed or was cancelled (`failed:`/`error:`/`cancelled`). */
  val hasError: Boolean
    get() = entries.any { it.isFailureLine() }

  /**
   * One-line summary of the activity's outcome: the last response/terminal
   * row's text, or the newest row's text when no response surfaced (a plain or
   * in-progress activity).
   */
  val summary: String
    get() = entries.lastOrNull { it.isResponseLine() }?.log ?: latest.log

  companion object {
    /**
     * Groups [entries] by [LogEntry.activityId] into activities ordered newest
     * first. Entries without an activity id become single-row activities (keyed
     * by their own id) so they still surface as individual log lines.
     */
    fun groupByActivity(entries: List<LogEntry>): List<LogActivity> {
      val grouped = LinkedHashMap<String, MutableList<LogEntry>>()
      entries.forEach { entry ->
        val key = entry.activityId ?: "line:${entry.id ?: standaloneKey(entries, entry)}"
        grouped.getOrPut(key) { mutableListOf() }.add(entry)
      }
      return grouped
        .map { (key, rows) -> LogActivity(key, rows.sortedBy { it.id ?: 0L }) }
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

private val RESPONSE_PREFIXES = listOf("response:", "succeeded", "failed:", "cancelled", "error:")

/** A row that reads as an outcome: a response or a terminal marker. */
private fun LogEntry.isResponseLine(): Boolean =
  RESPONSE_PREFIXES.any { log.startsWith(it) }

/** A row that reads as a failure or cancellation. */
private fun LogEntry.isFailureLine(): Boolean =
  log.startsWith("failed:") || log.startsWith("error:") || log == "cancelled"