package alphainterplanetary.thinker.engine

import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The seam that lets an LLM-backed [PlanningEngine] hand its verbatim model
 * reply to the activity log without knowing the log exists.
 *
 * The reply has to be recorded exactly as it came back — the whole point is the
 * ones we *couldn't* read: a `failed:` row carrying only "didn't reply with a
 * JSON array" is undiagnosable, and the offending text is the only evidence.
 * But the engine also has to stay a pure [PlanningEngine] (no logger, no
 * activity id plumbing beyond what the contract already passes), and the
 * decorator that owns the log has no way to see into a call it delegates.
 *
 * A coroutine-context element is the one channel that spans that seam: the
 * decorator installs a [RawResponseCapture] around the call it makes
 * ([capturingRawResponses]), and the engine publishes into it wherever the
 * reply actually lands ([publishRawResponse]) — which is every interaction,
 * parsed or not, because the publish happens before anything tries to read the
 * text. An engine invoked with no capture installed (a direct call, a test) is
 * simply not observed.
 */
class RawResponseCapture(
  private val onReply: (String) -> Unit,
) : AbstractCoroutineContextElement(Key) {
  /** Hands one verbatim reply to the caller that installed this capture. */
  fun record(reply: String) = onReply(reply)

  companion object Key : CoroutineContext.Key<RawResponseCapture>
}

/**
 * Runs [block], reporting every verbatim model reply it publishes to [report].
 * Multiple replies join into one payload (blank-separated, in order) rather
 * than overwriting each other, so an interaction that sends more than one
 * request keeps all of them.
 */
suspend fun <T> capturingRawResponses(
  report: (String) -> Unit,
  block: suspend () -> T,
): T = withContext(RawResponseCapture(report)) { block() }

/**
 * Reports [text] as this interaction's verbatim model reply, for whatever
 * [RawResponseCapture] is installed. A blank reply is dropped: there is nothing
 * to show, and a row carrying an empty raw reads as a truncated one.
 */
suspend fun publishRawResponse(text: String) {
  if (text.isBlank()) return
  coroutineContext[RawResponseCapture.Key]?.record(text)
}

/** The captured replies as one payload, or null when nothing was captured. */
fun List<String>.joinedRawResponse(): String? =
  takeIf { it.isNotEmpty() }?.joinToString("\n\n")
