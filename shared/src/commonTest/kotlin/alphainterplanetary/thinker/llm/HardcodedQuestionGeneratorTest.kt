package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.question
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FOLLOW_UP_COUNT = 3

class HardcodedQuestionGeneratorTest {

  private val generator =
    HardcodedQuestionGenerator(initialCount = 3, followUpCount = FOLLOW_UP_COUNT)

  // ---------- recommendTitle ----------

  @Test
  fun `recommendTitle returns empty for empty string`() {
    assertEquals("", title(""))
    assertEquals("", title("   "))
  }

  @Test
  fun `recommendTitle returns full string when no delimiters under 30 chars`() {
    assertEquals("Short synopsis", title("Short synopsis"))
    assertEquals(
      "Exactly 30 characters long!",
      title("Exactly 30 characters long!")
    )
  }

  @Test
  fun `recommendTitle cuts at sentence end before 30 chars`() {
    assertEquals("Short", title("Short. This is longer than 30 chars"))
    assertEquals("Ends with period", title("Ends with period. More text here"))
  }

  @Test
  fun `recommendTitle cuts at newline before 30 chars`() {
    assertEquals("Line one", title("Line one\nLine two continues"))
    assertEquals("First line", title("First line\nSecond line"))
  }

  @Test
  fun `recommendTitle cuts at 30 chars when no sentence end or newline`() {
    assertEquals(
      "This is a very long synopsis w",
      title("This is a very long synopsis without breaks")
    )
  }

  @Test
  fun `recommendTitle sentence end takes priority over 30 chars`() {
    assertEquals("A", title("A. This is way longer than thirty characters"))
  }

  @Test
  fun `recommendTitle newline takes priority over 30 chars`() {
    assertEquals(
      "Short",
      title("Short\nThis is way longer than thirty characters")
    )
  }

  @Test
  fun `recommendTitle sentence end takes priority over newline`() {
    assertEquals("Ends with period", title("Ends with period.\nNew line here"))
  }

  @Test
  fun `recommendTitle trims whitespace from result`() {
    assertEquals("Hello world", title("  Hello world  "))
    assertEquals("Short", title("Short.\n  "))
    assertEquals("Short", title("Short . \n  "))
    assertEquals("Short", title("Short \n  "))
  }

  @Test
  fun `recommendTitle handles multiple sentences - cuts at first`() {
    assertEquals("First", title("First. Second. Third."))
  }

  @Test
  fun `recommendTitle handles multiple newlines - cuts at first`() {
    assertEquals("Line one", title("Line one\nLine two\nLine three"))
  }

  private fun title(synopsis: String): String = generator.generateTitleFromSynopsisForTest(synopsis)

  // ---------- generateInitialQuestions ----------

  @Test
  fun `generateInitialQuestions returns the configured number of questions`() = runTest {
    val generator = HardcodedQuestionGenerator(initialCount = 5, followUpCount = FOLLOW_UP_COUNT)

    val questions = generator.generateInitialQuestions(
      "title",
      "synopsis",
      "ctx",
      BuiltInPhase.ScopeGoals,
    )

    assertEquals(5, questions.size)
    assertEquals(setOf("ctx"), questions.map { it.roundId }.toSet())
  }

  @Test
  fun `generateInitialQuestions draws from the start of the phase's pool`() = runTest {
    val questions = generator.generateInitialQuestions(
      "title",
      "synopsis",
      "ctx",
      BuiltInPhase.ScopeGoals,
    )

    assertEquals(3, questions.size)
    assertEquals(
      poolOf(BuiltInPhase.ScopeGoals).take(3),
      questions.map { it.text },
    )
  }

  @Test
  fun `generateInitialQuestions draws from its own phase's pool`() = runTest {
    val scope = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals)
    val research = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.Research)

    assertTrue(scope.map { it.text }.all { it in poolOf(BuiltInPhase.ScopeGoals) })
    assertTrue(research.map { it.text }.all { it in poolOf(BuiltInPhase.Research) })
    assertTrue(
      scope.map { it.text }.none { it in poolOf(BuiltInPhase.Research) },
      "a phase must not draw from another phase's pool",
    )
  }

  // ---------- generateFollowUpQuestions ----------

  @Test
  fun `generateFollowUpQuestions returns questions not already asked`() = runTest {
    val initial = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals)
    val followUp = generator.generateFollowUpQuestions(
      "synopsis",
      initial,
      "ctx",
      BuiltInPhase.ScopeGoals,
    )

    assertTrue(followUp.isNotEmpty())
    assertTrue(followUp.size <= FOLLOW_UP_COUNT)
    val initialTexts = initial.map { it.text }.toSet()
    assertTrue(followUp.map { it.text }.none { it in initialTexts })
  }

  @Test
  fun `generateFollowUpQuestions dedupes across multiple rounds`() = runTest {
    val initial = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals)
    val round1 = generator.generateFollowUpQuestions(
      "synopsis",
      initial,
      "ctx",
      BuiltInPhase.ScopeGoals,
    )
    val asked = (initial + round1).map { it.text }.toSet()
    val round2 = generator.generateFollowUpQuestions(
      "synopsis",
      initial + round1,
      "ctx",
      BuiltInPhase.ScopeGoals,
    )

    assertTrue(round2.map { it.text }.none { it in asked })
  }

  @Test
  fun `generateFollowUpQuestions returns empty when the phase's pool is exhausted`() = runTest {
    val pool = poolOf(BuiltInPhase.ValidationPlan)
    val asked = pool.mapIndexed { index, text -> question(id = "q$index", text = text) }

    val followUp = generator.generateFollowUpQuestions(
      "synopsis",
      asked,
      "ctx",
      BuiltInPhase.ValidationPlan,
    )

    assertTrue(followUp.isEmpty())
  }

  @Test
  fun `generateFollowUpQuestions respects followUpCount`() = runTest {
    val generator = HardcodedQuestionGenerator(initialCount = 3, followUpCount = 7)

    val initial = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals)
    val followUp = generator.generateFollowUpQuestions(
      "synopsis",
      initial,
      "ctx",
      BuiltInPhase.ScopeGoals,
    )

    assertEquals(7, followUp.size)
  }

  @Test
  fun `generation is stateless - same inputs yield same texts`() = runTest {
    val first = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals)
    val second = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals)

    assertEquals(first.map { it.text }, second.map { it.text })
  }

  // ---------- pool partition ----------

  @Test
  fun `every built-in phase has a non-empty pool`() {
    assertTrue(BuiltInPhase.entries.all { phase -> HardcodedQuestionGenerator.questionPoolByPhase.containsKey(phase) })
    assertTrue(
      BuiltInPhase.entries.all { phase -> !poolOf(phase).isEmpty() },
      "each phase needs enough questions to serve an initial round",
    )
  }

  @Test
  fun `pool texts are unique across phases`() {
    val flattened = HardcodedQuestionGenerator.questionPoolByPhase.values.flatten()

    assertEquals(flattened.size, flattened.toSet().size)
  }

  private fun poolOf(phase: BuiltInPhase): List<String> =
    requireNotNull(HardcodedQuestionGenerator.questionPoolByPhase[phase]) {
      "no pool for $phase"
    }
}