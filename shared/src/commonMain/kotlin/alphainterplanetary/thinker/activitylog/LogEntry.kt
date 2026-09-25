package alphainterplanetary.thinker.activitylog

import kotlin.time.Instant

/**
 * The topic a [LogEntry] is about — abstract enough to group and filter across
 * the whole app, detailed enough to tell operations apart. This is composite
 * dimension one of two (with [LogSource]): a remote-LLM question batch reads
 * `category = QuestionGeneration`, `source = RemoteLLM`; a task framework row
 * reads `category = TaskRun`, `source = TaskRunner`.
 */
enum class LogCategory(val label: String) {
  /** Initial or follow-up question batches. */
  QuestionGeneration("Question generation"),

  /** Recommending an editable title from a synopsis. */
  TitleRecommendation("Title recommendation"),

  /** Asking whether a phase can still produce questions. */
  CapabilityCheck("Capability check"),

  /** A tool / web-search / fetch call. */
  Lookup("Lookup"),

  /** Long-running task lifecycle rows (start/finish). */
  TaskRun("Task"),

  /** Generic app-wide noise that needs no specific topic. */
  Info("Info"),
}

/**
 * What produced a [LogEntry] — composite dimension two (with [LogCategory]).
 * `null` on a row means a source doesn't apply (e.g. a standalone note).
 */
enum class LogSource(val label: String) {
  /** The built-in hardcoded Lite engine. */
  Lite("Lite"),

  /** An on-device / system LLM. */
  LocalLLM("On-device LLM"),

  /** A remote / cloud LLM endpoint. */
  RemoteLLM("Remote LLM"),

  /** A tool call (e.g. a lookup). */
  Tool("Tool"),

  /** The app's task-runner framework. */
  TaskRunner("Task runner"),

  /** The app itself (settings, errors, misc). */
  App("App"),
}

/**
 * The stable text markers that synchronize log writers and the read model. A
 * row's [LogEntry.log] carries one of these prefixes so [LogActivity] can tell
 * a lifecycle opening from an outcome and an outcome from an interaction row
 * without parsing free prose. Writers file rows through [LogingContext] (which
 * renders these markers) or — for standalone rows that don't belong to a
 * lifecycle — build them around [LogEntry.companion]-style factories; the
 * reader never invents its own expectations. Keep the markers and their reader
 * in lockstep: every addition here belongs on both sides.
 */
object LogMarkers {
  /** A lifecycle opening, e.g. `started: InitialQuestions`. */
  const val Started = "started:"

  /** A success terminal, e.g. `succeeded` or `succeeded: result=true`. */
  const val Succeeded = "succeeded"

  /** A failure terminal carrying a message, e.g. `failed: model exploded`. */
  const val Failed = "failed:"

  /** A cancellation terminal (`cancelled`). */
  const val Cancelled = "cancelled"

  /** A compact non-rendered summary of what was sent, e.g. `input: synopsis=…`. */
  const val Input = "input:"

  /** A rendered prompt, verbatim. */
  const val Prompt = "prompt:"

  /** The engine's produced outcome, e.g. `response: canProduceMore=false`. */
  const val Response = "response:"

  /** A failure row raised by an engine/interaction, e.g. `error: connection`. */
  const val Error = "error:"
}

/**
 * One immutable, append-only row in the app-wide activity log (ENG-DESIGN.md
 * schema item 4). The log lives in its own `ActivityDatabase` so it grows and
 * prunes independently of projects/questions/settings.
 *
 * Global order is the autoincrement [id] primary key once persisted; before
 * that (in-memory) it is null. [activityId] is an optional UUID that ties
 * related rows together — a generation task's start/finish rows, or a prompt
 * and its response share an id; rows without one stand alone. [log] is free
 * text carrying whatever the row records (a prompt, a response, an error, a
 * result, a note) under one of the [LogMarkers] prefixes that the read model
 * ([LogActivity]) parses to derive summaries and outcomes.
 */
data class LogEntry(
  /** Autoincrement primary key; null until persisted. */
  val id: Long? = null,
  val projectId: String? = null,
  /** UUID tying related rows together; null means a standalone line. */
  val activityId: String? = null,
  val category: LogCategory,
  val source: LogSource? = null,
  /** The row's content: a prompt, response, error, result, or note. */
  val log: String,
  val timestamp: Instant,
)