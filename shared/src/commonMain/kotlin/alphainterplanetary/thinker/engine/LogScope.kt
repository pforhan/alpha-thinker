package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The seam that lets an LLM-backed [PlanningEngine] record its requests on the
 * app's activity log without knowing the log exists.
 *
 * Two things have to cross that seam, and both used to arrive on a side channel
 * that could not carry them. The *prompt* has to be the text that actually went
 * out — reconstructed on a parallel path it could silently drift from the
 * request. The *reply* has to be recorded exactly as it came back, including
 * the ones we could not read: a `failed:` row carrying only "didn't reply with
 * a JSON array" is undiagnosable, and the offending text is the only evidence.
 * And an interaction is not one request: a call that fans out (a summarizing
 * sub-request, a multi-model request) has to file a row pair per request, with
 * each reply on the pair it belongs to.
 *
 * A coroutine context element is the one channel that spans it. The decorator
 * installs one [LogScope] per interaction (the [LoggingPlanningEngine] root),
 * and the engine's one real call site opens a [LogRequest] from it for each
 * request it sends ([logRequest]). A request has its own writer and its own
 * reply — nothing is shared between them — so parallel `async` fan-out pairs
 * each reply with the request that produced it by construction, and an engine
 * invoked with no scope installed (a direct call, a test) is simply not
 * observed.
 *
 * Every request files into the same activity, in append order, which is what the
 * read model ([alphainterplanetary.thinker.activitylog.ActivityRecord]) folds
 * into an activity. A sub-request's reply therefore reads on its own
 * `response:`/`failed:` row rather than on the parent's — the failure case
 * matters most, where one merged payload put a successful sub-reply on the
 * parent's `failed:` row under a message about a different thing.
 */
class LogScope internal constructor(
  private val newContext: () -> LogContext,
) : AbstractCoroutineContextElement(Key) {
  // A request may be opened from parallel branches of one interaction, so the
  // flag is mutex-guarded rather than a plain flag; it is read once, after the
  // interaction has returned.
  private val reportedLock = Mutex()
  private var reported = false

  /**
   * The writer for one request's own row pair: its `prompt:` row is filed here,
   * as the request is opened, and this interaction is marked as having reported.
   */
  suspend fun request(prompt: String): LogRequest {
    val context = newContext()
    reportedLock.withLock { reported = true }
    context.prompt(prompt)
    return LogRequest(context)
  }

  /**
   * A fresh writer for the interaction's *own* outcome row, or null once a
   * request has reported one of its own. This is what keeps an engine that
   * never speaks to a model (the hardcoded Lite engine) on the log without
   * letting an LLM-backed engine file the same result twice.
   */
  suspend fun fallback(): LogContext? =
    if (reportedLock.withLock { reported }) null else newContext()

  companion object Key : CoroutineContext.Key<LogScope>
}

/**
 * One request's outcome row: `response:` for a reply that was read, `failed:`
 * for one that wasn't, `cancelled` for a request that never completed — with
 * the verbatim reply riding along on that row as its raw payload either way. A
 * [LogScope] opens one per request, so an interaction files exactly one prompt
 * row and one outcome row for every request it makes.
 */
class LogRequest internal constructor(
  private val context: LogContext,
) {
  /**
   * The outcome row for a request that produced [summary], with [raw] — the
   * reply as it came back — as the row's payload. A blank [raw] is dropped
   * rather than filed: there is nothing to show, and an empty payload reads as
   * a truncated one.
   */
  suspend fun responded(summary: String, raw: String? = null) =
    context.response(summary, raw?.ifBlank { null })

  /**
   * The outcome row for a request that failed, keeping [raw] — the reply that
   * caused the failure, or nothing at all for a send that never got one.
   */
  suspend fun failed(message: String, raw: String? = null) =
    context.closeFailed(message, raw?.ifBlank { null })

  /** The outcome row for a request that was cancelled before it produced anything. */
  suspend fun cancelled() = context.closeCancelled()
}

/**
 * Files the `prompt:` row for one model request under the interaction's ambient
 * [LogScope] and hands back the writer for that request's outcome row. Null
 * when no scope is installed — an engine called directly is not observed, and
 * the caller carries on as if the row didn't exist.
 */
suspend fun logRequest(prompt: String): LogRequest? = coroutineContext[LogScope]?.request(prompt)
