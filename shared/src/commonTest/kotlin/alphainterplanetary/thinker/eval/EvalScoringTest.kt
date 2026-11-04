package alphainterplanetary.thinker.eval

import alphainterplanetary.thinker.eval.EvalFixtures.Interaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvalScoringTest {
  @Test
  fun `a short plain title is ok and clean`() {
    val score = EvalScoring.score(Interaction.Title, "Water Log")

    assertTrue(score.ok)
    assertTrue(score.clean)
    assertEquals(null, score.error)
    assertEquals(2, score.wordCount)
  }

  @Test
  fun `a title with quotes is usable but not clean`() {
    val score = EvalScoring.score(Interaction.Title, "\"Water Log\"")

    assertTrue(score.ok)
    assertFalse(score.clean)
    assertTrue(score.error.orEmpty().contains("wrapped in quotes"))
  }

  @Test
  fun `an empty title is not usable`() {
    val score = EvalScoring.score(Interaction.Title, "  ")

    assertFalse(score.ok)
    assertEquals("empty title", score.error)
  }

  @Test
  fun `a question array must be an array of non-empty strings`() {
    val score = EvalScoring.score(
      Interaction.InitialQuestions,
      """["Who is it for?","What is the MVP?"]""",
    )

    assertTrue(score.ok)
    assertTrue(score.clean)
    assertEquals(true, score.jsonOk)
    assertEquals(true, score.arrayStrings)
    assertEquals(null, score.error)
  }

  @Test
  fun `prose around a question array is still read`() {
    val score = EvalScoring.score(
      Interaction.InitialQuestions,
      """Here you go:
        |["Who is it for?","What is the MVP?"]
        |Hope that helps.""".trimMargin(),
    )

    assertTrue(score.ok)
    assertTrue(score.clean)
  }

  @Test
  fun `two arrays in one reply fail rather than half-parsing`() {
    // The Python harness treats first-`[`..last-`]` as one candidate array, so
    // this is a parse failure there and has to be one here too.
    val score = EvalScoring.score(
      Interaction.InitialQuestions,
      """["Who is it for?"] and ["What is the MVP?"]""",
    )

    assertFalse(score.ok)
    assertEquals(false, score.jsonOk)
    assertTrue(score.error.orEmpty().startsWith("json parse failed"))
  }

  @Test
  fun `a JSON array with an empty string fails the string check`() {
    val score = EvalScoring.score(Interaction.InitialQuestions, """["Who?", "  "]""")

    assertFalse(score.ok)
    assertEquals(true, score.jsonOk)
    assertEquals(false, score.arrayStrings)
    assertEquals("not array of non-empty strings", score.error)
  }

  @Test
  fun `a non-JSON reply fails JSON parsing`() {
    val score = EvalScoring.score(Interaction.InitialQuestions, "Who is it for?")

    assertFalse(score.ok)
    assertEquals(false, score.jsonOk)
    assertTrue(score.error.orEmpty().startsWith("json parse failed"))
  }

  @Test
  fun `a summary with bullets is usable but not clean`() {
    val score = EvalScoring.score(
      Interaction.Summarize,
      "Here is the summary:\n- scope\n- goals",
    )

    assertTrue(score.ok)
    assertFalse(score.clean)
    assertNotNull(score.error)
    assertTrue(score.error.contains("bullets/headings"))
    assertTrue(score.error.contains("preamble"))
  }

  @Test
  fun `an overlong summary is not clean`() {
    val text = (1..151).joinToString(" ") { "word" }

    val score = EvalScoring.score(Interaction.Summarize, text)

    assertTrue(score.ok)
    assertFalse(score.clean)
    assertTrue(score.error.orEmpty().contains(">150 words"))
  }

  @Test
  fun `the suite runs every interaction over every fixture once`() {
    assertEquals(9, EvalFixtures.groups.size)
    assertEquals(
      EvalFixtures.groups.size,
      EvalFixtures.groups.distinctBy { it.first.wire to it.second.name }.size,
    )
  }
}
