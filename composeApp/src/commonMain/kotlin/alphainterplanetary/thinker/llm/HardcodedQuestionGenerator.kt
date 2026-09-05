package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.util.randomUUID
import kotlinx.datetime.Clock.System
import me.tatarka.inject.annotations.Inject
import org.jetbrains.annotations.TestOnly

class HardcodedQuestionGenerator @Inject constructor(
  private val initialCount: Int = 7,
  private val followUpCount: Int = 5,
) : QuestionGenerator {

  override suspend fun recommendTitle(synopsis: String): String =
    generateTitleFromSynopsis(synopsis)

  @TestOnly
  fun generateTitleFromSynopsisForTest(synopsis: String): String =
    generateTitleFromSynopsis(synopsis)

  private fun generateTitleFromSynopsis(synopsis: String): String = synopsis.trim()
    .substringBefore('\n')
    .substringBefore('.')
    .take(30)
    .trim()

  override suspend fun generateInitialQuestions(
    @Suppress("UNUSED_PARAMETER") editableTitle: String,
    @Suppress("UNUSED_PARAMETER") synopsis: String,
    contextId: String,
  ): List<Question> {
    val now = System.now()
    return questionPool
      .take(initialCount)
      .map { text ->
        Question(
          id = randomUUID(),
          text = text,
          timestamp = now,
          contextId = contextId
        )
      }
  }

  override suspend fun generateFollowUpQuestions(
    @Suppress("UNUSED_PARAMETER") synopsis: String,
    previousQuestions: List<Question>,
    contextId: String,
  ): List<Question> {
    val askedTexts = previousQuestions.map { it.text }.toSet()
    val remaining = questionPool.filter { it !in askedTexts }
    if (remaining.isEmpty()) return emptyList()

    val now = System.now()
    return remaining
      .take(followUpCount)
      .map { text ->
        Question(
          id = randomUUID(),
          text = text,
          timestamp = now,
          contextId = contextId
        )
      }
  }

  companion object {
    val questionPool = listOf(
      "What is the primary problem this project solves?",
      "Who is the ideal user or beneficiary?",
      "What is the single most important goal?",
      "What are three key milestones for the first month?",
      "What resources (time, money, tools) are currently available?",
      "What resources are still needed?",
      "What is the target completion date?",
      "What are the top three risks to success?",
      "How will you know if the project is successful?",
      "What is the \"Minimum Viable Product\" (MVP) version?",
      "What are the key technical constraints or requirements?",
      "Who are the primary stakeholders and decision-makers?",
      "What similar projects or competitors have you looked at?",
      "What is the long-term vision for this project?",
      "What are the non-negotiable features or qualities?",
      "How will this project be maintained or supported later?",
      "What is the estimated total budget?",
      "Are there any legal, ethical, or compliance factors?",
      "What will you promote or distribute the final result?",
      "What is the very first step you need to take?",
      "What problem does this project solve?",
      "Who is the primary user?",
      "What are the key features?",
      "What's the core value proposition?",
      "What is the biggest constraint?",
      "What does success look like?",
      "What technologies would you like to use?",
      "Are there any existing solutions you're inspired by?",
      "What makes your approach different?",
      "What's the minimum viable product?",
      "What are the long-term goals?",
      "Who else cares about this project?",
      "What assumptions are you making?",
      "What could go wrong?",
      "What's the simplest version that still works?",
      "What's the scope you're comfortable with?",
      "What needs to be done first?",
      "What can wait until later?",
      "What resources do you need?",
      "What skills or knowledge gaps exist?",
      "What's the estimated timeline?",
      "How will you measure progress?",
      "What feedback will you gather?",
      "What milestones define completion?",
      "How would your first user describe what this does?",
      "What's the one thing that must just work?",
      "What's out of scope right now?",
      "What's your biggest technical risk?",
      "How would you explain this to a teammate?",
      "What could you build in a week?",
      "What's the core workflow?",
      "Who else benefits from this besides the main user?",
      "What data flows through the system?",
      "What's the fallback if everything breaks?",
      "What would the user do after using this?",
      "What makes this stick in someone's mind?",
      "What's one feature you're excited about?",
      "What's the most boring but necessary part?",
      "Where will you cut corners to ship faster?",
      "What's the learning goal for your users?",
      "What's the quickest path to value?",
      "How will you know you're done?",
      "What's your go-to-market story?",
      "What's the most surprising thing about your users?",
      "What's the story you'll tell at the end?",
      "What's the hardest part to build?",
      "What's the simplest version of your answer?",
      "Who's the first person you'll show this to?",
    )
  }
}
