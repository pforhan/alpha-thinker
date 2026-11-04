package alphainterplanetary.thinker.eval

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvalRepetitionDetectorTest {
  @Test
  fun `a repeated sentence is caught before the reply gets long`() {
    val detector = EvalRepetitionDetector()

    val looping = "The project needs a clearer answer. ".repeat(200)
    assertTrue(detector.append(looping))
    // Everything that arrived is kept as evidence, not truncated to the match.
    assertTrue(detector.text.endsWith("clearer answer. "))
  }

  @Test
  fun `a repeated line is caught even when the text is not one long unit`() {
    val detector = EvalRepetitionDetector()

    val looping = buildString {
      repeat(12) { appendLine("1. Clarify the project scope with the team.") }
    }
    assertTrue(detector.append(looping))
  }

  @Test
  fun `deltas are checked as they arrive rather than only at the end`() {
    val detector = EvalRepetitionDetector()
    val unit = "and then the model repeats itself. "
    var caughtAt = -1
    var appended = 0

    repeat(200) { index ->
      appended++
      if (detector.append(unit) && caughtAt < 0) caughtAt = index
    }

    assertTrue(caughtAt in 0 until 30, "loop was caught at chunk $caughtAt of 200")
    assertTrue(appended > caughtAt)
  }

  @Test
  fun `an ordinary answer with a repeated phrase is not a loop`() {
    val detector = EvalRepetitionDetector()

    val answer = buildString {
      appendLine("Water Log is a simple product for tracking daily intake.")
      appendLine("Who is it for? People who forget to drink water.")
      appendLine("What is the MVP? A single button and a daily counter.")
      appendLine("How will you measure success? Daily active users.")
      appendLine("The scope is intentionally small, and the name is clear.")
      appendLine("Yes, yes, yes — but only once.")
    }

    assertFalse(detector.append(answer))
  }

  @Test
  fun `a short answer is never flagged no matter how it repeats`() {
    val detector = EvalRepetitionDetector()

    assertFalse(detector.append("yes yes yes yes yes yes"))
  }
}
