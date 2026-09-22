package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.question
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val FOLLOW_UP_COUNT = 3

class HardcodedPlanningEngineTest {

  private val generator =
    HardcodedPlanningEngine(initialCount = 3, followUpCount = FOLLOW_UP_COUNT)

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
    val generator = HardcodedPlanningEngine(initialCount = 5, followUpCount = FOLLOW_UP_COUNT)

    val batch = generator.generateInitialQuestions(
      "title",
      "synopsis",
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(5, batch.questions.size)
    assertEquals(setOf("ctx"), batch.questions.map { it.roundId }.toSet())
    assertFalse(batch.done)
  }

  @Test
  fun `generateInitialQuestions draws from the start of the phase's pool`() = runTest {
    val batch = generator.generateInitialQuestions(
      "title",
      "synopsis",
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(3, batch.questions.size)
    assertEquals(
      poolOf(BuiltInPhase.ScopeGoals).take(3),
      batch.questions.map { it.text },
    )
  }

  @Test
  fun `generateInitialQuestions draws from its own phase's pool`() = runTest {
    val scope =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    val research =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.Research, activityId = "test-activity")

    assertTrue(scope.questions.map { it.text }.all { it in poolOf(BuiltInPhase.ScopeGoals) })
    assertTrue(research.questions.map { it.text }.all { it in poolOf(BuiltInPhase.Research) })
    assertTrue(
      scope.questions.map { it.text }.none { it in poolOf(BuiltInPhase.Research) },
      "a phase must not draw from another phase's pool",
    )
  }

  @Test
  fun `generateInitialQuestions reports done once the whole pool has been served`() = runTest {
    val generator = HardcodedPlanningEngine(initialCount = 12, followUpCount = FOLLOW_UP_COUNT)

    val batch = generator.generateInitialQuestions(
      "title",
      "synopsis",
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(12, batch.questions.size)
    assertTrue(batch.done)
  }

  // ---------- generateFollowUpQuestions ----------

  @Test
  fun `generateFollowUpQuestions returns questions not already asked`() = runTest {
    val initial =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    val followUp = generator.generateFollowUpQuestions(
      "synopsis",
      initial.questions,
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertTrue(followUp.questions.isNotEmpty())
    assertTrue(followUp.questions.size <= FOLLOW_UP_COUNT)
    val initialTexts = initial.questions.map { it.text }.toSet()
    assertTrue(followUp.questions.map { it.text }.none { it in initialTexts })
  }

  @Test
  fun `generateFollowUpQuestions dedupes across multiple rounds`() = runTest {
    val initial =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    val round1 = generator.generateFollowUpQuestions(
      "synopsis",
      initial.questions,
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )
    val asked = (initial.questions + round1.questions).map { it.text }.toSet()
    val round2 = generator.generateFollowUpQuestions(
      "synopsis",
      initial.questions + round1.questions,
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertTrue(round2.questions.map { it.text }.none { it in asked })
  }

  @Test
  fun `generateFollowUpQuestions returns empty and reports done when the phase's pool is exhausted`() = runTest {
    val pool = poolOf(BuiltInPhase.ValidationPlan)
    val asked = pool.mapIndexed { index, text -> question(id = "q$index", text = text) }

    val followUp = generator.generateFollowUpQuestions(
      "synopsis",
      asked,
      "ctx",
      BuiltInPhase.ValidationPlan,
      activityId = "test-activity",
    )

    assertTrue(followUp.questions.isEmpty())
    assertTrue(followUp.done)
  }

  @Test
  fun `generateFollowUpQuestions reports done when the last of the remaining pool is served`() = runTest {
    val generator = HardcodedPlanningEngine(initialCount = 3, followUpCount = 9)
    val initial =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")

    val followUp = generator.generateFollowUpQuestions(
      "synopsis",
      initial.questions,
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(9, followUp.questions.size)
    assertTrue(followUp.done)
  }

  @Test
  fun `generateFollowUpQuestions respects followUpCount`() = runTest {
    val generator = HardcodedPlanningEngine(initialCount = 3, followUpCount = 4)

    val initial =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    val followUp = generator.generateFollowUpQuestions(
      "synopsis",
      initial.questions,
      "ctx",
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(4, followUp.questions.size)
  }

  @Test
  fun `generation is stateless - same inputs yield same texts`() = runTest {
    val first =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")
    val second =
      generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")

    assertEquals(first.questions.map { it.text }, second.questions.map { it.text })
  }

  // ---------- canProduceMoreInPhase ----------

  @Test
  fun `canProduceMoreInPhase is true before anything has been asked`() = runTest {
    assertTrue(
      generator.canProduceMoreInPhase("synopsis", emptyList(), BuiltInPhase.ScopeGoals, activityId = "test-activity"),
    )
  }

  @Test
  fun `canProduceMoreInPhase counts only the phase's own pool texts not yet asked`() = runTest {
    val initial = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.ScopeGoals, activityId = "test-activity")

    val canProduceMore = generator.canProduceMoreInPhase(
      "synopsis",
      initial.questions,
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(poolOf(BuiltInPhase.ScopeGoals).size > initial.questions.size, canProduceMore)
  }

  @Test
  fun `canProduceMoreInPhase ignores questions asked in other phases`() = runTest {
    val research = generator.generateInitialQuestions("title", "synopsis", "ctx", BuiltInPhase.Research, activityId = "test-activity")

    val scopeCanProduceMore = generator.canProduceMoreInPhase(
      "synopsis",
      research.questions,
      BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertTrue(scopeCanProduceMore)
  }

  @Test
  fun `canProduceMoreInPhase is false once the phase pool is exhausted`() = runTest {
    val asked = poolOf(BuiltInPhase.ValidationPlan)
      .mapIndexed { index, text -> question(id = "q$index", text = text) }

    assertFalse(
      generator.canProduceMoreInPhase("synopsis", asked, BuiltInPhase.ValidationPlan, activityId = "test-activity"),
    )
  }

  // ---------- pool partition ----------

  @Test
  fun `every built-in phase has a non-empty pool`() {
    assertTrue(BuiltInPhase.entries.all { phase ->
      HardcodedPlanningEngine.questionPoolByPhase.containsKey(
        phase
      )
    })
    assertTrue(
      BuiltInPhase.entries.all { phase -> !poolOf(phase).isEmpty() },
      "each phase needs enough questions to serve an initial round",
    )
  }

  @Test
  fun `pool texts are unique across phases`() {
    val flattened = HardcodedPlanningEngine.questionPoolByPhase.values.flatten()

    assertEquals(flattened.size, flattened.toSet().size)
  }

  private fun poolOf(phase: BuiltInPhase): List<String> =
    requireNotNull(HardcodedPlanningEngine.questionPoolByPhase[phase]) {
      "no pool for $phase"
    }
}