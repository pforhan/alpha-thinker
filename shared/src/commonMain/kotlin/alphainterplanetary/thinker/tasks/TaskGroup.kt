package alphainterplanetary.thinker.tasks

/**
 * The shared resource a task competes for. The [TaskRunner] schedules by group:
 * a group's [concurrency] caps how many of its tasks may run at once, so a
 * single-resource group stays serial while a pool of independent remote
 * endpoints runs in parallel.
 *
 * Grouping is a scheduling *policy*, not a fixed property of a run: most kinds
 * land in [Engine] by default (see [TaskKind.group]), and a caller can pass an
 * explicit group to [TaskRunner.enqueue] when a body competes for a different
 * resource (e.g. a remote HTTP lookup).
 */
enum class TaskGroup(val concurrency: Int) {
  /** The local planning engine — one shared resource, so strictly serial. */
  Engine(concurrency = 1),

  /** Independent remote work (remote LLM, HTTP lookups) — bounded parallelism. */
  Remote(concurrency = 4),
}