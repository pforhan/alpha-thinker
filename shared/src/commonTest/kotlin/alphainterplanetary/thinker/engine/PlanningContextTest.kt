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

  @Test
  fun `the default budget is one of the offered budgets`() {
    assertTrue(PlanningContext.DefaultBudgetTokens in PlanningContext.BudgetOptionsTokens)
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
