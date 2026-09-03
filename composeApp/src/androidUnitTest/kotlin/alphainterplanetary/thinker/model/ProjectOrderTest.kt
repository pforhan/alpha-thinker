package alphainterplanetary.thinker.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOrderTest {

  private fun question(id: String, answered: Boolean = false) = Question(
    id = id,
    text = id,
    timestamp = Instant.fromEpochMilliseconds(0),
    contextId = "",
    answers = if (answered) listOf(Answer(1, id, "a", Instant.fromEpochMilliseconds(1), null, null)) else emptyList()
  )

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
      question("x", answered = true),
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
      question("a"), question("b", answered = true), question("c")
    )
    val after = p.moveToEnd("a")
    assertEquals(setOf("a", "b", "c"), after.questions.map { it.id }.toSet())
    assertTrue(after.questions.first { it.id == "b" }.isAnswered)
  }
}
