package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.defaultTestInstant
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PlanningContextTest {

  // ---------- rendering one question's state ----------

  /**
   * The transcript is only useful if the model can tell the four states apart:
   * answered, drafted, skipped, and open. They are the difference between "go
   * deeper" and "leave it alone", so each is pinned to its own shape.
   */
  @Test
  fun `a committed answer renders as a question and its answer`() {
    val committed = answered("q1", "What is the MVP?", "A menu planner")

    assertEquals(
      "Q: What is the MVP? / A: A menu planner",
      PlanningContext.line(committed),
    )
  }

  @Test
  fun `a draft renders marked as a draft`() {
    val draft = question(
      "q1",
      "Who is this for?",
      draftText = "home cooks",
      draftUpdatedAt = defaultTestInstant,
    )

    assertEquals(
      "Q: Who is this for? / Draft: home cooks",
      PlanningContext.line(draft),
    )
  }

  @Test
  fun `an ignored question renders as a single skipped word`() {
    val ignored = question("q1", "How much will it cost?", ignoredAt = defaultTestInstant)

    assertEquals(
      "Q: How much will it cost? / ${PlanningContext.SkippedNote}",
      PlanningContext.line(ignored),
    )
  }

  @Test
  fun `an unanswered question says so`() {
    assertEquals(
      "Q: What is the timeline? / ${PlanningContext.NotAnsweredNote}",
      PlanningContext.line(question("q1", "What is the timeline?")),
    )
  }

  /**
   * Ignoring is the user passing on the question, so what they wrote for it is
   * not part of the plan going forward — the transcript says "skipped" and stops,
   * rather than spending a whole answer on a question nobody is pursuing.
   */
  @Test
  fun `an ignored question keeps neither its answer nor an unanswered note`() {
    val ignoredAnswered = question(
      "q1",
      "How much will it cost?",
      ignoredAt = defaultTestInstant,
      answers = listOf(answer("q1", "under a thousand", id = "a1")),
    )

    assertEquals(
      "Q: How much will it cost? / ${PlanningContext.SkippedNote}",
      PlanningContext.line(ignoredAnswered),
    )
  }

  /**
   * The whole point of the marker: an answer that existed but did not fit must
   * not read as the open "not yet answered" it used to, and it keeps the
   * answer slot so the line still reads as answered.
   */
  @Test
  fun `an omitted answer renders in the answer slot rather than as unanswered`() {
    val omitted = answered("q1", "What is the MVP?", "A menu planner").asCompacted()

    assertEquals(
      "Q: What is the MVP? / A: ${PlanningContext.OmittedNote}",
      PlanningContext.line(omitted),
    )
  }

  @Test
  fun `a transcript is one line per question in the order given`() {
    val transcript = PlanningContext.render(
      listOf(question("q1", "First?"), answered("q2", "Second?", "Yes")),
    )

    assertEquals(
      listOf(
        "Q: First? / ${PlanningContext.NotAnsweredNote}",
        "Q: Second? / A: Yes",
      ),
      transcript.split("\n"),
    )
  }

  @Test
  fun `an empty transcript renders as nothing`() {
    assertEquals("", PlanningContext.render(emptyList()))
  }

  // ---------- estimating the cost ----------

  @Test
  fun `an estimate is the character count at four chars per token rounded up`() {
    assertEquals(0, PlanningContext.estimateTokens(""))
    assertEquals(1, PlanningContext.estimateTokens("abcd"))
    assertEquals(2, PlanningContext.estimateTokens("abcde"))
    assertEquals(25, PlanningContext.estimateTokens("x".repeat(100)))
  }

  @Test
  fun `estimating a transcript measures what is actually rendered`() {
    val questions = listOf(question("q1", "x".repeat(40)))

    assertEquals(
      PlanningContext.estimateTokens(PlanningContext.render(questions)),
      PlanningContext.estimateTokens(questions),
    )
  }

  /**
   * The budget is [PlanningContext.TranscriptSharePercent] of the model's own
   * window, and the rest is the prompt around the transcript plus the room the
   * model needs to answer. Nothing to configure: the window is the model's and
   * the share is a constant, so the same project is measured the same way
   * against a 4k edge model and a 200k hosted one.
   */
  @Test
  fun `the budget is the transcript's share of the window`() {
    assertEquals(921, PlanningContext.budgetTokens(contextWindowTokens = 1024))
    assertEquals(3686, PlanningContext.budgetTokens(contextWindowTokens = 4096))
    assertEquals(7372, PlanningContext.budgetTokens(contextWindowTokens = 8192))
    assertEquals(73728, PlanningContext.budgetTokens(contextWindowTokens = 81920))
  }

  /**
   * A window small enough that even the reserved share rounds to nothing, and a
   * nonsense one: neither may produce a negative budget, which would read as
   * "over budget by an unknown amount" and compact every answer in every round.
   */
  @Test
  fun `the budget never goes negative`() {
    assertEquals(0, PlanningContext.budgetTokens(contextWindowTokens = 0))
    assertEquals(0, PlanningContext.budgetTokens(contextWindowTokens = -1))
  }

  // ---------- the near-limit question ----------

  /**
   * One threshold, the budget itself: a transcript comfortably inside it has
   * nothing to decide, and one past it has to be asked about.
   */
  @Test
  fun `the question is asked once the transcript is over the budget`() {
    assertFalse(PlanningContext.nearLimit(estimatedTokens = 1999, budgetTokens = 2000))
    assertFalse(PlanningContext.nearLimit(estimatedTokens = 2000, budgetTokens = 2000))
    assertTrue(PlanningContext.nearLimit(estimatedTokens = 2001, budgetTokens = 2000))
  }


  // ---------- the phases a summary could be written from ----------

  /**
   * Whole past phases, oldest first: the unit a summary is written from is the
   * unit of work the project actually finished, and the one it can least afford
   * to keep re-reading is the one it started with. (The fixture leaves the
   * *last* phase given in progress, so Design is the live one here.)
   */
  @Test
  fun `summarizable phases are the past ones oldest first`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "yes"),
      BuiltInPhase.Research to answered("q2", "Second?", "yes"),
      BuiltInPhase.Design to answered("q3", "Third?", "yes"),
    )

    val phases = PlanningContext.summarizablePhases(project)

    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research),
      phases.map { it.phase },
      "the phase in progress is not a candidate — it is the one being asked about",
    )
    assertEquals(listOf("q1", "q2"), phases.flatMap { it.questions }.map { it.id })
    assertTrue(phases.all { it.estimatedTokens > 0 })
  }

  @Test
  fun `a phase with nothing to summarize is not offered`() {
    val project = project(
      BuiltInPhase.ScopeGoals to question("q1", "Ignored?", ignoredAt = defaultTestInstant),
      BuiltInPhase.Research to question("q2", "Never answered?"),
      BuiltInPhase.Design to answered("q3", "Live?", "yes"),
    )

    assertEquals(emptyList(), PlanningContext.summarizablePhases(project))
  }

  @Test
  fun `a past phase's transcript is its own answered questions rendered`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "MVP?", "a menu planner"),
      BuiltInPhase.ScopeGoals to answered("q2", "Budget?", "shoestring"),
      BuiltInPhase.Design to question("live", "And now?"),
    )

    val unit = PlanningContext.summarizablePhases(project).single()

    assertEquals(BuiltInPhase.ScopeGoals, unit.phase)
    assertEquals(
      "Q: MVP? / A: a menu planner\nQ: Budget? / A: shoestring",
      unit.transcript,
    )
    assertEquals(setOf("q1", "q2"), unit.coveredQuestionIds)
  }

  // ---------- rendering with summaries ----------

  /**
   * The summary rides ahead of the transcript and the questions it stands in
   * for stay in it, so a model reads what the phase settled *and* still knows
   * what it was asked.
   */
  @Test
  fun `a summary leads the transcript and marks the answers it replaces`() {
    val summary = PlanningContext.PhaseSummary(
      phase = BuiltInPhase.ScopeGoals,
      summary = "A menu planner for home cooks.",
      coversQuestionIds = setOf("q1"),
    )

    val transcript = PlanningContext.render(
      listOf(answered("q1", "MVP?", "a menu planner"), question("q2", "And now?")),
      listOf(summary),
    )

    assertEquals(
      listOf(
        PlanningContext.SummariesHeading,
        "Scope & Goals: A menu planner for home cooks.",
        "",
        "Q: MVP? / A: ${PlanningContext.SummarizedNote}",
        "Q: And now? / ${PlanningContext.NotAnsweredNote}",
      ),
      transcript.split("\n"),
    )
  }

  @Test
  fun `a transcript with no summaries renders exactly as it always did`() {
    val questions = listOf(question("q1", "First?"), answered("q2", "Second?", "Yes"))

    assertEquals(PlanningContext.render(questions), PlanningContext.render(questions, emptyList()))
  }

  @Test
  fun `a blank summary is left out rather than filed as an empty section`() {
    val questions = listOf(question("q1", "First?"))
    val summary = PlanningContext.PhaseSummary(
      phase = BuiltInPhase.ScopeGoals,
      summary = "   ",
      coversQuestionIds = setOf("q1"),
    )

    assertEquals(
      "Q: First? / ${PlanningContext.NotAnsweredNote}",
      PlanningContext.render(questions, listOf(summary)),
    )
  }

  /**
   * A summary is only worth its tokens if it costs less than the answers it
   * replaces — and the estimate has to see it, or the loop that decides how many
   * phases to summarize would count a saving it hasn't made yet.
   */
  @Test
  fun `estimating counts the summaries as well as the transcript`() {
    val questions = listOf(answered("q1", "MVP?", "word ".repeat(80)))
    val summary = PlanningContext.PhaseSummary(
      phase = BuiltInPhase.ScopeGoals,
      summary = "a menu planner",
      coversQuestionIds = setOf("q1"),
    )

    assertTrue(PlanningContext.estimateTokens(questions, listOf(summary)) > 0)
    assertEquals(
      PlanningContext.estimateTokens(PlanningContext.render(questions, listOf(summary))),
      PlanningContext.estimateTokens(questions, listOf(summary)),
    )
  }

  // ---------- trimming to the budget ----------

  @Test
  fun `a project inside the budget is handed over untouched`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "What is the MVP?", "A menu planner"),
    )

    val trim = PlanningContext.trim(project, 2000)

    assertEquals(project.questions, trim.questions)
    assertEquals(0, trim.droppedAnswers, "nothing was dropped, so nothing to report")
  }

  @Test
  fun `an over-budget project drops answers and never question text`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "word ".repeat(80)),
      BuiltInPhase.ScopeGoals to answered("q2", "Second?", "word ".repeat(80)),
      BuiltInPhase.Research to question("q3", "And now?"),
    )

    val trimmed = PlanningContext.trim(project, 100).questions

    assertEquals(listOf("q1", "q2", "q3"), trimmed.map { it.id })
    assertTrue(trimmed.none { it.isAnswered }, "the answers are what gets dropped")
    assertEquals(project.questions.map { it.text }, trimmed.map { it.text })
    assertTrue(
      PlanningContext.estimateTokens(trimmed) <= 100,
      "the transcript comes back inside the budget",
    )
  }

  /**
   * Earliest phase first: a project works its way forward, so the phase it
   * started in is the one a model can least afford to keep re-reading.
   */
  @Test
  fun `trimming drops the earliest phase's answers before the later ones`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("early", "Early?", "scope. ".repeat(60)),
      BuiltInPhase.Research to answered("late", "Late?", "research. ".repeat(60)),
      BuiltInPhase.Design to question("live", "And now?"),
    )

    val trimmed = PlanningContext.trim(project, 200).questions

    assertEquals(listOf("late"), trimmed.answeredIds())
    assertTrue(
      trimmed.none { it.id == "early" && it.isAnswered },
      "the earliest phase loses its answer first",
    )
  }

  @Test
  fun `trimming works within a phase oldest question first`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("older", "Older?", "first. ".repeat(60), at = 0),
      BuiltInPhase.ScopeGoals to answered("newer", "Newer?", "second. ".repeat(60), at = 1),
      BuiltInPhase.Research to question("live", "And now?"),
    )

    val trimmed = PlanningContext.trim(project, 200).questions

    assertEquals(listOf("newer"), trimmed.answeredIds())
    assertTrue(
      trimmed.none { it.id == "older" && it.isAnswered },
      "within a phase, the oldest answer goes first",
    )
  }

  /**
   * The current phase is the one a round is being asked about, so its answers
   * are the last thing to go — and the only thing, when the live phase is what
   * blows the budget.
   */
  @Test
  fun `the current phase's answers are never trimmed`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("earlier", "Earlier?", "gone. ".repeat(60)),
      BuiltInPhase.Design to answered("live", "Live?", "the live one. ".repeat(80)),
    )

    val trimmed = PlanningContext.trim(project, 100).questions

    assertEquals(listOf("live"), trimmed.answeredIds())
  }

  /** A trim only changes what is rendered: the question's history is untouched. */
  @Test
  fun `a trimmed question keeps its answer history but not its current answer`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "words. ".repeat(80)),
      BuiltInPhase.Research to question("live", "And now?"),
    )

    val trimmed = PlanningContext.trim(project, 50).questions.first()

    assertFalse(trimmed.isAnswered)
    assertEquals(project.questions.first().answers, trimmed.answers)
  }

  /**
   * [PlanningContext.trim] reports what it gave up so a caller can say so (the
   * generation task logs it) — the count is what the log line is built from.
   */
  @Test
  fun `trimming reports how many answers it dropped`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "one. ".repeat(60), at = 0),
      BuiltInPhase.ScopeGoals to answered("q2", "Second?", "two. ".repeat(60), at = 1),
      BuiltInPhase.ScopeGoals to answered("q3", "Third?", "three. ".repeat(60), at = 2),
      BuiltInPhase.Research to question("live", "And now?"),
    )

    val trim = PlanningContext.trim(project, 200)

    assertEquals(2, trim.droppedAnswers, "the two oldest answers go; the last still fits")
    assertEquals(listOf("q3"), trim.questions.answeredIds())
  }

  /**
   * A dropped answer is marked, not blanked: the transcript has to be able to
   * tell the model that the question was answered and merely left out, so a
   * trimmed question is never indistinguishable from an open one.
   */
  @Test
  fun `trimming marks what it omitted rather than blanking it`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "words. ".repeat(80)),
      BuiltInPhase.Research to question("live", "And now?"),
    )

    val trimmed = PlanningContext.trim(project, 50).questions

    val omitted = trimmed.first()
    assertTrue(omitted.compacted, "the dropped answer is marked as omitted")
    assertEquals("Q: First? / A: ${PlanningContext.OmittedNote}", PlanningContext.line(omitted))
    assertEquals(
      "Q: And now? / ${PlanningContext.NotAnsweredNote}",
      PlanningContext.line(trimmed.last()),
      "a genuinely open question still reads as open — the marker is what tells the two apart",
    )
  }

  /**
   * An ignored question renders as one word whatever it holds, so its answer was
   * never costing the transcript anything — compacting it would report a loss
   * the model never saw.
   */
  @Test
  fun `trimming leaves an ignored question's answer alone`() {
    val project = project(
      BuiltInPhase.ScopeGoals to question(
        "skipped",
        "Ignored?",
        ignoredAt = defaultTestInstant,
        answers = listOf(answer("skipped", "ignored. ".repeat(60), id = "a1")),
      ),
      BuiltInPhase.Research to question("live", "And now?"),
    )

    val trim = PlanningContext.trim(project, 50)

    assertEquals(0, trim.droppedAnswers, "a skipped answer is already not rendered")
    assertEquals("Q: Ignored? / ${PlanningContext.SkippedNote}", PlanningContext.line(trim.questions.first()))
  }

  // ---------- trimming a transcript that already has summaries ----------

  /**
   * A summarized phase's answers are already gone, so the trim must not count
   * dropping them a second time — both because the count is what the task's log
   * line reports, and because a "dropped" question the model can already read
   * through its summary is a phantom loss.
   */
  @Test
  fun `a summarized phase's answers are not dropped again`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "summarized. ".repeat(20)),
      BuiltInPhase.Research to answered("q2", "Second?", "kept. ".repeat(20)),
      BuiltInPhase.Design to question("live", "And now?"),
    )
    val summary = PlanningContext.PhaseSummary(
      phase = BuiltInPhase.ScopeGoals,
      summary = "the first phase settled on a menu planner",
      coversQuestionIds = setOf("q1"),
    )

    val trim = PlanningContext.trim(project, 2000, listOf(summary))

    assertEquals(0, trim.droppedAnswers, "the phase the summary covers is untouched")
    assertEquals(listOf(summary), trim.summaries, "what was already compacted is carried through")
  }

  /**
   * The summaries are part of what gets sent, so a budget that only fits once
   * they are counted still has to be enforced against them.
   */
  @Test
  fun `summaries count against the budget when trimming`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "words. ".repeat(20)),
      BuiltInPhase.Research to answered("q2", "Second?", "words. ".repeat(20)),
      BuiltInPhase.Design to question("live", "And now?"),
    )
    val summary = PlanningContext.PhaseSummary(
      phase = BuiltInPhase.ScopeGoals,
      summary = "a summary that is itself a good deal of text. ".repeat(10),
      coversQuestionIds = setOf("q1"),
    )

    val trim = PlanningContext.trim(project, 300, listOf(summary))

    assertTrue(
      PlanningContext.estimateTokens(trim.questions, trim.summaries) <= 300,
      "the summary and the transcript together come back inside the budget",
    )
  }

  // ---------- fixtures ----------

  private fun List<Question>.answeredIds(): List<String> =
    filter { it.isAnswered }.map { it.id }

  private fun answered(
    id: String,
    text: String,
    answerText: String,
    at: Long = 0,
  ): Question = question(
    id,
    text = text,
    answers = listOf(answer(id, answerText, id = "a-$id")),
    timestamp = defaultTestInstant + at.seconds,
  )

  /**
   * A project whose questions are filed into one round per phase, in the order
   * given, with the last phase's round the one still in progress — so a
   * question's phase reads back through its round the way it does in storage.
   */
  private fun project(vararg entries: Pair<Phase, Question>): Project {
    val phases = entries.map { it.first }.distinct()
    val rounds: List<Round> = phases.mapIndexed { index, phase ->
      round(id = "r-${phase.key}", phase = phase, roundNumber = index + 1)
    }
    return Project(
      id = "p1",
      synopsis = "A synopsis",
      editableTitle = "A title",
      status = "Draft",
      questions = entries.map { (phase, q) -> q.copy(roundId = "r-${phase.key}") },
      rounds = rounds.mapIndexed { index, r ->
        if (index == rounds.lastIndex) r else r.complete(defaultTestInstant)
      },
      createdAt = defaultTestInstant,
      updatedAt = defaultTestInstant,
    )
  }
}
