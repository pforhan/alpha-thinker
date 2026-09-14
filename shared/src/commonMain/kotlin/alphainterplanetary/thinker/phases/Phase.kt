package alphainterplanetary.thinker.phases

/**
 * One planning phase — a labeled planning focus with a keyword profile used
 * for next-phase recommendation (text-scoring round answers + synopsis
 * against [keywords]).
 *
 * Currently implemented by the built-in [BuiltInPhase] enum; the sealed
 * hierarchy leaves room for user-created or LLM-proposed phases later.
 */
sealed interface Phase {
  /** Durable string identifier persisted as `Round.phase`; stable across label or ordering changes. */
  val key: String

  /** Human-readable display name shown to the user (e.g. in UI). */
  val label: String

  /** 1-based display index into the phase ordering ("Phase 3 of N"); built-ins derive it from enum order. */
  val order: Int

  /** Keyword profile used to recommend the next phase by text-scoring round answers + synopsis. */
  val keywords: List<String>

  companion object {
    /** The phase a fresh project starts in: the library's first phase. */
    val first: Phase
      get() = BuiltInPhase.entries.first()

    /** Resolves a stored string key (e.g. `"scope-goals"`) back to a phase. */
    fun fromKey(key: String): Phase? = BuiltInPhase.entries.find { it.key == key }

    /** 1-based display index into the library's ordering ("Phase 3 of N"); 0 if unknown. */
    fun indexOf(key: String): Int = fromKey(key)?.order ?: 0
  }
}

/**
 * The six fixed planning phases shipped with the library.
 */
enum class BuiltInPhase(
  override val key: String,
  override val label: String,
  override val keywords: List<String>,
) : Phase {
  ScopeGoals(
    key = "scope-goals",
    label = "Scope & Goals",
    keywords = listOf("problem", "user", "goal", "vision", "why"),
  ),
  Research(
    key = "research",
    label = "Research",
    keywords = listOf("benchmark", "competitor", "reference", "inspiration", "similar"),
  ),
  Design(
    key = "design",
    label = "Design",
    keywords = listOf("feature", "design", "prototype", "workflow", "value proposition"),
  ),
  ExecutionPlan(
    key = "execution-plan",
    label = "Execution Plan",
    keywords = listOf("build", "implement", "backlog", "milestone", "technical", "resource", "timeline"),
  ),
  ValidationPlan(
    key = "validation-plan",
    label = "Validation Plan",
    keywords = listOf("trial", "feedback", "test", "measure", "risk"),
  ),
  DefinitionOfDone(
    key = "definition-of-done",
    label = "Definition of Done",
    keywords = listOf("finish", "launch", "ship", "publish", "review", "deliverable", "done"),
  );

  override val order: Int get() = ordinal + 1
}
