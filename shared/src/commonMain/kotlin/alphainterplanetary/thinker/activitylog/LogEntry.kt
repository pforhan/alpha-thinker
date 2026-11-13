package alphainterplanetary.thinker.activitylog

import kotlin.time.Instant

/**
 * The topic a [LogEntry] is about — abstract enough to group and filter across
 * the whole app, detailed enough to tell operations apart. This is composite
 * dimension one of two (with [LogSource]): a remote-LLM question batch reads
 * `topic = QuestionGeneration`, `source = RemoteLLM`; a task framework row reads
 * `topic = TaskRun`, `source = TaskRunner`.
 */
enum class LogTopic(val label: String) {
  /** Initial or follow-up question batches. */
  QuestionGeneration("Question generation"),

  /** Recommending an editable title from a synopsis. */
  TitleRecommendation("Title recommendation"),

  /** A tool / web-search / fetch call. */
  Lookup("Lookup"),

  /** A past phase's answers condensed into a summary the question prompt uses. */
  PriorSummary("Prior-phase summary"),

  /** Long-running task lifecycle rows (start/finish). */
  TaskRun("Task"),

  /** Moving a model onto a remote inference server (e.g. the Ollama pull). */
  ModelDownload("Model download"),

  /** Generic app-wide noise that needs no specific topic. */
  Info("Info"),
}

/**
 * What produced a [LogEntry] — composite dimension two (with [LogTopic]).
 * `null` on a row means a source doesn't apply (e.g. a standalone note).
 */
enum class LogSource(val label: String) {
  /** The built-in hardcoded Offline engine. */
  Offline("Offline"),

  /** A remote / cloud LLM endpoint. */
  RemoteLLM("Remote LLM"),

  /** A tool call (e.g. a lookup). */
  Tool("Tool"),

  /** The app's task-runner framework. */
  TaskRunner("Task runner"),

  /** A native Ollama instance (model catalogue lookups and pulls). */
  Ollama("Ollama"),

  /** The app itself (settings, errors, misc). */
  App("App"),
}

/**
 * The step a [LogEntry] row represents. Writers set it so the read model
 * ([ActivityRecord]) and the UI can branch on structure instead of parsing
 * marker prefixes out of [LogEntry.log]; [LogEntry.log] remains the row's
 * human-readable display text.
 */
enum class LogStep {
  /** A lifecycle opening (e.g. a task starting). */
  Started,

  /** A success terminal. */
  Succeeded,

  /** A failure terminal carrying a message in [LogEntry.log]. */
  Failed,

  /** A cancellation terminal. */
  Cancelled,

  /** A rendered prompt, verbatim in [LogEntry.log]. */
  Prompt,

  /** An engine-produced outcome in [LogEntry.log]. */
  Response,

  /** A plain note or any row that is not a lifecycle/interaction marker. */
  Note,
}

/**
 * One immutable, append-only row in the app-wide activity log (ENG-DESIGN.md
 * schema item 4). The log lives in its own `ActivityDatabase` so it grows and
 * prunes independently of projects/questions/settings.
 *
 * Global order is the autoincrement [id] primary key once persisted; before
 * that (in-memory) it is null. [activityId] is an optional UUID that ties
 * related rows together — a generation task's start/finish rows, or a prompt
 * and its response share an id; rows without one stand alone. [log] is the
 * row's human-readable display text; [step] records its typed step so readers
 * do not have to parse marker prefixes out of [log].
 */
data class LogEntry(
  /** Autoincrement primary key; null until persisted. */
  val id: Long? = null,
  val projectId: String? = null,
  /** UUID tying related rows together; null means a standalone line. */
  val activityId: String? = null,
  val topic: LogTopic,
  val source: LogSource? = null,
  /** The typed step this row represents. */
  val step: LogStep = LogStep.Note,
  /** The row's human-readable content: a prompt, response, error, result, or note. */
  val log: String,
  val timestamp: Instant,
  /**
   * The verbatim payload behind [log] — today the model's unedited reply, kept
   * even when it couldn't be parsed. It is deliberately *not* folded into
   * [log]: the row's text stays the readable summary the headlines are derived
   * from, and the raw is what the viewer's full-entry popup adds on top (never
   * a second copy of the row). Null when there is no underlying payload — a
   * lifecycle marker, a synthesized note, or an engine that never spoke to a
   * model (Offline).
   */
  val raw: String? = null,
)
