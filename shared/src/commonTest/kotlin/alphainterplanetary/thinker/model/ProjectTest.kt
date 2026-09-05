package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.testutil.answeredQuestion
import alphainterplanetary.thinker.testutil.draftQuestion
import alphainterplanetary.thinker.testutil.ignoredQuestion
import alphainterplanetary.thinker.testutil.question
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTest {

  private fun project(vararg qs: Question) = Project(
    id = "p",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = qs.toList(),
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0)
  )

  private fun firstThree(p: Project): List<String> =
    p.unansweredQuestions.map { it.id }.take(3)

  @Test
  fun `moveToEnd pushes the question to the back of the list`() {
    val p = project(question("a"), question("b"), question("c"), question("d"))
    assertEquals(listOf("a", "b", "c", "d"), p.questionOrderIds)
    assertEquals(listOf("a", "c", "d", "b"), p.moveToEnd("b").questionOrderIds)
  }

  @Test
  fun `askLater on the first visible question rotates the deck, pulling in the next one`() {
    val p = project(question("a"), question("b"), question("c"), question("d"))
    assertEquals(listOf("a", "b", "c"), firstThree(p))

    val after = p.moveToEnd("a")
    // a moved to the back, so b/c/d now lead the order
    assertEquals(listOf("b", "c", "d", "a"), after.questionOrderIds)
    // and the visible three now pull in d
    assertEquals(listOf("b", "c", "d"), firstThree(after))
  }

  @Test
  fun `shuffle rotates the current visible three to the back`() {
    val p = project(
      question("a"), question("b"), question("c"),
      question("d"), question("e")
    )
    assertEquals(listOf("a", "b", "c"), firstThree(p))

    val after = p.rotateToEnd(listOf("a", "b", "c"))
    assertEquals(listOf("d", "e", "a", "b", "c"), after.questionOrderIds)
    assertEquals(listOf("d", "e", "a"), firstThree(after))
  }

  @Test
  fun `askLater on last question is a no-op`() {
    val p = project(question("a"), question("b"), question("c"))
    val moved = p.moveToEnd("c")
    assertEquals(listOf("a", "b", "c"), moved.questionOrderIds)
  }

  @Test
  fun `unanswered order is preserved across reorders and ignores settled questions`() {
    val p = project(
      answeredQuestion("x"),
      question("a"), question("b"), question("c"), question("d")
    )
    // answered question x is not part of the unanswered deck
    assertEquals(listOf("a", "b", "c"), firstThree(p))

    // a is moved to the end of the full list, so b/c/d now lead the deck
    val after = p.moveToEnd("a")
    assertEquals(listOf("b", "c", "d"), firstThree(after))
  }

  @Test
  fun `moveToEnd preserves the full question set and their payloads`() {
    val p = project(
      question("a"), answeredQuestion("b"), question("c")
    )
    val after = p.moveToEnd("a")
    assertEquals(setOf("a", "b", "c"), after.questions.map { it.id }.toSet())
    assertTrue(after.questions.first { it.id == "b" }.isAnswered)
  }

  @Test
  fun `unansweredQuestions excludes answered and ignored questions`() {
    val p = project(answeredQuestion("a"), ignoredQuestion("b"), question("c"), draftQuestion("d"))

    assertEquals(listOf("c", "d"), p.unansweredQuestions.map { it.id })
  }

  @Test
  fun `activeQuestions excludes only ignored questions`() {
    val p = project(answeredQuestion("a"), ignoredQuestion("b"), question("c"))

    assertEquals(listOf("a", "c"), p.activeQuestions.map { it.id })
  }

  @Test
  fun `allActiveQuestionsAnswered is true when every active question is answered`() {
    val p = project(answeredQuestion("a"), answeredQuestion("b"), ignoredQuestion("c"))

    assertTrue(p.allActiveQuestionsAnswered)
  }

  @Test
  fun `allActiveQuestionsAnswered is false when an active question is unanswered`() {
    val p = project(answeredQuestion("a"), question("b"))

    assertFalse(p.allActiveQuestionsAnswered)
  }

  @Test
  fun `allActiveQuestionsAnswered is false when an active question only has a draft`() {
    val p = project(answeredQuestion("a"), draftQuestion("b"))

    assertFalse(p.allActiveQuestionsAnswered)
  }

  @Test
  fun `allActiveQuestionsAnswered is false when there are no active questions`() {
    val p = project(ignoredQuestion("a"), ignoredQuestion("b"))

    assertFalse(p.allActiveQuestionsAnswered)
  }
}