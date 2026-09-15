package alphainterplanetary.thinker.tools

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import me.tatarka.inject.annotations.Inject
import kotlin.time.Instant

class SampleProjectGenerator @Inject constructor(
  private val storage: Storage,
) {

  private val allIds = listOf(
    SCOPE_PROJECT_ID,
    RESEARCH_PROJECT_ID,
    DESIGN_PROJECT_ID,
    EXECUTION_PROJECT_ID,
    VALIDATION_PROJECT_ID,
    DOD_PROJECT_ID,
    DONE_PROJECT_ID,
    STRESS_PROJECT_ID,
  )

  fun count(): Int = allIds.size

  suspend fun generate(): Int {
    allIds.forEach { id ->
      storage.deleteProject(id)
    }
    storage.saveProject(scopeProject())
    storage.saveProject(researchProject())
    storage.saveProject(designProject())
    storage.saveProject(executionProject())
    storage.saveProject(validationProject())
    storage.saveProject(dodProject())
    storage.saveProject(doneProject())
    storage.saveProject(stressProject())
    return allIds.size
  }

  /** Just starting out — the opening round of the first phase, sparsely answered. */
  private fun scopeProject(): Project {
    val created = daysAgo(9)
    val roundId = "${SCOPE_PROJECT_ID}-r1"
    return Project(
      id = SCOPE_PROJECT_ID,
      synopsis = "A no-fuss method for making cold brew coffee concentrate at home.",
      editableTitle = "Cold brew",
      status = "Draft",
      questions = listOf(
        sampleQuestion(
          id = "scope-q1",
          text = "What is the best ratio of coffee to water?",
          timestamp = created,
          roundId = roundId,
          answers = listOf(
            completeAnswer(
              questionId = "scope-q1",
              text = "I have been using a 1:5 ratio of coffee to water and it comes out " +
                "concentrated enough to dilute with milk or water.",
              createdAt = daysAgo(7),
            ),
          ),
        ),
        sampleQuestion(
          id = "scope-q2",
          text = "How long should it steep?",
          timestamp = created,
          roundId = roundId,
          answers = listOf(
            completeAnswer(
              questionId = "scope-q2",
              text = "18 to 24 hours in the fridge seems right; anything shorter tastes weak.",
              createdAt = daysAgo(6),
            ),
          ),
        ),
        sampleQuestion(id = "scope-q3", text = "What grind size works best for cold brew?", timestamp = created, roundId = roundId),
        sampleQuestion(id = "scope-q4", text = "How long can the concentrate stay fresh in the fridge?", timestamp = created, roundId = roundId),
        sampleQuestion(id = "scope-q5", text = "Do I need a filter bag or is a regular cheesecloth enough?", timestamp = created, roundId = roundId),
        sampleQuestion(id = "scope-q6", text = "What is a simple way to serve it without fancy equipment?", timestamp = created, roundId = roundId),
        sampleQuestion(id = "scope-q7", text = "How much concentrate does one batch yield?", timestamp = created, roundId = roundId),
      ),
      rounds = listOf(
        sampleRound(
          projectId = SCOPE_PROJECT_ID,
          roundId = roundId,
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 1,
          startedAt = created,
        ),
      ),
      createdAt = created,
      updatedAt = created,
    )
  }

  /** Early research — scope wrapped up, the Research round is partway through. */
  private fun researchProject(): Project {
    val created = daysAgo(14)
    return Project(
      id = RESEARCH_PROJECT_ID,
      synopsis = "Cook more plant-based dinners the whole family actually eats, without " +
        "reaching for pricey meat substitutes or landing on cheese-heavy standbys. I want " +
        "a short list of reliable, fast recipes before committing to it as a habit.",
      editableTitle = "Plant-based dinners",
      status = "Draft",
      questions = listOf(
        answeredQuestion(
          id = "research-r1-q1",
          roundId = "${RESEARCH_PROJECT_ID}-r1",
          text = "What is the primary problem this project solves?",
          timestamp = created,
          answerText = "We reach for meat out of habit and my plant-based standbys are all " +
            "heavy on cheese or packaged substitutes. I want reliable recipes, not a " +
            "Pinterest graveyard.",
          answeredAt = daysAgo(13),
        ),
        answeredQuestion(
          id = "research-r1-q2",
          roundId = "${RESEARCH_PROJECT_ID}-r1",
          text = "Who is the ideal user or beneficiary?",
          timestamp = created,
          answerText = "My family of five — two adults and three kids — plus me as the cook. " +
            "It only counts if everyone eats it, so the kids' acceptance is a real gate.",
          answeredAt = daysAgo(13),
        ),
        answeredQuestion(
          id = "research-r1-q3",
          roundId = "${RESEARCH_PROJECT_ID}-r1",
          text = "What is the single most important goal?",
          timestamp = created,
          answerText = "Cook plant-based at least five nights a week by September, using a " +
            "short list of recipes that each take under 30 minutes and that all three kids " +
            "rate a 4 out of 5.",
          answeredAt = daysAgo(12),
        ),
        ignoredQuestion(
          id = "research-r1-q4",
          roundId = "${RESEARCH_PROJECT_ID}-r1",
          text = "What assumptions are you making?",
          timestamp = created,
          ignoredAt = daysAgo(11),
        ),
        answeredQuestion(
          id = "research-r2-q5",
          roundId = "${RESEARCH_PROJECT_ID}-r2",
          text = "What similar projects or competitors have you looked at?",
          timestamp = daysAgo(9),
          answerText = "Meal-planning apps like Mealime and Plant Jammer, plus a few vegan " +
            "meal-prep channels. Most assume you already love tofu and specialty aisles.",
          answeredAt = daysAgo(8),
        ),
        answeredQuestion(
          id = "research-r2-q6",
          roundId = "${RESEARCH_PROJECT_ID}-r2",
          text = "What do existing solutions do badly that you could improve on?",
          timestamp = daysAgo(9),
          answerText = "They stretch for novelty — zucchini-noodle everything — instead of " +
            "nailing five or six genuinely crowd-pleasing basics a picky kid will eat twice " +
            "in a week.",
          answeredAt = daysAgo(7),
        ),
        answeredQuestion(
          id = "research-r2-q7",
          roundId = "${RESEARCH_PROJECT_ID}-r2",
          text = "What is the fastest way to sanity-check this idea before building anything?",
          timestamp = daysAgo(8),
          answerText = "Cook one candidate recipe a week for three weeks and get the kids to " +
            "score each. If three of three land, stop looking and scale that shortlist.",
          answeredAt = daysAgo(6),
        ),
        sampleQuestion(id = "research-r2-q8", text = "What makes your approach different?", timestamp = daysAgo(7), roundId = "${RESEARCH_PROJECT_ID}-r2"),
        sampleQuestion(id = "research-r2-q9", text = "What have others already learned in this space that you can borrow?", timestamp = daysAgo(6), roundId = "${RESEARCH_PROJECT_ID}-r2"),
        draftQuestion(
          id = "research-r2-q10",
          roundId = "${RESEARCH_PROJECT_ID}-r2",
          text = "Who is already solving this for a slightly different audience?",
          timestamp = daysAgo(4),
          draftText = "Budget-meal blogs for large families — their 'make it on Sunday for " +
            "the week' prep patterns are close to what I need.",
          draftUpdatedAt = daysAgo(2),
        ),
        ignoredQuestion(
          id = "research-r2-q11",
          roundId = "${RESEARCH_PROJECT_ID}-r2",
          text = "What is the most surprising thing about your users?",
          timestamp = daysAgo(3),
          ignoredAt = daysAgo(1),
        ),
      ),
      rounds = roundsFor(
        RESEARCH_PROJECT_ID,
        listOf(daysAgo(14) to daysAgo(10), daysAgo(10) to null),
      ),
      createdAt = created,
      updatedAt = daysAgo(1),
    )
  }

  /** Midway — scope and research wrapped up, the Design round is being worked. */
  private fun designProject(): Project {
    val created = daysAgo(21)
    return Project(
      id = DESIGN_PROJECT_ID,
      synopsis = "A tiny, no-pressure language-learning app for my trip to Lisbon — " +
        "one short travel-phrase lesson a day, decent pronunciation audio, and no streaks " +
        "or subscription guilt. I want to hold a stumbling real conversation, not finish " +
        "a leaderboard.",
      editableTitle = "Trip Portuguese app",
      status = "Draft",
      questions = listOf(
        answeredQuestion(
          id = "design-r1-q1",
          roundId = "${DESIGN_PROJECT_ID}-r1",
          text = "What is the primary problem this project solves?",
          timestamp = created,
          answerText = "Every language app I've tried turns into an obligation — streaks, " +
            "notifications, subscription guilt. I want a small tool I actually look forward " +
            "to for ten minutes before bed.",
          answeredAt = daysAgo(20),
        ),
        answeredQuestion(
          id = "design-r1-q2",
          roundId = "${DESIGN_PROJECT_ID}-r1",
          text = "Who is the ideal user or beneficiary?",
          timestamp = created,
          answerText = "Me first — I'm learning Portuguese for a two-week trip — then my " +
            "partner, who has the same goal but hates flashcards.",
          answeredAt = daysAgo(19),
        ),
        answeredQuestion(
          id = "design-r1-q3",
          roundId = "${DESIGN_PROJECT_ID}-r1",
          text = "What is the single most important goal?",
          timestamp = created,
          answerText = "Hold a real, if stumbling, conversation by the trip in six months — " +
            "order food, ask directions, make small talk. Everything else is secondary.",
          answeredAt = daysAgo(18),
        ),
        ignoredQuestion(
          id = "design-r1-q4",
          roundId = "${DESIGN_PROJECT_ID}-r1",
          text = "What assumptions are you making?",
          timestamp = created,
          ignoredAt = daysAgo(17),
        ),
        answeredQuestion(
          id = "design-r2-q5",
          roundId = "${DESIGN_PROJECT_ID}-r2",
          text = "What similar projects or competitors have you looked at?",
          timestamp = daysAgo(16),
          answerText = "Duolingo (gamey and broad), Anki (powerful but cold), and Clozemaster " +
            "(sentence-based). They either chase engagement or assume you already know how " +
            "to study.",
          answeredAt = daysAgo(15),
        ),
        answeredQuestion(
          id = "design-r2-q6",
          roundId = "${DESIGN_PROJECT_ID}-r2",
          text = "What do existing solutions do badly that you could improve on?",
          timestamp = daysAgo(15),
          answerText = "They drill vocabulary in isolation and never let me just converse. " +
            "I want sentences I'd actually say on a trip, read aloud.",
          answeredAt = daysAgo(14),
        ),
        ignoredQuestion(
          id = "design-r2-q7",
          roundId = "${DESIGN_PROJECT_ID}-r2",
          text = "What makes your approach different?",
          timestamp = daysAgo(14),
          ignoredAt = daysAgo(13),
        ),
        answeredQuestion(
          id = "design-r3-q8",
          roundId = "${DESIGN_PROJECT_ID}-r3",
          text = "What are the key features?",
          timestamp = daysAgo(10),
          answerText = "A daily deck of ten travel-phrase cards with text-to-speech, a " +
            "'likely in Lisbon' tag, and a strength score that surfaces the phrases least " +
            "likely to stick.",
          answeredAt = daysAgo(9),
        ),
        answeredQuestion(
          id = "design-r3-q9",
          roundId = "${DESIGN_PROJECT_ID}-r3",
          text = "What is the one thing that must just work?",
          timestamp = daysAgo(10),
          answerText = "The pronunciation audio — if I can't hear how a phrase should sound, " +
            "the app is worse than a paper phrasebook.",
          answeredAt = daysAgo(8),
        ),
        answeredQuestion(
          id = "design-r3-q10",
          roundId = "${DESIGN_PROJECT_ID}-r3",
          text = "What is the core workflow?",
          timestamp = daysAgo(9),
          answerText = "Open the app, ten cards, swipe right if I'd say it out loud, left " +
            "if not, done in five minutes. No streaks, no pressure.",
          answeredAt = daysAgo(7),
        ),
        draftQuestion(
          id = "design-r3-q11",
          roundId = "${DESIGN_PROJECT_ID}-r3",
          text = "What is the hook that makes a first-time user sit up?",
          timestamp = daysAgo(6),
          draftText = "Your very first card is a question you'll likely actually need: " +
            "'Is there a vegetarian version?'",
          draftUpdatedAt = daysAgo(2),
        ),
        sampleQuestion(id = "design-r3-q12", text = "What is the smallest demo that shows the core idea moving?", timestamp = daysAgo(5), roundId = "${DESIGN_PROJECT_ID}-r3"),
        sampleQuestion(id = "design-r3-q13", text = "What is the part you will iterate on most?", timestamp = daysAgo(4), roundId = "${DESIGN_PROJECT_ID}-r3"),
        ignoredQuestion(
          id = "design-r3-q14",
          roundId = "${DESIGN_PROJECT_ID}-r3",
          text = "What is one feature you are excited about?",
          timestamp = daysAgo(4),
          ignoredAt = daysAgo(1),
        ),
      ),
      rounds = roundsFor(
        DESIGN_PROJECT_ID,
        listOf(daysAgo(21) to daysAgo(16), daysAgo(16) to daysAgo(11), daysAgo(11) to null),
      ),
      createdAt = created,
      updatedAt = daysAgo(2),
    )
  }

  /** Maturing — three phases wrapped up, the Execution Plan round is well underway. */
  private fun executionProject(): Project {
    val created = daysAgo(30)
    return Project(
      id = EXECUTION_PROJECT_ID,
      synopsis = "Offering home energy audits as a side business: walk through drafty " +
        "houses, take measurements, and hand owners one prioritized sheet of upgrades. " +
        "Right now the open question is the launch plan — tools, pricing, scheduling, and " +
        "the first handful of paid clients.",
      editableTitle = "Home energy audits",
      status = "Draft",
      questions = listOf(
        answeredQuestion(
          id = "exec-r1-q1",
          roundId = "${EXECUTION_PROJECT_ID}-r1",
          text = "What is the primary problem this project solves?",
          timestamp = created,
          answerText = "Homeowners get audited and handed a giant binder of upgrades stuck " +
            "behind jargon. I want to walk in, take measurements, and hand over one sheet " +
            "they can act on the same week.",
          answeredAt = daysAgo(29),
        ),
        answeredQuestion(
          id = "exec-r1-q2",
          roundId = "${EXECUTION_PROJECT_ID}-r1",
          text = "Who is the ideal user or beneficiary?",
          timestamp = created,
          answerText = "Homeowners aged 35-65 in old, drafty houses who suspect they're " +
            "throwing money away and want a calm look — not a sales pitch for new windows.",
          answeredAt = daysAgo(28),
        ),
        answeredQuestion(
          id = "exec-r1-q3",
          roundId = "${EXECUTION_PROJECT_ID}-r1",
          text = "What is the single most important goal?",
          timestamp = created,
          answerText = "Do ten paid audits in the first year, with every client leaving " +
            "with a prioritized list they say they understand in plain English.",
          answeredAt = daysAgo(27),
        ),
        ignoredQuestion(
          id = "exec-r1-q4",
          roundId = "${EXECUTION_PROJECT_ID}-r1",
          text = "What assumptions are you making?",
          timestamp = created,
          ignoredAt = daysAgo(26),
        ),
        answeredQuestion(
          id = "exec-r2-q5",
          roundId = "${EXECUTION_PROJECT_ID}-r2",
          text = "What similar projects or competitors have you looked at?",
          timestamp = daysAgo(23),
          answerText = "A few local energy auditors charge $200-400 and hand over a " +
            "software-generated report; the utilities' cheap audits exist mainly to sell " +
            "insulation financing.",
          answeredAt = daysAgo(22),
        ),
        answeredQuestion(
          id = "exec-r2-q6",
          roundId = "${EXECUTION_PROJECT_ID}-r2",
          text = "What do existing solutions do badly that you could improve on?",
          timestamp = daysAgo(22),
          answerText = "The reports read like regulatory filings. Nobody leaves with 'do " +
            "these three things first, they'll save the most.'",
          answeredAt = daysAgo(21),
        ),
        answeredQuestion(
          id = "exec-r2-q7",
          roundId = "${EXECUTION_PROJECT_ID}-r2",
          text = "What is the fastest way to sanity-check this idea before building anything?",
          timestamp = daysAgo(21),
          answerText = "Do a free practice audit on a neighbor's house, time it, and ask one " +
            "plain question: would you have paid for that?",
          answeredAt = daysAgo(20),
        ),
        ignoredQuestion(
          id = "exec-r2-q8",
          roundId = "${EXECUTION_PROJECT_ID}-r2",
          text = "What makes your approach different?",
          timestamp = daysAgo(20),
          ignoredAt = daysAgo(19),
        ),
        answeredQuestion(
          id = "exec-r3-q9",
          roundId = "${EXECUTION_PROJECT_ID}-r3",
          text = "What are the key features?",
          timestamp = daysAgo(17),
          answerText = "A 90-minute walk-through checklist, a thermal pass of the envelope, " +
            "and a one-page deliverable: top three fixes ranked by cost, effort, and payback.",
          answeredAt = daysAgo(16),
        ),
        answeredQuestion(
          id = "exec-r3-q10",
          roundId = "${EXECUTION_PROJECT_ID}-r3",
          text = "What is the core workflow?",
          timestamp = daysAgo(16),
          answerText = "Owner books online, I show up and measure, report emailed within " +
            "48 hours, then an optional 20-minute call to walk through it.",
          answeredAt = daysAgo(15),
        ),
        ignoredQuestion(
          id = "exec-r3-q11",
          roundId = "${EXECUTION_PROJECT_ID}-r3",
          text = "What is the value proposition?",
          timestamp = daysAgo(15),
          ignoredAt = daysAgo(14),
        ),
        answeredQuestion(
          id = "exec-r4-q12",
          roundId = "${EXECUTION_PROJECT_ID}-r4",
          text = "What is the very first step you need to take?",
          timestamp = daysAgo(11),
          answerText = "Do the free neighbor practice audit, buy the thermal camera, and " +
            "time everything — I still don't know if 90 minutes is realistic.",
          answeredAt = daysAgo(9),
        ),
        answeredQuestion(
          id = "exec-r4-q13",
          roundId = "${EXECUTION_PROJECT_ID}-r4",
          text = "What resources (time, money, tools) are currently available?",
          timestamp = daysAgo(11),
          answerText = "Weekday evenings plus Saturdays, about $800 to start (camera and " +
            "insurance), and a car. Skills: I've fixed up my own 1930s house for years.",
          answeredAt = daysAgo(9),
        ),
        answeredQuestion(
          id = "exec-r4-q14",
          roundId = "${EXECUTION_PROJECT_ID}-r4",
          text = "What is the estimated timeline?",
          timestamp = daysAgo(10),
          answerText = "Practice audit in March, friends-and-family audits in April to tune " +
            "the report, paid launch in May.",
          answeredAt = daysAgo(8),
        ),
        answeredQuestion(
          id = "exec-r4-q15",
          roundId = "${EXECUTION_PROJECT_ID}-r4",
          text = "What is the quickest path to value?",
          timestamp = daysAgo(10),
          answerText = "Skip the website — post in the two neighborhood Facebook groups and " +
            "let word of mouth run.",
          answeredAt = daysAgo(7),
        ),
        draftQuestion(
          id = "exec-r4-q16",
          roundId = "${EXECUTION_PROJECT_ID}-r4",
          text = "What technologies would you like to use?",
          timestamp = daysAgo(8),
          draftText = "Leaning toward a FLIR compact camera, a scheduling tool like Calendly, " +
            "and a template in Canva for the one-pager.",
          draftUpdatedAt = daysAgo(3),
        ),
        sampleQuestion(id = "exec-r4-q17", text = "What are three key milestones for the first month?", timestamp = daysAgo(6), roundId = "${EXECUTION_PROJECT_ID}-r4"),
        sampleQuestion(id = "exec-r4-q18", text = "What is the estimated total budget?", timestamp = daysAgo(5), roundId = "${EXECUTION_PROJECT_ID}-r4"),
        ignoredQuestion(
          id = "exec-r4-q19",
          roundId = "${EXECUTION_PROJECT_ID}-r4",
          text = "What is the fallback if everything breaks?",
          timestamp = daysAgo(4),
          ignoredAt = daysAgo(2),
        ),
      ),
      rounds = roundsFor(
        EXECUTION_PROJECT_ID,
        listOf(
          daysAgo(30) to daysAgo(24),
          daysAgo(24) to daysAgo(18),
          daysAgo(18) to daysAgo(12),
          daysAgo(12) to null,
        ),
      ),
      createdAt = created,
      updatedAt = daysAgo(2),
    )
  }

  /** Deep — four phases wrapped up, the Validation Plan round is mostly resolved. */
  private fun validationProject(): Project {
    val created = daysAgo(45)
    return Project(
      id = VALIDATION_PROJECT_ID,
      synopsis = "Starting a small community garden on the empty lot our block rents from " +
        "the city. The plan is to prove it's worth continuing next season: how many " +
        "households actually tend plots, how much food comes out, and whether volunteers " +
        "keep showing up past July.",
      editableTitle = "Block community garden",
      status = "Draft",
      questions = listOf(
        answeredQuestion(
          id = "val-r1-q1",
          roundId = "${VALIDATION_PROJECT_ID}-r1",
          text = "What is the primary problem this project solves?",
          timestamp = created,
          answerText = "Our block's empty lot sits fenced and unused, and past garden " +
            "attempts fizzled because nobody agreed on who it was for or how it would run.",
          answeredAt = daysAgo(44),
        ),
        answeredQuestion(
          id = "val-r1-q2",
          roundId = "${VALIDATION_PROJECT_ID}-r1",
          text = "Who is the ideal user or beneficiary?",
          timestamp = created,
          answerText = "The households on our block — families who want fresh produce, " +
            "older residents who want a reason to sit outside, and kids who'd help if the " +
            "adults let them.",
          answeredAt = daysAgo(43),
        ),
        answeredQuestion(
          id = "val-r1-q3",
          roundId = "${VALIDATION_PROJECT_ID}-r1",
          text = "What is the single most important goal?",
          timestamp = created,
          answerText = "Get ten households actively tending beds by the end of the season " +
            "and enough agreement on rules that it survives its first winter.",
          answeredAt = daysAgo(42),
        ),
        answeredQuestion(
          id = "val-r2-q4",
          roundId = "${VALIDATION_PROJECT_ID}-r2",
          text = "What similar projects or competitors have you looked at?",
          timestamp = daysAgo(36),
          answerText = "Two neighborhood gardens that thrived and one that petered out; both " +
            "survivors had a paid coordinator slot and a waitlist.",
          answeredAt = daysAgo(35),
        ),
        answeredQuestion(
          id = "val-r2-q5",
          roundId = "${VALIDATION_PROJECT_ID}-r2",
          text = "What do existing solutions do badly that you could improve on?",
          timestamp = daysAgo(35),
          answerText = "Most start too big — thirty beds nobody can fill, then a volunteer " +
            "collapse in July. Ours should start with a handful of shared beds and scale on " +
            "demand.",
          answeredAt = daysAgo(34),
        ),
        answeredQuestion(
          id = "val-r2-q6",
          roundId = "${VALIDATION_PROJECT_ID}-r2",
          text = "What is the fastest way to sanity-check this idea before building anything?",
          timestamp = daysAgo(34),
          answerText = "A sign-up sheet taped to the lot's fence for two weeks. If fewer than " +
            "eight households sign, the interest isn't there yet.",
          answeredAt = daysAgo(33),
        ),
        answeredQuestion(
          id = "val-r3-q7",
          roundId = "${VALIDATION_PROJECT_ID}-r3",
          text = "What are the key features?",
          timestamp = daysAgo(29),
          answerText = "Ten 4x8 raised beds, one wheelchair-accessible bed, a rain barrel, a " +
            "shared tool lockbox, and a simple bed-rotation rulebook in three languages.",
          answeredAt = daysAgo(28),
        ),
        answeredQuestion(
          id = "val-r3-q8",
          roundId = "${VALIDATION_PROJECT_ID}-r3",
          text = "What is the core workflow?",
          timestamp = daysAgo(28),
          answerText = "Spring sign-up, plot assignment by lottery, monthly work-and-learn " +
            "Saturday, harvest shares to whoever showed up.",
          answeredAt = daysAgo(27),
        ),
        ignoredQuestion(
          id = "val-r3-q9",
          roundId = "${VALIDATION_PROJECT_ID}-r3",
          text = "What are the non-negotiable features?",
          timestamp = daysAgo(27),
          ignoredAt = daysAgo(26),
        ),
        answeredQuestion(
          id = "val-r4-q10",
          roundId = "${VALIDATION_PROJECT_ID}-r4",
          text = "What is the very first step you need to take?",
          timestamp = daysAgo(22),
          answerText = "Get the city's lease letter in writing and the liability waiver " +
            "signed before anyone digs a hole.",
          answeredAt = daysAgo(21),
        ),
        answeredQuestion(
          id = "val-r4-q11",
          roundId = "${VALIDATION_PROJECT_ID}-r4",
          text = "What resources are still needed?",
          timestamp = daysAgo(21),
          answerText = "Soil and lumber eat most of the budget; tools are donated, there's a " +
            "hose bib on the building, and no irrigation money in year one.",
          answeredAt = daysAgo(20),
        ),
        answeredQuestion(
          id = "val-r4-q12",
          roundId = "${VALIDATION_PROJECT_ID}-r4",
          text = "What are three key milestones for the first month?",
          timestamp = daysAgo(20),
          answerText = "Lease signed, ten beds built, eight households signed.",
          answeredAt = daysAgo(19),
        ),
        ignoredQuestion(
          id = "val-r4-q13",
          roundId = "${VALIDATION_PROJECT_ID}-r4",
          text = "What is the fallback if everything breaks?",
          timestamp = daysAgo(19),
          ignoredAt = daysAgo(18),
        ),
        answeredQuestion(
          id = "val-r5-q14",
          roundId = "${VALIDATION_PROJECT_ID}-r5",
          text = "How will you measure progress?",
          timestamp = daysAgo(16),
          answerText = "Two numbers a month: plots actively tended and harvest pounds logged, " +
            "plus a rough volunteer-hours count at work days.",
          answeredAt = daysAgo(14),
        ),
        answeredQuestion(
          id = "val-r5-q15",
          roundId = "${VALIDATION_PROJECT_ID}-r5",
          text = "What feedback will you gather?",
          timestamp = daysAgo(15),
          answerText = "A magnet board by the gate — one column 'working', one 'stuck' — plus " +
            "a short survey at the September harvest potluck.",
          answeredAt = daysAgo(13),
        ),
        answeredQuestion(
          id = "val-r5-q16",
          roundId = "${VALIDATION_PROJECT_ID}-r5",
          text = "What are the top three risks to success?",
          timestamp = daysAgo(14),
          answerText = "1) Interest that doesn't survive July heat. 2) One or two aggressive " +
            "gardeners crowding everyone else out. 3) The city pulling the lease over " +
            "insurance.",
          answeredAt = daysAgo(12),
        ),
        answeredQuestion(
          id = "val-r5-q17",
          roundId = "${VALIDATION_PROJECT_ID}-r5",
          text = "What could go wrong?",
          timestamp = daysAgo(13),
          answerText = "Water access dies — the building's hose spigot is ancient and the " +
            "landlord isn't promising anything.",
          answeredAt = daysAgo(11),
        ),
        answeredQuestion(
          id = "val-r5-q18",
          roundId = "${VALIDATION_PROJECT_ID}-r5",
          text = "Who is the first person you will show this to?",
          timestamp = daysAgo(12),
          answerText = "My neighbor Fran, who's run our block's flower boxes for four seasons — " +
            "she'll say yes fast if it's workable.",
          answeredAt = daysAgo(10),
        ),
        sampleQuestion(id = "val-r5-q19", text = "What would convince a skeptic this works?", timestamp = daysAgo(9), roundId = "${VALIDATION_PROJECT_ID}-r5"),
        sampleQuestion(id = "val-r5-q20", text = "What would you measure to know it is good, not just done?", timestamp = daysAgo(8), roundId = "${VALIDATION_PROJECT_ID}-r5"),
        draftQuestion(
          id = "val-r5-q21",
          roundId = "${VALIDATION_PROJECT_ID}-r5",
          text = "What is the smallest test that proves the core idea?",
          timestamp = daysAgo(7),
          draftText = "Three shared beds in a corner this spring — if casual neighbors tend " +
            "them without anyone nagging, scale to ten next year.",
          draftUpdatedAt = daysAgo(2),
        ),
      ),
      rounds = roundsFor(
        VALIDATION_PROJECT_ID,
        listOf(
          daysAgo(45) to daysAgo(38),
          daysAgo(38) to daysAgo(31),
          daysAgo(31) to daysAgo(24),
          daysAgo(24) to daysAgo(17),
          daysAgo(17) to null,
        ),
      ),
      createdAt = created,
      updatedAt = daysAgo(1),
    )
  }

  /** Nearly done — all five earlier phases wrapped up, the Definition of Done round is open. */
  private fun dodProject(): Project {
    val created = daysAgo(60)
    return Project(
      id = DOD_PROJECT_ID,
      synopsis = "A small open-source command-line tool that turns a folder of photos into " +
        "a print-ready zine PDF. It builds locally and I use it myself, but it needs to be " +
        "good enough that a stranger can install and run it — so the current focus is " +
        "acceptance criteria, docs, and a release path.",
      editableTitle = "Zine-maker CLI",
      status = "Draft",
      questions = listOf(
        answeredQuestion(
          id = "dod-r1-q1",
          roundId = "${DOD_PROJECT_ID}-r1",
          text = "What is the primary problem this project solves?",
          timestamp = created,
          answerText = "Turning phone photos into printable zines is fiddly — resize, " +
            "arrange, export, and hope the print shop doesn't mangle it. I want one command " +
            "that does the whole layout.",
          answeredAt = daysAgo(59),
        ),
        answeredQuestion(
          id = "dod-r1-q2",
          roundId = "${DOD_PROJECT_ID}-r1",
          text = "Who is the ideal user or beneficiary?",
          timestamp = created,
          answerText = "Indie zine makers and photo teachers with a laptop and no InDesign " +
            "budget. I'm the first user, but I want strangers able to run it too.",
          answeredAt = daysAgo(58),
        ),
        answeredQuestion(
          id = "dod-r1-q3",
          roundId = "${DOD_PROJECT_ID}-r1",
          text = "What is the single most important goal?",
          timestamp = created,
          answerText = "A CLI that turns a folder of images into a print-ready PDF in three " +
            "minutes, configurable enough for the odd spec list real print shops use.",
          answeredAt = daysAgo(57),
        ),
        answeredQuestion(
          id = "dod-r2-q4",
          roundId = "${DOD_PROJECT_ID}-r2",
          text = "What similar projects or competitors have you looked at?",
          timestamp = daysAgo(50),
          answerText = "BookletCreator, LibreOffice's print-to-booklet, and a couple of " +
            "GitHub scripts. All need GUI clicking or produce PDFs that trip up print shops.",
          answeredAt = daysAgo(49),
        ),
        answeredQuestion(
          id = "dod-r2-q5",
          roundId = "${DOD_PROJECT_ID}-r2",
          text = "What do existing solutions do badly that you could improve on?",
          timestamp = daysAgo(49),
          answerText = "They ignore real-world print specs — bleed, page counts in multiples " +
            "of four, gutter shifts.",
          answeredAt = daysAgo(48),
        ),
        answeredQuestion(
          id = "dod-r3-q6",
          roundId = "${DOD_PROJECT_ID}-r3",
          text = "What are the key features?",
          timestamp = daysAgo(42),
          answerText = "One command, a simple TOML config, automatic booklet ordering, and a " +
            "dry-run mode that emits an exact cover/order blueprint before the final PDF.",
          answeredAt = daysAgo(41),
        ),
        answeredQuestion(
          id = "dod-r3-q7",
          roundId = "${DOD_PROJECT_ID}-r3",
          text = "What is the one thing that must just work?",
          timestamp = daysAgo(41),
          answerText = "Getting a PDF to a 2-up / 4-up booklet at a print shop without rework.",
          answeredAt = daysAgo(40),
        ),
        ignoredQuestion(
          id = "dod-r3-q8",
          roundId = "${DOD_PROJECT_ID}-r3",
          text = "What is the core workflow?",
          timestamp = daysAgo(40),
          ignoredAt = daysAgo(39),
        ),
        answeredQuestion(
          id = "dod-r4-q9",
          roundId = "${DOD_PROJECT_ID}-r4",
          text = "What is the very first step you need to take?",
          timestamp = daysAgo(34),
          answerText = "Get the layout math right on paper — the page-shuffle formula — " +
            "before writing code, and lock in the four-page-multiple constraint.",
          answeredAt = daysAgo(33),
        ),
        answeredQuestion(
          id = "dod-r4-q10",
          roundId = "${DOD_PROJECT_ID}-r4",
          text = "What resources (time, money, tools) are currently available?",
          timestamp = daysAgo(33),
          answerText = "A couple evenings a week, zero budget, a decade of Kotlin, and three " +
            "real print shops within walking distance willing to test output.",
          answeredAt = daysAgo(32),
        ),
        answeredQuestion(
          id = "dod-r4-q11",
          roundId = "${DOD_PROJECT_ID}-r4",
          text = "What is the estimated timeline?",
          timestamp = daysAgo(32),
          answerText = "Layout math plus core layout in six weeks, then a docs pass, then the " +
            "packaging and release pass.",
          answeredAt = daysAgo(31),
        ),
        answeredQuestion(
          id = "dod-r5-q12",
          roundId = "${DOD_PROJECT_ID}-r5",
          text = "How will you measure progress?",
          timestamp = daysAgo(26),
          answerText = "A single acceptance script that renders a fixture folder and fails if " +
            "the page order, bleed, or page count is wrong.",
          answeredAt = daysAgo(25),
        ),
        answeredQuestion(
          id = "dod-r5-q13",
          roundId = "${DOD_PROJECT_ID}-r5",
          text = "What is your biggest technical risk?",
          timestamp = daysAgo(25),
          answerText = "PDF generation libraries do 90% of the work but the last 10% — exact " +
            "bleed boxes and crop marks — is where it breaks.",
          answeredAt = daysAgo(24),
        ),
        answeredQuestion(
          id = "dod-r5-q14",
          roundId = "${DOD_PROJECT_ID}-r5",
          text = "What feedback will you gather?",
          timestamp = daysAgo(24),
          answerText = "Give two local zine makers a real PDF and time how long it takes them " +
            "to print it successfully.",
          answeredAt = daysAgo(23),
        ),
        answeredQuestion(
          id = "dod-r6-q15",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "How will you know if the project is successful?",
          timestamp = daysAgo(19),
          answerText = "A stranger installs it, runs one command, and produces a printable " +
            "PDF on the first try without reading the README twice.",
          answeredAt = daysAgo(16),
        ),
        answeredQuestion(
          id = "dod-r6-q16",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "How will you know you are done?",
          timestamp = daysAgo(18),
          answerText = "The acceptance script passes, two strangers get through the README " +
            "unassisted, and I've stopped worrying about the version number.",
          answeredAt = daysAgo(15),
        ),
        answeredQuestion(
          id = "dod-r6-q17",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "What milestones define completion?",
          timestamp = daysAgo(17),
          answerText = "1) Layout math tests green, 2) dry-run blueprint mode, 3) two of three " +
            "print shops confirm, 4) a tagged 1.0 with docs.",
          answeredAt = daysAgo(14),
        ),
        answeredQuestion(
          id = "dod-r6-q18",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "What has to be true before you call it shipped?",
          timestamp = daysAgo(16),
          answerText = "Nobody should need me — install, docs, and one example folder should " +
            "be enough to succeed.",
          answeredAt = daysAgo(13),
        ),
        answeredQuestion(
          id = "dod-r6-q19",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "What is the final deliverable a teammate could pick up and use?",
          timestamp = daysAgo(15),
          answerText = "A signed installer plus a one-page quickstart, and the acceptance " +
            "script so a future maintainer can check nothing regressed.",
          answeredAt = daysAgo(12),
        ),
        draftQuestion(
          id = "dod-r6-q20",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "What will you promote or distribute the final result?",
          timestamp = daysAgo(14),
          draftText = "Post to two or three zine-maker forums and one photo-teacher mailing " +
            "list, with a five-image GIF of the one-command flow.",
          draftUpdatedAt = daysAgo(3),
        ),
        ignoredQuestion(
          id = "dod-r6-q21",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "What is your go-to-market story?",
          timestamp = daysAgo(13),
          ignoredAt = daysAgo(4),
        ),
        ignoredQuestion(
          id = "dod-r6-q22",
          roundId = "${DOD_PROJECT_ID}-r6",
          text = "What does done explicitly not include?",
          timestamp = daysAgo(12),
          ignoredAt = daysAgo(2),
        ),
      ),
      rounds = roundsFor(
        DOD_PROJECT_ID,
        listOf(
          daysAgo(60) to daysAgo(52),
          daysAgo(52) to daysAgo(44),
          daysAgo(44) to daysAgo(36),
          daysAgo(36) to daysAgo(28),
          daysAgo(28) to daysAgo(20),
          daysAgo(20) to null,
        ),
      ),
      createdAt = created,
      updatedAt = daysAgo(2),
    )
  }

  /** Finished — all six phases walked, and every open question in the final round is resolved. */
  private fun doneProject(): Project {
    val created = daysAgo(35)
    return Project(
      id = DONE_PROJECT_ID,
      synopsis = "A small mobile puzzle game I have been designing in my head for months. " +
        "I want to ship a polished MVP on the Play Store within the next two quarters while " +
        "keeping my day job, using weekend time and a small budget. The biggest challenge is " +
        "staying honest about scope: keeping the game tiny while still making it feel complete.",
      editableTitle = "Ship my tiny puzzle game",
      status = "Draft",
      questions = listOf(
        answeredQuestion(
          id = "done-r1-q1",
          roundId = "${DONE_PROJECT_ID}-r1",
          text = "What is the primary problem this project solves?",
          timestamp = created,
          answerText = "Players looking for a quick, thoughtful break get a bite-sized puzzle " +
            "game they can finish in 3-5 minutes. No accounts, no forced progression, no " +
            "pay-to-win energy systems; just a satisfying loop that fits into a commute or " +
            "a coffee break.",
          answeredAt = daysAgo(34),
        ),
        answeredQuestion(
          id = "done-r1-q2",
          roundId = "${DONE_PROJECT_ID}-r1",
          text = "Who is the ideal user or beneficiary?",
          timestamp = created,
          answerText = "Busy commuters in their late 20s to 40s who enjoy casual mobile games " +
            "but hate pay-to-win mechanics. They value calm visuals and do not want a game " +
            "that demands daily login streaks or loot boxes.",
          answeredAt = daysAgo(34),
        ),
        answeredQuestion(
          id = "done-r1-q3",
          roundId = "${DONE_PROJECT_ID}-r1",
          text = "What is the single most important goal?",
          timestamp = daysAgo(34),
          answerText = "Launch a playable, polished MVP on the Play Store within the next two " +
            "quarters (by end of Q2) and reach at least 1,000 installs in the first month, " +
            "mostly from organic discovery.",
          answeredAt = daysAgo(33),
        ),
        ignoredQuestion(
          id = "done-r1-q4",
          roundId = "${DONE_PROJECT_ID}-r1",
          text = "What are the long-term goals?",
          timestamp = daysAgo(33),
          ignoredAt = daysAgo(32),
        ),
        answeredQuestion(
          id = "done-r2-q5",
          roundId = "${DONE_PROJECT_ID}-r2",
          text = "What similar projects or competitors have you looked at?",
          timestamp = daysAgo(30),
          answerText = "Glanced at the match-3 giants and the small-but-beloved puzzlers; most " +
            "either monetize hard or chase endless content. The reference point is a tiny " +
            "Polish puzzler — calm, bought once, no ads.",
          answeredAt = daysAgo(29),
        ),
        answeredQuestion(
          id = "done-r2-q6",
          roundId = "${DONE_PROJECT_ID}-r2",
          text = "What do existing solutions do badly that you could improve on?",
          timestamp = daysAgo(29),
          answerText = "The giants drown you in currencies and time-gates; the nice ones " +
            "underdeliver content. I want the calm feel with real depth: one move type, but " +
            "crafted levels that keep surprising.",
          answeredAt = daysAgo(28),
        ),
        answeredQuestion(
          id = "done-r3-q7",
          roundId = "${DONE_PROJECT_ID}-r3",
          text = "What are the key features?",
          timestamp = daysAgo(25),
          answerText = "The core loop is sliding a single piece across a 5x5 board to match " +
            "targets. Around 60 hand-crafted levels with a gentle difficulty curve, a hint " +
            "system, and settings for sound and haptics. No accounts, no leaderboards, no " +
            "daily rewards.",
          answeredAt = daysAgo(24),
        ),
        answeredQuestion(
          id = "done-r3-q8",
          roundId = "${DONE_PROJECT_ID}-r3",
          text = "What is the minimum viable product (MVP) version?",
          timestamp = daysAgo(24),
          answerText = "One mechanic, 60 levels, a level-select screen, local high-score " +
            "saving, a basic settings menu, and an About screen. Cut everything else: no " +
            "music composer, no cloud saves, no achievements, no online leaderboard.",
          answeredAt = daysAgo(23),
        ),
        answeredQuestion(
          id = "done-r4-q9",
          roundId = "${DONE_PROJECT_ID}-r4",
          text = "What is the target completion date?",
          timestamp = daysAgo(20),
          answerText = "June 30 for the Play Store release. Level design finished by end of " +
            "March, a closed beta with friends by end of April, and a soft launch through " +
            "May.",
          answeredAt = daysAgo(19),
        ),
        answeredQuestion(
          id = "done-r4-q10",
          roundId = "${DONE_PROJECT_ID}-r4",
          text = "What resources (time, money, tools) are currently available?",
          timestamp = daysAgo(19),
          answerText = "About eight hours a week of free time (Saturdays plus two weekday " +
            "evenings), roughly $300 budget for asset packs, a testing device and the " +
            "one-time developer account fee. Existing skills: basic Kotlin and gameplay " +
            "prototyping.",
          answeredAt = daysAgo(18),
        ),
        answeredQuestion(
          id = "done-r4-q11",
          roundId = "${DONE_PROJECT_ID}-r4",
          text = "What is the very first step you need to take?",
          timestamp = daysAgo(18),
          answerText = "Build a three-level vertical slice of the swipe mechanic with real " +
            "touch input before touching the level editor — it decides whether the whole " +
            "design holds.",
          answeredAt = daysAgo(16),
        ),
        answeredQuestion(
          id = "done-r5-q12",
          roundId = "${DONE_PROJECT_ID}-r5",
          text = "What are the top three risks to success?",
          timestamp = daysAgo(14),
          answerText = "1) Scope creep: I keep wanting to add mechanics, so the game could " +
            "balloon past my skill and budget. 2) Burnout from building everything solo on " +
            "weekends. 3) The 'polished but tiny' bar is high; a rough MVP may not convert " +
            "installs into retention.",
          answeredAt = daysAgo(12),
        ),
        answeredQuestion(
          id = "done-r5-q13",
          roundId = "${DONE_PROJECT_ID}-r5",
          text = "What is your biggest technical risk?",
          timestamp = daysAgo(13),
          answerText = "The swipe-to-move gesture was the worry, but the vertical-slice test " +
            "with real touch input confirmed it feels right — that risk is retired.",
          answeredAt = daysAgo(11),
        ),
        answeredQuestion(
          id = "done-r5-q14",
          roundId = "${DONE_PROJECT_ID}-r5",
          text = "What feedback will you gather?",
          timestamp = daysAgo(12),
          answerText = "A closed beta with a group of casual-playing friends; I log time-to-" +
            "first-think and ask one question a session: 'which level made you want to stop?'",
          answeredAt = daysAgo(9),
        ),
        answeredQuestion(
          id = "done-r6-q15",
          roundId = "${DONE_PROJECT_ID}-r6",
          text = "How will you know if the project is successful?",
          timestamp = daysAgo(8),
          answerText = "1,000+ installs in the first month, an average rating above 4.2, and " +
            "a handful of reviews asking for more levels rather than complaining about " +
            "pricing or bugs.",
          answeredAt = daysAgo(6),
        ),
        answeredQuestion(
          id = "done-r6-q16",
          roundId = "${DONE_PROJECT_ID}-r6",
          text = "How will you know you are done?",
          timestamp = daysAgo(8),
          answerText = "The store listing is live, the last beta bug (a save-file crash) is " +
            "fixed, and nothing in the tracker is marked blocking.",
          answeredAt = daysAgo(6),
        ),
        answeredQuestion(
          id = "done-r6-q17",
          roundId = "${DONE_PROJECT_ID}-r6",
          text = "What milestones define completion?",
          timestamp = daysAgo(7),
          answerText = "Launch build championed, store pages approved, 100 organic installs in " +
            "week one, a release tag on the repo, then a week of bug sweeps.",
          answeredAt = daysAgo(6),
        ),
        answeredQuestion(
          id = "done-r6-q18",
          roundId = "${DONE_PROJECT_ID}-r6",
          text = "What has to be true before you call it shipped?",
          timestamp = daysAgo(7),
          answerText = "All 60 levels playable start to finish with no known blockers, the " +
            "store page is honest, and a stranger handed the phone would enjoy level one.",
          answeredAt = daysAgo(5),
        ),
        answeredQuestion(
          id = "done-r6-q19",
          roundId = "${DONE_PROJECT_ID}-r6",
          text = "Who signs off on done?",
          timestamp = daysAgo(6),
          answerText = "Me, after my beta testers' last pass — they're the closest thing to " +
            "the real audience I have.",
          answeredAt = daysAgo(5),
        ),
        ignoredQuestion(
          id = "done-r6-q20",
          roundId = "${DONE_PROJECT_ID}-r6",
          text = "What is your go-to-market story?",
          timestamp = daysAgo(5),
          ignoredAt = daysAgo(4),
        ),
        ignoredQuestion(
          id = "done-r6-q21",
          roundId = "${DONE_PROJECT_ID}-r6",
          text = "What is the story you will tell at the end?",
          timestamp = daysAgo(5),
          ignoredAt = daysAgo(3),
        ),
      ),
      rounds = roundsFor(
        DONE_PROJECT_ID,
        listOf(
          daysAgo(35) to daysAgo(30),
          daysAgo(30) to daysAgo(25),
          daysAgo(25) to daysAgo(20),
          daysAgo(20) to daysAgo(14),
          daysAgo(14) to daysAgo(8),
          daysAgo(8) to null,
        ),
      ),
      createdAt = created,
      updatedAt = daysAgo(1),
    )
  }

  /** Extreme-text fixture — very long lorem ipsum everywhere, to stretch the UI. */
  private fun stressProject(): Project {
    val created = daysAgo(3)
    val updated = daysAgo(0, 120)
    val roundId = "${STRESS_PROJECT_ID}-r1"
    return Project(
      id = STRESS_PROJECT_ID,
      synopsis = lipsum(5),
      editableTitle = lipsum(1),
      status = "Draft",
      questions = listOf(
        sampleQuestion(id = "stress-q1", text = lipsum(2), timestamp = daysAgo(3), roundId = roundId),
        sampleQuestion(
          id = "stress-q2",
          text = lipsum(2),
          timestamp = daysAgo(3),
          roundId = roundId,
          answers = listOf(
            completeAnswer(questionId = "stress-q2", text = lipsum(3), createdAt = daysAgo(2)),
          ),
        ),
        sampleQuestion(id = "stress-q3", text = lipsum(1), timestamp = daysAgo(2), roundId = roundId, ignoredAt = daysAgo(2)),
        sampleQuestion(
          id = "stress-q4",
          text = lipsum(2),
          timestamp = daysAgo(2),
          roundId = roundId,
          draftText = lipsum(2),
          draftUpdatedAt = daysAgo(2),
        ),
        sampleQuestion(
          id = "stress-q5",
          text = lipsum(2),
          timestamp = daysAgo(1),
          roundId = roundId,
          answers = listOf(
            completeAnswer(questionId = "stress-q5", text = lipsum(3), createdAt = daysAgo(1)),
          ),
        ),
        sampleQuestion(
          id = "stress-q6",
          text = lipsum(2),
          timestamp = daysAgo(1),
          roundId = roundId,
          answers = listOf(
            completeAnswer(questionId = "stress-q6", text = lipsum(2), createdAt = daysAgo(0, 3)),
          ),
        ),
        sampleQuestion(id = "stress-q7", text = lipsum(2), timestamp = daysAgo(2), roundId = roundId),
        sampleQuestion(id = "stress-q8", text = lipsum(1), timestamp = daysAgo(1), roundId = roundId, ignoredAt = daysAgo(0, 6)),
      ),
      rounds = listOf(
        sampleRound(
          projectId = STRESS_PROJECT_ID,
          roundId = roundId,
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 1,
          startedAt = created,
        ),
      ),
      createdAt = created,
      updatedAt = updated,
    )
  }

  /**
   * Builds a project's round history in library order: one round per traversed
   * phase — [startedAt] to [completedAt] (`null` = still open, the current
   * phase). Round numbers are 1-based and questions reference a round by its
   * `"<projectId>-r<N>"` id.
   */
  private fun roundsFor(
    projectId: String,
    schedule: List<Pair<Instant, Instant?>>,
  ): List<Round> = schedule.mapIndexed { index, (startedAt, completedAt) ->
    Round(
      id = "$projectId-r${index + 1}",
      projectId = projectId,
      phase = BuiltInPhase.entries[index],
      roundNumber = index + 1,
      origin = RoundOrigin.Initial,
      startedAt = startedAt,
      completedAt = completedAt,
    )
  }

  private fun answeredQuestion(
    id: String,
    text: String,
    timestamp: Instant,
    roundId: String,
    answerText: String,
    answeredAt: Instant,
  ) = sampleQuestion(
    id = id,
    text = text,
    timestamp = timestamp,
    roundId = roundId,
    answers = listOf(
      completeAnswer(questionId = id, text = answerText, createdAt = answeredAt),
    ),
  )

  private fun ignoredQuestion(
    id: String,
    text: String,
    timestamp: Instant,
    roundId: String,
    ignoredAt: Instant,
  ) = sampleQuestion(
    id = id,
    text = text,
    timestamp = timestamp,
    roundId = roundId,
    ignoredAt = ignoredAt,
  )

  private fun draftQuestion(
    id: String,
    text: String,
    timestamp: Instant,
    roundId: String,
    draftText: String,
    draftUpdatedAt: Instant,
  ) = sampleQuestion(
    id = id,
    text = text,
    timestamp = timestamp,
    roundId = roundId,
    draftText = draftText,
    draftUpdatedAt = draftUpdatedAt,
  )

  private fun sampleQuestion(
    id: String,
    text: String,
    timestamp: Instant,
    roundId: String,
    answers: List<Answer> = emptyList(),
    ignoredAt: Instant? = null,
    draftText: String? = null,
    draftUpdatedAt: Instant? = null,
  ) = Question(
    id = id,
    text = text,
    timestamp = timestamp,
    roundId = roundId,
    ignoredAt = ignoredAt,
    answers = answers,
    answerId = answers.lastOrNull()?.id,
    draftText = draftText,
    draftUpdatedAt = draftUpdatedAt,
  )

  private fun sampleRound(
    projectId: String,
    roundId: String,
    phase: Phase,
    roundNumber: Int,
    startedAt: Instant,
    completedAt: Instant? = null,
  ): Round = Round(
    id = roundId,
    projectId = projectId,
    phase = phase,
    roundNumber = roundNumber,
    origin = RoundOrigin.Initial,
    startedAt = startedAt,
    completedAt = completedAt,
  )

  private fun completeAnswer(
    questionId: String,
    text: String,
    createdAt: Instant,
  ): Answer = Answer(
    id = randomUUID(),
    questionId = questionId,
    text = text,
    createdAt = createdAt,
  )

  private val now: Instant = now()

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
    const val SCOPE_PROJECT_ID = "sample-scope"
    const val RESEARCH_PROJECT_ID = "sample-research"
    const val DESIGN_PROJECT_ID = "sample-design"
    const val EXECUTION_PROJECT_ID = "sample-execution"
    const val VALIDATION_PROJECT_ID = "sample-validation"
    const val DOD_PROJECT_ID = "sample-dod"
    const val DONE_PROJECT_ID = "sample-done"
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