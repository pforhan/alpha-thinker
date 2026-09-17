package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class QuestionViewModeTest {

  private val latest: Instant = Instant.fromEpochMilliseconds(3000)
  private val middle: Instant = Instant.fromEpochMilliseconds(2000)
  private val earliest: Instant = Instant.fromEpochMilliseconds(1000)

  private fun ids(view: QuestionViewMode, questions: List<alphainterplanetary.thinker.model.Question>): List<String> =
    view.apply(questions).map { it.id }

  // ---------- filter membership ----------

  @Test
  fun `an unanswered question appears only in the unanswered filter`() {
    val q = question("q1")
    assertEquals(listOf("q1"), ids(QuestionViewMode.Unanswered, listOf(q)))
    assertTrue(QuestionViewMode.Answered.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Draft.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Ignored.apply(listOf(q)).isEmpty())
  }

  @Test
  fun `an answered question belongs to the answered filter and drops out of unanswered`() {
    val q = question("q1", answers = listOf(answer("q1", "A", id = "a1")))
    assertEquals(listOf("q1"), ids(QuestionViewMode.Answered, listOf(q)))
    assertTrue(QuestionViewMode.Unanswered.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Draft.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Ignored.apply(listOf(q)).isEmpty())
  }

  @Test
  fun `a draft belongs to the drafts filter and the unanswered filter but never answered or ignored`() {
    val q = question("q1", draftText = "draft", draftUpdatedAt = earliest)
    assertEquals(listOf("q1"), ids(QuestionViewMode.Draft, listOf(q)))
    assertEquals(listOf("q1"), ids(QuestionViewMode.Unanswered, listOf(q)))
    assertTrue(QuestionViewMode.Answered.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Ignored.apply(listOf(q)).isEmpty())
  }

  @Test
  fun `an ignored question without an answer belongs only to the ignored filter`() {
    val q = question("q1", ignoredAt = earliest)
    assertEquals(listOf("q1"), ids(QuestionViewMode.Ignored, listOf(q)))
    assertTrue(QuestionViewMode.Unanswered.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Answered.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Draft.apply(listOf(q)).isEmpty())
  }

  @Test
  fun `an ignored question with an answer belongs only to the ignored filter`() {
    val q = question("q1", ignoredAt = earliest, answers = listOf(answer("q1", "A", id = "a1")))
    assertEquals(listOf("q1"), ids(QuestionViewMode.Ignored, listOf(q)))
    assertTrue(QuestionViewMode.Answered.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Draft.apply(listOf(q)).isEmpty())
  }

  @Test
  fun `an ignored question with a draft belongs only to the ignored filter`() {
    val q = question("q1", ignoredAt = earliest, draftText = "draft", draftUpdatedAt = earliest)
    assertEquals(listOf("q1"), ids(QuestionViewMode.Ignored, listOf(q)))
    assertTrue(QuestionViewMode.Draft.apply(listOf(q)).isEmpty())
    assertTrue(QuestionViewMode.Answered.apply(listOf(q)).isEmpty())
  }

  // ---------- moving answers between filters ----------

  @Test
  fun `committing a draft moves the question from drafts to answered`() {
    val draft = question("q1", draftText = "draft", draftUpdatedAt = earliest)
    val completed = question("q1", answers = listOf(answer("q1", "draft", id = "a1", createdAt = latest)))
    assertEquals(listOf("q1"), ids(QuestionViewMode.Draft, listOf(draft)))
    assertTrue(QuestionViewMode.Answered.apply(listOf(draft)).isEmpty())
    assertTrue(QuestionViewMode.Draft.apply(listOf(completed)).isEmpty())
    assertEquals(listOf("q1"), ids(QuestionViewMode.Answered, listOf(completed)))
  }

  @Test
  fun `ignoring an answered question moves it out of answered`() {
    val answered = question("q1", answers = listOf(answer("q1", "A", id = "a1")))
    val ignored = question("q1", ignoredAt = latest, answers = listOf(answer("q1", "A", id = "a1")))
    assertEquals(listOf("q1"), ids(QuestionViewMode.Answered, listOf(answered)))
    assertTrue(QuestionViewMode.Answered.apply(listOf(ignored)).isEmpty())
    assertEquals(listOf("q1"), ids(QuestionViewMode.Ignored, listOf(ignored)))
  }

  @Test
  fun `unignoring a draft moves it from ignored to drafts`() {
    val ignored = question(
      "q1",
      ignoredAt = latest,
      draftText = "draft",
      draftUpdatedAt = earliest,
    )
    val draft = question("q1", draftText = "draft", draftUpdatedAt = earliest)
    assertEquals(listOf("q1"), ids(QuestionViewMode.Ignored, listOf(ignored)))
    assertTrue(QuestionViewMode.Draft.apply(listOf(ignored)).isEmpty())
    assertTrue(QuestionViewMode.Ignored.apply(listOf(draft)).isEmpty())
    assertEquals(listOf("q1"), ids(QuestionViewMode.Draft, listOf(draft)))
  }

  @Test
  fun `clearing a committed answer returns the question to unanswered`() {
    val answered = question("q1", answers = listOf(answer("q1", "A", id = "a1")))
    val cleared = question("q1")
    assertTrue(QuestionViewMode.Unanswered.apply(listOf(answered)).isEmpty())
    assertEquals(listOf("q1"), ids(QuestionViewMode.Unanswered, listOf(cleared)))
    assertTrue(QuestionViewMode.Answered.apply(listOf(cleared)).isEmpty())
  }

  // ---------- sort order ----------

  @Test
  fun `answered questions sort by answer date descending`() {
    val questions = listOf(
      question("old", answers = listOf(answer("old", "A", id = "a1", createdAt = earliest))),
      question("new", answers = listOf(answer("new", "A", id = "a2", createdAt = latest))),
      question("mid", answers = listOf(answer("mid", "A", id = "a3", createdAt = middle))),
    )
    assertEquals(listOf("new", "mid", "old"), ids(QuestionViewMode.Answered, questions))
  }

  @Test
  fun `drafts sort by last edit date descending`() {
    val questions = listOf(
      question("old", draftText = "d", draftUpdatedAt = earliest),
      question("new", draftText = "d", draftUpdatedAt = latest),
      question("mid", draftText = "d", draftUpdatedAt = middle),
    )
    assertEquals(listOf("new", "mid", "old"), ids(QuestionViewMode.Draft, questions))
  }

  @Test
  fun `ignored questions sort by ignored date descending`() {
    val questions = listOf(
      question("old", ignoredAt = earliest),
      question("new", ignoredAt = latest),
      question("mid", ignoredAt = middle),
    )
    assertEquals(listOf("new", "mid", "old"), ids(QuestionViewMode.Ignored, questions))
  }

  // ---------- recommendations ----------

  @Test
  fun `recommended views exclude the current view and prefer unanswered`() {
    val questions = listOf(
      question("answered", answers = listOf(answer("answered", "A", id = "a1"))),
      question("unanswered"),
    )
    val recommended = QuestionViewMode.Answered.recommendedViews(questions)
    assertEquals(QuestionViewMode.Unanswered, recommended.first())
    assertTrue(QuestionViewMode.Answered !in recommended)
  }

  // ---------- phase sections ----------

  private val scopeGoalsSections: (Question) -> Phase = { q ->
    when (q.roundId) {
      "r-research" -> BuiltInPhase.Research
      else -> BuiltInPhase.ScopeGoals
    }
  }

  @Test
  fun `answered sections group by phase - most recently active first`() {
    val questions = listOf(
      question(
        "researchOld",
        roundId = "r-research",
        answers = listOf(answer("researchOld", "A", id = "a1", createdAt = earliest)),
      ),
      question(
        "scopeRecent",
        roundId = "r-scope",
        answers = listOf(answer("scopeRecent", "A", id = "a2", createdAt = latest)),
      ),
      question(
        "researchRecent",
        roundId = "r-research",
        answers = listOf(answer("researchRecent", "A", id = "a3", createdAt = middle)),
      ),
    )

    val sections = QuestionViewMode.Answered.sections(questions, scopeGoalsSections)
    assertEquals(listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research), sections.map { it.phase })
    // each section keeps the answered-by-date ordering
    assertEquals(listOf("scopeRecent"), sections[0].questions.map { it.id })
    assertEquals(listOf("researchRecent", "researchOld"), sections[1].questions.map { it.id })
  }

  @Test
  fun `ignored sections group by phase - most recently ignored first`() {
    val questions = listOf(
      question("researchIgnored", roundId = "r-research", ignoredAt = earliest),
      question("scopeIgnored", roundId = "r-scope", ignoredAt = latest),
      question("scopeIgnored2", roundId = "r-scope", ignoredAt = middle),
    )

    val sections = QuestionViewMode.Ignored.sections(questions, scopeGoalsSections)
    assertEquals(listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research), sections.map { it.phase })
    assertEquals(listOf("scopeIgnored", "scopeIgnored2"), sections[0].questions.map { it.id })
    assertEquals(listOf("researchIgnored"), sections[1].questions.map { it.id })
  }

  @Test
  fun `sections returns empty for views without phase headers`() {
    val q = question("q1")
    assertTrue(QuestionViewMode.Unanswered.sections(listOf(q), scopeGoalsSections).isEmpty())
    assertTrue(QuestionViewMode.Draft.sections(listOf(q), scopeGoalsSections).isEmpty())
  }

  @Test
  fun `resolved count labels match their views`() {
    assertEquals("answered", QuestionViewMode.Answered.resolvedCountLabel)
    assertEquals("ignored", QuestionViewMode.Ignored.resolvedCountLabel)
    assertEquals("", QuestionViewMode.Unanswered.resolvedCountLabel)
    assertEquals("", QuestionViewMode.Draft.resolvedCountLabel)
  }
}