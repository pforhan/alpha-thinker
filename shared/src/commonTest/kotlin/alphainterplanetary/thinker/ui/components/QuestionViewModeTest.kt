package alphainterplanetary.thinker.ui.components

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
}