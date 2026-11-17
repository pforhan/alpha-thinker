package alphainterplanetary.thinker.engine.core

/**
 * The consumer intent for a phase summary.
 *
 * Different intents may produce different summaries (prompts, length/structure,
 * emphasis) even for the same phase content (see "Evaluate and expand summarize
 * support" in 4C), so cache entries and resolution are keyed by intent rather
 * than assuming a single summary per phase.
 *
 * Only one engine-backed summarize method exists today; intents still separate
 * cache/provenance so future per-intent methods/prompts do not conflate results.
 */
enum class PhaseSummaryIntent(val storageKey: String) {
  /** Condense settled decisions/constraints for prompt/context compaction. */
  Compaction("compaction"),

  /** Summarize a phase for human-readable document export. */
  Export("export");

  companion object {
    fun fromStorageKey(key: String): PhaseSummaryIntent? =
      entries.firstOrNull { it.storageKey == key }
  }
}
