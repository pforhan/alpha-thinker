package alphainterplanetary.thinker.tools

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.tatarka.inject.annotations.Inject

class SampleProjectGenerator @Inject constructor(
  private val storage: Storage,
) {

  suspend fun generate() {
    listOf(SPARSE_PROJECT_ID, COMPLETE_PROJECT_ID, STRESS_PROJECT_ID).forEach { id ->
      storage.deleteProject(id)
    }
    storage.saveProject(sparseProject())
    storage.saveProject(completeProject())
    storage.saveProject(stressProject())
  }

  private fun sparseProject(): Project {
    val created = daysAgo(9)
    val contextId = "sample-sparse-ctx"
    return Project(
      id = SPARSE_PROJECT_ID,
      synopsis = "A no-fuss method for making cold brew coffee concentrate at home.",
      editableTitle = "Cold brew",
      status = "Draft",
      questions = listOf(
        sampleQuestion(
          id = "sparse-q1",
          text = "What is the best ratio of coffee to water?",
          timestamp = created,
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "sparse-q1",
              text = "I have been using a 1:5 ratio of coffee to water and it comes out " +
                "concentrated enough to dilute with milk or water.",
              answeredAt = daysAgo(7),
            ),
          ),
        ),
        sampleQuestion(
          id = "sparse-q2",
          text = "How long should it steep?",
          timestamp = created,
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "sparse-q2",
              text = "18 to 24 hours in the fridge seems right; anything shorter tastes weak.",
              answeredAt = daysAgo(6),
            ),
          ),
        ),
        sampleQuestion(
          id = "sparse-q3",
          text = "What grind size works best for cold brew?",
          timestamp = created,
          contextId = contextId,
        ),
        sampleQuestion(
          id = "sparse-q4",
          text = "How long can the concentrate stay fresh in the fridge?",
          timestamp = created,
          contextId = contextId,
        ),
        sampleQuestion(
          id = "sparse-q5",
          text = "Do I need a filter bag or is a regular cheesecloth enough?",
          timestamp = created,
          contextId = contextId,
        ),
        sampleQuestion(
          id = "sparse-q6",
          text = "What is a simple way to serve it without fancy equipment?",
          timestamp = created,
          contextId = contextId,
        ),
        sampleQuestion(
          id = "sparse-q7",
          text = "How much concentrate does one batch yield?",
          timestamp = created,
          contextId = contextId,
        ),
      ),
      createdAt = created,
      updatedAt = created,
    )
  }

  private fun completeProject(): Project {
    val created = daysAgo(6)
    val updated = daysAgo(2, 30)
    val contextId = "sample-complete-ctx"
    return Project(
      id = COMPLETE_PROJECT_ID,
      synopsis = "A small mobile puzzle game I have been designing in my head for months. " +
        "I want to ship a polished MVP on the Play Store within the next two quarters while " +
        "keeping my day job, using weekend time and a small budget. The biggest challenge is " +
        "staying honest about scope: keeping the game tiny while still making it feel complete.",
      editableTitle = "Ship my tiny puzzle game",
      status = "Draft",
      questions = listOf(
        sampleQuestion(
          id = "complete-q1",
          text = "What is the primary problem this project solves?",
          timestamp = created,
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q1",
              text = "Players looking for a quick, thoughtful break get a bite-sized puzzle " +
                "game they can finish in 3-5 minutes. No accounts, no forced progression, no " +
                "pay-to-win energy systems; just a satisfying loop that fits into a commute or " +
                "a coffee break.",
              answeredAt = daysAgo(5),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q2",
          text = "Who is the ideal user or beneficiary?",
          timestamp = created,
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q2",
              text = "Busy commuters in their late 20s to 40s who enjoy casual mobile games but " +
                "hate pay-to-win mechanics. They value calm visuals and do not want a game that " +
                "demands daily login streaks or loot boxes.",
              answeredAt = daysAgo(5),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q3",
          text = "What is the single most important goal?",
          timestamp = daysAgo(5),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q3",
              text = "Launch a playable, polished MVP on the Play Store within the next two " +
                "quarters (by end of Q2) and reach at least 1,000 installs in the first month, " +
                "mostly from organic discovery.",
              answeredAt = daysAgo(4),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q4",
          text = "What are the key features?",
          timestamp = daysAgo(5),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q4",
              text = "The core loop is sliding a single piece across a 5x5 board to match " +
                "targets. Around 60 hand-crafted levels with a gentle difficulty curve, a hint " +
                "system, and settings for sound and haptics. No accounts, no leaderboards, no " +
                "daily rewards.",
              answeredAt = daysAgo(4),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q5",
          text = "What is the minimum viable product (MVP) version?",
          timestamp = daysAgo(5),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q5",
              text = "One mechanic, 60 levels, a level-select screen, local high-score saving, " +
                "a basic settings menu, and an About screen. Cut everything else: no music " +
                "composer, no cloud saves, no achievements, no online leaderboard.",
              answeredAt = daysAgo(4),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q6",
          text = "What are the top three risks to success?",
          timestamp = daysAgo(4),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q6",
              text = "1) Scope creep: I keep wanting to add mechanics, so the game could " +
                "balloon past my skill and budget. 2) Burnout from building everything solo on " +
                "weekends. 3) The 'polished but tiny' bar is high; a rough MVP may not convert " +
                "installs into retention, killing momentum before a second version.",
              answeredAt = daysAgo(3),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q7",
          text = "What is the target completion date?",
          timestamp = daysAgo(4),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q7",
              text = "June 30 for the Play Store release. Level design finished by end of " +
                "March, a closed beta with friends by end of April, and a soft launch through " +
                "May.",
              answeredAt = daysAgo(3),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q8",
          text = "What resources (time, money, tools) are currently available?",
          timestamp = daysAgo(3),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "complete-q8",
              text = "About eight hours a week of free time (Saturdays plus two weekday " +
                "evenings), roughly $300 budget for asset packs, a testing device and the " +
                "one-time developer account fee. Existing skills: basic Kotlin and gameplay " +
                "prototyping. I have a mid-range Android phone and a decent laptop.",
              answeredAt = daysAgo(2),
            ),
          ),
        ),
        sampleQuestion(
          id = "complete-q9",
          text = "What is the very first step you need to take?",
          timestamp = daysAgo(3),
          contextId = contextId,
        ),
        sampleQuestion(
          id = "complete-q10",
          text = "How will you know if the project is successful?",
          timestamp = daysAgo(3),
          contextId = contextId,
          ignoredAt = daysAgo(3),
        ),
        sampleQuestion(
          id = "complete-q11",
          text = "What are the long-term goals?",
          timestamp = daysAgo(2),
          contextId = contextId,
          ignoredAt = daysAgo(2),
        ),
        sampleQuestion(
          id = "complete-q12",
          text = "What is your biggest technical risk?",
          timestamp = daysAgo(2),
          contextId = contextId,
          answers = listOf(
            Answer(
              questionId = "complete-q12",
              text = "Still unsure whether the custom swipe-to-move gesture will feel right on " +
                "a phone. I need to prototype it early with real touch input before building " +
                "the level editor around it.",
              answeredAt = null,
            ),
          ),
        ),
      ),
      createdAt = created,
      updatedAt = updated,
    )
  }

  private fun stressProject(): Project {
    val created = daysAgo(3)
    val updated = daysAgo(0, 120)
    val contextId = "sample-stress-ctx"
    return Project(
      id = STRESS_PROJECT_ID,
      synopsis = lipsum(5),
      editableTitle = lipsum(1),
      status = "Draft",
      questions = listOf(
        sampleQuestion(
          id = "stress-q1",
          text = lipsum(2),
          timestamp = daysAgo(3),
          contextId = contextId,
        ),
        sampleQuestion(
          id = "stress-q2",
          text = lipsum(2),
          timestamp = daysAgo(3),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "stress-q2",
              text = lipsum(3),
              answeredAt = daysAgo(2),
            ),
          ),
        ),
        sampleQuestion(
          id = "stress-q3",
          text = lipsum(1),
          timestamp = daysAgo(2),
          contextId = contextId,
          ignoredAt = daysAgo(2),
        ),
        sampleQuestion(
          id = "stress-q4",
          text = lipsum(2),
          timestamp = daysAgo(2),
          contextId = contextId,
          answers = listOf(
            Answer(
              questionId = "stress-q4",
              text = lipsum(2),
              answeredAt = null,
            ),
          ),
        ),
        sampleQuestion(
          id = "stress-q5",
          text = lipsum(2),
          timestamp = daysAgo(1),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "stress-q5",
              text = lipsum(3),
              answeredAt = daysAgo(1),
            ),
          ),
        ),
        sampleQuestion(
          id = "stress-q6",
          text = lipsum(2),
          timestamp = daysAgo(1),
          contextId = contextId,
          answers = listOf(
            completeAnswer(
              questionId = "stress-q6",
              text = lipsum(2),
              answeredAt = daysAgo(0, 3),
            ),
          ),
        ),
        sampleQuestion(
          id = "stress-q7",
          text = lipsum(2),
          timestamp = daysAgo(2),
          contextId = contextId,
        ),
        sampleQuestion(
          id = "stress-q8",
          text = lipsum(1),
          timestamp = daysAgo(1),
          contextId = contextId,
          ignoredAt = daysAgo(0, 6),
        ),
      ),
      createdAt = created,
      updatedAt = updated,
    )
  }

  private fun sampleQuestion(
    id: String,
    text: String,
    timestamp: Instant,
    contextId: String,
    answers: List<Answer> = emptyList(),
    ignoredAt: Instant? = null,
  ) = Question(
    id = id,
    text = text,
    timestamp = timestamp,
    contextId = contextId,
    ignoredAt = ignoredAt,
    answers = answers,
  )

  private fun completeAnswer(
    questionId: String,
    text: String,
    answeredAt: Instant,
  ) = Answer(
    questionId = questionId,
    text = text,
    answeredAt = answeredAt,
  )

  private val now: Instant = Clock.System.now()

  private fun daysAgo(days: Long, minutes: Long = 0): Instant {
    val millis = now.toEpochMilliseconds()
    return Instant.fromEpochMilliseconds(millis - (days * 24 * 60 + minutes) * 60 * 1000)
  }

  private fun lipsum(paragraphCount: Int): String = buildString {
    var paragraphs = 0
    while (paragraphs < paragraphCount) {
      if (paragraphs > 0) append("\n\n")
      append(LOREM_PARAGRAPHS[paragraphs % LOREM_PARAGRAPHS.size])
      paragraphs++
    }
  }

  private companion object {
    const val SPARSE_PROJECT_ID = "sample-sparse"
    const val COMPLETE_PROJECT_ID = "sample-complete"
    const val STRESS_PROJECT_ID = "sample-stress"

    val LOREM_PARAGRAPHS = listOf(
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
        "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud " +
        "exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
      "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat " +
        "nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui " +
        "officia deserunt mollit anim id est laborum.",
      "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque " +
        "laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi " +
        "architecto beatae vitae dicta sunt explicabo.",
      "Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia " +
        "consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro " +
        "quisquam est, qui dolorem ipsum quia dolor sit amet.",
      "At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium " +
        "voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint " +
        "occaecati cupiditate non provident.",
    )
  }
}
