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
 *
 * The names are the *subsystem* a task talks to, not where that subsystem runs.
 * [Engine] is not "the local engine": the selected engine may be a remote
 * inference backend, and its tasks land in [Engine] all the same, so the limit
 * here is a conservative policy rather than a hardware constraint. The limit
 * that reflects the actual resource — on-device inference admits one batch at a
 * time, a remote endpoint several — is not derivable from a [TaskKind], since
 * the engine is resolved per task at enqueue time (see
 * [alphainterplanetary.thinker.engine.PlanningEngineSelector]). Until the call
 * site derives the group from the engine it resolved, [Engine] serializes across
 * every project.
 */
enum class ConcurrencyGroup(val concurrency: Int) {
  /** Planning-engine work of any backend — one at a time, so strictly serial. */
  Engine(concurrency = 1),

  /** Independent remote work (remote LLM, HTTP lookups) — bounded parallelism. */
  Remote(concurrency = 4),
}