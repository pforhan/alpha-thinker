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

  /** Short sentence describing the phase — shown when it's offered as a next-phase choice. */
  val description: String

  /** Short sentence describing the phase once it's been worked through — shown for completed phases. */
  val completedDescription: String

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
  override val description: String,
  override val completedDescription: String,
) : Phase {
  ScopeGoals(
    key = "scope-goals",
    label = "Scope & Goals",
    keywords = listOf("problem", "user", "goal", "vision", "why"),
    description = "Pin down the problem, who it's for, and what success looks like.",
    completedDescription = "You've anchored the project — problem, audience, and goals are defined.",
  ),
  Research(
    key = "research",
    label = "Research",
    keywords = listOf("benchmark", "competitor", "reference", "inspiration", "similar"),
    description = "Survey what already exists so the plan builds on proven ground.",
    completedDescription = "You've mapped the landscape — benchmarks, references, and inspiration.",
  ),
  Design(
    key = "design",
    label = "Design",
    keywords = listOf("feature", "design", "prototype", "workflow", "value proposition"),
    description = "Shape the core experience — features, workflow, and what makes it worth using.",
    completedDescription = "You've designed the core experience — features and workflows are set.",
  ),
  ExecutionPlan(
    key = "execution-plan",
    label = "Execution Plan",
    keywords = listOf(
      "build",
      "implement",
      "backlog",
      "milestone",
      "technical",
      "resource",
      "timeline"
    ),
    description = "Lay out the build — milestones, timeline, and the resources it will take.",
    completedDescription = "You've planned the execution — milestones, timeline, and resources.",
  ),
  ValidationPlan(
    key = "validation-plan",
    label = "Validation Plan",
    keywords = listOf("trial", "feedback", "test", "measure", "risk"),
    description = "Decide how you'll test the plan and measure the risks along the way.",
    completedDescription = "You've planned the validation — what to test, measure, and watch for.",
  ),
  DefinitionOfDone(
    key = "definition-of-done",
    label = "Definition of Done",
    keywords = listOf("finish", "launch", "ship", "publish", "review", "deliverable", "done"),
    description = "Pin down what done means — the deliverable, sign-off, and shipped look.",
    completedDescription = "The plan is finished — done is defined and ready to hand off.",
  );

  override val order: Int get() = ordinal + 1
}
