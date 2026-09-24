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
 * One immutable, append-only row in the app-wide activity log (ENG-DESIGN.md
 * schema item 4). The log lives in its own `ActivityDatabase` so it grows and
 * prunes independently of projects/questions/settings.
 *
 * Global order is the autoincrement [id] primary key once persisted; before
 * that (in-memory) it is null. [activityId] is an optional UUID that ties
 * related rows together — a generation task's start/finish rows, or a prompt
 * and its response re shared id; rows without one stand alone. [log] is free
 * text carrying whatever the row records (a prompt, a response, an error, a
 * result, a note), and the writers that produce these rows keep stable prefix
 * conventions (`started:`/`succeeded:`/`failed:`/`cancelled:`/`input:`/
 * `prompt:`/`response:`/`error:`) that the read model ([LogActivity]) uses to
 * synthesize summaries.
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