package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.util.randomUUID
import kotlinx.datetime.Clock.System

class MockQuestionGenerator : QuestionGenerator {
  private var roundCounter = 0

  private val initialQuestionTemplates = listOf(
    listOf(
      "What problem does this project solve?",
      "Who is the primary user?",
      "What are the key features?"
    ),
    listOf(
      "What's the core value proposition?",
      "What is the biggest constraint?",
      "What does success look like?"
    ),
    listOf(
      "What technologies would you like to use?",
      "Are there any existing solutions you're inspired by?",
      "What makes your approach different?"
    ),
    listOf(
      "What's the minimum viable product?",
      "What are the long-term goals?",
      "Who else cares about this project?"
    ),
    listOf(
      "What assumptions are you making?",
      "What could go wrong?",
      "What's the simplest version that still works?"
    ),
    listOf(
      "What's the scope you're comfortable with?",
      "What needs to be done first?",
      "What can wait until later?"
    ),
    listOf(
      "What resources do you need?",
      "What skills or knowledge gaps exist?",
      "What's the estimated timeline?"
    ),
    listOf(
      "How will you measure progress?",
      "What feedback will you gather?",
      "What milestones define completion?"
    )
  )

  private val followUpTemplates = listOf(
    listOf(
      "How would your first user describe what this does?",
      "What's the one thing that must just work?",
      "What's out of scope right now?"
    ),
    listOf(
      "What's your biggest technical risk?",
      "How would you explain this to a teammate?",
      "What could you build in a week?"
    ),
    listOf(
      "What's the core workflow?",
      "Who else benefits from this besides the main user?",
      "What data flows through the system?"
    ),
    listOf(
      "What's the fallback if everything breaks?",
      "What would the user do after using this?",
      "What makes this stick in someone's mind?"
    ),
    listOf(
      "What's one feature you're excited about?",
      "What's the most boring but necessary part?",
      "Where will you cut corners to ship faster?"
    ),
    listOf(
      "What's the learning goal for your users?",
      "What's the quickest path to value?",
      "How will you know you're done?"
    ),
    listOf(
      "What's your go-to-market story?",
      "What's the most surprising thing about your users?",
      "What's the story you'll tell at the end?"
    ),
    listOf(
      "What's the hardest part to build?",
      "What's the simplest version of your answer?",
      "Who's the first person you'll show this to?"
    )
  )

  override suspend fun generateInitialQuestions(@Suppress("UNUSED_PARAMETER") synopsis: String): List<Question> {
    return getInitialQuestions()
  }

  override suspend fun generateFollowUpQuestions(
    @Suppress("UNUSED_PARAMETER") synopsis: String,
  ): List<Question> {
    return getFollowUpQuestions(roundCounter++)
  }

  @Suppress("UNUSED_PARAM")
  private fun getInitialQuestions(): List<Question> {
    val templateIndex = roundCounter % initialQuestionTemplates.size
    val template = initialQuestionTemplates[templateIndex]
    roundCounter++

    return template.map { text ->
      Question(
        id = randomUUID(),
        text = text,
        timestamp = System.now(),
        contextId = ""
      )
    }
  }

  @Suppress("UNUSED_PARAM")
  private fun getFollowUpQuestions(previousRound: Int): List<Question> {
    // Use previous round to offset template selection
    val templateIndex = (previousRound) % followUpTemplates.size
    val template = followUpTemplates[templateIndex]

    return template.map { text ->
      Question(
        id = randomUUID(),
        text = text,
        timestamp = System.now(),
        contextId = ""
      )
    }
  }
}
