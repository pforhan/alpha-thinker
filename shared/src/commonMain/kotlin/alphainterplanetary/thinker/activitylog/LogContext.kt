package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.repository.ContextCheck
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
 * line each in the viewer. A blank payload is dropped rather than filed: there
 * is nothing to show, and an empty payload reads as a truncated one.
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

  /**
   * The generation's `context checked:` note: the transcript's estimated cost
   * and the budget it was measured against, in the same "tokens … budget" words
   * the near-limit dialog uses. Obtained from a fresh standalone context — the
   * check is taken before any task is enqueued, so it has no task activity to
   * join — and the note is plain text, so the read model's started/terminal
   * parsing is unaffected.
   */
  suspend fun contextChecked(check: ContextCheck) = append(contextCheckText(check))

  /**
   * The generation's round note — `Action: more questions` for a "Get more
   * questions" round, `Action: advance phase` for a wrap-up into a new phase,
   * each under a `Phase:` line. Might be a standalone context sharing the task's
   * activity without its lifecycle, and is plain text under the
   * [LogMarkers.Action] prefix, so the read model's started/terminal parsing is
   * unaffected and [ActivityRecord] can headline an advance off it. Nothing is
   * filed for a round with no action (the project's opening round).
   */
  suspend fun round(round: Round) {
    val action = roundAction(round) ?: return
    append("${LogMarkers.Action} $action\n${LogMarkers.Phase} ${round.phase.label}")
  }

  /**
   * The context's "compacted … dropped" note: how many answers the transcript
   * had to drop to fit the budget, which the engine (and its prompt) never sees.
   * Deliberately phrased so the near-limit compaction choice can extend it
   * without a breaking change; uses the same "answers" words the summarized note
   * does, so the two read as one story in the log.
   */
  suspend fun contextCompacted(dropped: Int) {
    val noun = if (dropped == 1) "answer" else "answers"
    append("context compacted: $dropped $noun dropped to fit budget")
  }

  suspend fun contextCompacted(phaseLabel: String, count: Int, oldEstTokens: Int, newEstTokens: Int) {
    val noun = if (count == 1) "answer" else "answers"
    append(
      "context compacted: $phaseLabel summarized, $count $noun replaced " +
        "($oldEstTokens t → $newEstTokens t)"
    )
  }

  /**
   * The one-line shape of a check row: the transcript's estimated cost and the
   * budget it was measured against, in the same "tokens … budget" words the
   * near-limit dialog uses. An engine that composes no prompt has no window to
   * measure against, so it reads as measured-but-unbudgeted instead of claiming
   * a budget of zero.
   */
  private fun contextCheckText(check: ContextCheck): String {
    val estimated = "${PlanningContext.formatTokens(check.estimatedTokens)} tokens"
    val budget = check.budgetTokens
      ?: return "context checked: $estimated estimated, no window to fill"
    val standing = if (check.nearLimit) "over" else "within"
    return "context checked: $estimated estimated, $standing the " +
      "${PlanningContext.formatTokens(budget)} budget"
  }

  /**
   * The action a round's origin reads as, or null for a round the user didn't
   * choose — a project's opening round. The [RoundOrigin.Initial] case splits on
   * round number because both an opening round and a wrap-up into a new phase
   * open an Initial round: only the latter is an advance.
   */
  private fun roundAction(round: Round): String? = when (round.origin) {
    RoundOrigin.UserRequested, RoundOrigin.FollowUp -> LogMarkers.ActionMoreQuestions
    RoundOrigin.Initial -> if (round.roundNumber == 1) null else LogMarkers.ActionAdvancePhase
  }

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
        raw = raw?.ifBlank { null },
      )
    )
  }

  private suspend fun terminal(text: String, raw: String? = null) {
    file(text, raw)
    closed = true
  }
}
