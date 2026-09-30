package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.util.now

/**
 * A scoped writer for the rows of one activity in the app-wide log. Obtained
 * from [ActivityLogger.context] with the activity's fixed sibling fields
 * ([activityId], [category], [source], [projectId]) and filed once; callers
 * phrase only the content and the row is rendered under the right [LogMarkers]
 * prefix here — so a sequence filed across several calls ("started" →
 * interaction rows → one terminal row) can't drift from what the read model
 * ([ActivityRecord]) parses.
 *
 * Lifecycle: an activity opens with [started] (or just interaction rows for a
 * standalone detail), then any number of [prompt]/[response] rows, then exactly
 * one terminal row ([closeSucceeded]/[closeFailed]/[closeCancelled]).
 * The first terminal method closes the context; a later append fails fast with
 * [IllegalStateException] instead of silently writing a malformed activity.
 * Writers may also create a fresh context per launch — the guard only fires on
 * a genuine post-outcome write, not on a reused name.
 *
 * [response] and [closeFailed] also take the verbatim payload behind their
 * message ([LogEntry.raw]) — the unparsed model reply — which rides along on
 * the row rather than becoming a row of its own, so the outcome rows stay one
 * line each in the viewer.
 */
class LogContext internal constructor(
  private val log: ActivityLogger,
  val activityId: String,
  val category: LogCategory,
  val source: LogSource?,
  val projectId: String? = null,
) {
  private var closed = false

  /** A lifecycle opening row, e.g. `started: InitialQuestions`. */
  suspend fun started(label: String) = file("${LogMarkers.Started} $label")

  /** A success terminal row (`succeeded`). */
  suspend fun closeSucceeded() = terminal(LogMarkers.Succeeded)

  /**
   * A failure terminal row rendering `failed: $message` — the single failure
   * marker (previously a separate `error:` variant; the read model treats the
   * two rows identically, so there's only one writer path). [raw] is the
   * verbatim payload behind the failure (an unreadable model reply, say), kept
   * so the popup can show what actually came back.
   */
  suspend fun closeFailed(message: String, raw: String? = null) =
    terminal("${LogMarkers.Failed} $message", raw)

  /** A cancellation terminal row (`cancelled`). */
  suspend fun closeCancelled() = terminal(LogMarkers.Cancelled)

  /** A `prompt:` detail row (a prompt, verbatim). */
  suspend fun prompt(text: String) = file("${LogMarkers.Prompt} $text")

  /**
   * A `response:` detail row (the engine's produced outcome), optionally with
   * the verbatim payload it was read from (see [LogEntry.raw]).
   */
  suspend fun response(text: String, raw: String? = null) = file("${LogMarkers.Response} $text", raw)

  /** Files a row verbatim — the escape hatch for text that has no marker. */
  suspend fun append(text: String) = file(text)

  private fun checkOpen() {
    check(!closed) { "activity $activityId already terminated; no further rows accepted" }
  }

  private suspend fun file(text: String, raw: String? = null) {
    checkOpen()
    log.append(
      LogEntry(
        projectId = projectId,
        activityId = activityId,
        category = category,
        source = source,
        log = text,
        timestamp = now(),
        raw = raw,
      )
    )
  }

  private suspend fun terminal(text: String, raw: String? = null) {
    file(text, raw)
    closed = true
  }
}
