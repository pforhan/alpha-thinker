package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase.DefinitionOfDone
import alphainterplanetary.thinker.phases.BuiltInPhase.Design
import alphainterplanetary.thinker.phases.BuiltInPhase.ExecutionPlan
import alphainterplanetary.thinker.phases.BuiltInPhase.Research
import alphainterplanetary.thinker.phases.BuiltInPhase.ScopeGoals
import alphainterplanetary.thinker.phases.BuiltInPhase.ValidationPlan
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import me.tatarka.inject.annotations.Inject
import kotlin.time.Instant

class HardcodedPlanningEngine @Inject constructor(
  private val initialCount: Int = 5,
  private val followUpCount: Int = 5,
) : PlanningEngine {

  override suspend fun recommendTitle(synopsis: String): String =
    generateTitleFromSynopsis(synopsis)

  fun generateTitleFromSynopsisForTest(synopsis: String): String =
    generateTitleFromSynopsis(synopsis)

  private fun generateTitleFromSynopsis(synopsis: String): String = synopsis.trim()
    .substringBefore('\n')
    .substringBefore('.')
    .take(30)
    .trim()

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    val now = now()
    return phase.pool
      .take(initialCount)
      .map { text -> newQuestion(text, roundId, now) }
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    val remaining = remainingPool(phase, previousQuestions)
    if (remaining.isEmpty()) return emptyList()

    val now = now()
    return remaining
      .take(followUpCount)
      .map { text -> newQuestion(text, roundId, now) }
  }

  override suspend fun remainingInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
  ): Int = remainingPool(phase, previousQuestions).size

  /** The phase's pool texts not yet asked in the project, in pool priority order. */
  private fun remainingPool(phase: Phase, previousQuestions: List<Question>): List<String> {
    val askedTexts = previousQuestions.map { it.text }.toSet()
    return phase.pool.filter { it !in askedTexts }
  }

  /** The phase's own pool; empty for phases the library doesn't ship questions for. */
  private val Phase.pool: List<String>
    get() = questionPoolByPhase[this].orEmpty()

  private fun newQuestion(text: String, roundId: String, timestamp: Instant): Question =
    Question(
      id = randomUUID(),
      text = text,
      timestamp = timestamp,
      roundId = roundId,
    )

  companion object {
    /**
     * Each phase's question pool, ordered within the phase so the front of the
     * pool (served first by initial rounds, then cycled through by follow-up
     * rounds) holds that phase's highest-value planning questions. The recipe
     * mirrors PROJECT-FLOWS.md's settled pool partition.
     */
    val questionPoolByPhase: Map<Phase, List<String>> = linkedMapOf(
      ScopeGoals to listOf(
        "What is the primary problem this project solves?",
        "Who is the ideal user or beneficiary?",
        "What is the single most important goal?",
        "What is the \"Minimum Viable Product\" (MVP) version?",
        "What's the core value proposition?",
        "What is the biggest constraint?",
        "What does success look like?",
        "What is the long-term vision for this project?",
        "Who are the primary stakeholders and decision-makers?",
        "What assumptions are you making?",
        "What's the scope you're comfortable with?",
        "What's out of scope right now?",
      ),
      Research to listOf(
        "What similar projects or competitors have you looked at?",
        "What makes your approach different?",
        "Are there any existing solutions you're inspired by?",
        "What's the most surprising thing about your users?",
        "What have others already learned in this space that you can borrow?",
        "What's the proven playbook or pattern that fits this kind of project?",
        "What do existing solutions do badly that you could improve on?",
        "Where would an expert tell you not to reinvent the wheel?",
        "What's the fastest way to sanity-check this idea before building anything?",
        "Who is already solving this for a slightly different audience?",
      ),
      Design to listOf(
        "What are the key features?",
        "What are the non-negotiable features or qualities?",
        "What's the one thing that must just work?",
        "What's the core workflow?",
        "What data flows through the system?",
        "What would the user do after using this?",
        "What makes this stick in someone's mind?",
        "What's one feature you're excited about?",
        "What does the first version look like — the shape, not the polish?",
        "What's the hook that makes a first-time user sit up?",
        "What's the part you'll iterate on most?",
        "What's the smallest demo that shows the core idea moving?",
      ),
      ExecutionPlan to listOf(
        "What is the very first step you need to take?",
        "What are three key milestones for the first month?",
        "What is the target completion date?",
        "What's the estimated timeline?",
        "What's the quickest path to value?",
        "What could you build in a week?",
        "What resources (time, money, tools) are currently available?",
        "What resources are still needed?",
        "What is the estimated total budget?",
        "What are the key technical constraints or requirements?",
        "What technologies would you like to use?",
        "What's the fallback if everything breaks?",
        "Where will you cut corners to ship faster?",
        "What can wait until later?",
      ),
      ValidationPlan to listOf(
        "What are the top three risks to success?",
        "What could go wrong?",
        "Are there any legal, ethical, or compliance factors?",
        "How will you measure progress?",
        "What feedback will you gather?",
        "Who's the first person you'll show this to?",
        "What's your biggest technical risk?",
        "What would convince a skeptic this works?",
        "What's the smallest test that proves the core idea?",
        "What would you measure to know it's good, not just done?",
      ),
      DefinitionOfDone to listOf(
        "How will you know if the project is successful?",
        "How will you know you're done?",
        "What milestones define completion?",
        "What will you promote or distribute the final result?",
        "What's your go-to-market story?",
        "What's the story you'll tell at the end?",
        "What has to be true before you call it shipped?",
        "What's the final deliverable a teammate could pick up and use?",
        "What does \"done\" explicitly not include?",
        "Who signs off on done?",
      ),
    )
  }
}