package alphainterplanetary.thinker.phases

/**
 * One planning phase in the shared library — a labeled planning focus with a
 * keyword profile used for next-phase recommendation (text-scoring round
 * answers + synopsis against [keywords]).
 *
 * The library is a settled, closed set while phases are only the six below; a
 * persisted `phases` table replaces this enum once user-created/LLM-proposed
 * labels arrive (the [key] is the durable reference `Round.phase` stores).
 */
enum class Phase(
  val key: String,
  val label: String,
  val order: Int,
  val keywords: List<String>,
) {
  ScopeGoals(
    key = "scope-goals",
    label = "Scope & Goals",
    order = 1,
    keywords = listOf("problem", "user", "goal", "vision", "why"),
  ),
  Research(
    key = "research",
    label = "Research",
    order = 2,
    keywords = listOf("benchmark", "competitor", "reference", "inspiration", "similar"),
  ),
  Design(
    key = "design",
    label = "Design",
    order = 3,
    keywords = listOf("feature", "design", "prototype", "workflow", "value proposition"),
  ),
  ExecutionPlan(
    key = "execution-plan",
    label = "Execution Plan",
    order = 4,
    keywords = listOf("build", "implement", "backlog", "milestone", "technical", "resource", "timeline"),
  ),
  ValidationPlan(
    key = "validation-plan",
    label = "Validation Plan",
    order = 5,
    keywords = listOf("trial", "feedback", "test", "measure", "risk"),
  ),
  DefinitionOfDone(
    key = "definition-of-done",
    label = "Definition of Done",
    order = 6,
    keywords = listOf("finish", "launch", "ship", "publish", "review", "deliverable", "done"),
  );

  companion object {
    /** The phase a fresh project starts in: the library's first phase. */
    val first: Phase
      get() = entries.sortedBy { it.order }.first()

    /** Resolves a stored string key (e.g. `"scope-goals"`) back to a phase. */
    fun fromKey(key: String): Phase? = entries.find { it.key == key }

    /** 1-based display index into the library's ordering ("Phase 3 of N"); 0 if unknown. */
    fun indexOf(key: String): Int = fromKey(key)?.order ?: 0
  }
}