package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.question
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val COUNT = 3

class HardcodedPlanningEngineTest {

  private val generator = HardcodedPlanningEngine(count = COUNT)

  /**
   * The declaration the whole context-budget apparatus keys off: the pool
   * serves fixed strings, so this engine composes no prompt and has no window
   * to overrun. Getting it wrong would put a near-limit dialog in front of a
   * Lite user whose generation sends nothing they wrote.
   */
  @Test
  fun `the engine declares that it has no context window`() {
    assertNull(generator.contextWindowTokens)
    assertFalse(generator.canSummarize)
  }

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

  private fun title(synopsis: String): String = generator.generateTitleFromSynopsis(synopsis)

  // ---------- generateQuestions: a project's opening round (nothing asked yet) ----------

  @Test
  fun `generateQuestions returns the configured number of questions`() = runTest {
    val generator = HardcodedPlanningEngine(count = 5)

    val batch = generator.generateQuestions(
      title = "title",
      synopsis = "synopsis",
      previousQuestions = emptyList(),
      roundId = "ctx",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(5, batch.questions.size)
    assertEquals(setOf("ctx"), batch.questions.map { it.roundId }.toSet())
    assertFalse(batch.done)
  }

  @Test
  fun `generateQuestions draws from the start of the phase's pool when nothing is asked`() = runTest {
    val batch = generate(phase = BuiltInPhase.ScopeGoals)

    assertEquals(COUNT, batch.questions.size)
    assertEquals(
      poolOf(BuiltInPhase.ScopeGoals).take(COUNT),
      batch.questions.map { it.text },
    )
  }

  @Test
  fun `generateQuestions draws from its own phase's pool`() = runTest {
    val scope = generate(phase = BuiltInPhase.ScopeGoals)
    val research = generate(phase = BuiltInPhase.Research)

    assertTrue(scope.questions.map { it.text }.all { it in poolOf(BuiltInPhase.ScopeGoals) })
    assertTrue(research.questions.map { it.text }.all { it in poolOf(BuiltInPhase.Research) })
    assertTrue(
      scope.questions.map { it.text }.none { it in poolOf(BuiltInPhase.Research) },
      "a phase must not draw from another phase's pool",
    )
  }

  @Test
  fun `generateQuestions reports done once the whole pool has been served`() = runTest {
    val generator = HardcodedPlanningEngine(count = 12)

    val batch = generator.generateQuestions(
      title = "title",
      synopsis = "synopsis",
      previousQuestions = emptyList(),
      roundId = "ctx",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(12, batch.questions.size)
    assertTrue(batch.done)
  }

  // ---------- generateQuestions: a later round (questions already asked) ----------

  @Test
  fun `generateQuestions returns questions not already asked`() = runTest {
    val opening = generate(phase = BuiltInPhase.ScopeGoals)

    val later = generate(phase = BuiltInPhase.ScopeGoals, previousQuestions = opening.questions)

    assertTrue(later.questions.isNotEmpty())
    assertTrue(later.questions.size <= COUNT)
    val asked = opening.questions.map { it.text }.toSet()
    assertTrue(later.questions.map { it.text }.none { it in asked })
  }

  @Test
  fun `generateQuestions dedupes across multiple rounds`() = runTest {
    val opening = generate(phase = BuiltInPhase.ScopeGoals)
    val round1 = generate(phase = BuiltInPhase.ScopeGoals, previousQuestions = opening.questions)
    val asked = (opening.questions + round1.questions).map { it.text }.toSet()

    val round2 = generate(
      phase = BuiltInPhase.ScopeGoals,
      previousQuestions = opening.questions + round1.questions,
    )

    assertTrue(round2.questions.map { it.text }.none { it in asked })
  }

  @Test
  fun `generateQuestions returns empty and reports done when the phase's pool is exhausted`() = runTest {
    val asked = poolOf(BuiltInPhase.ValidationPlan)
      .mapIndexed { index, text -> question(id = "q$index", text = text) }

    val batch = generate(phase = BuiltInPhase.ValidationPlan, previousQuestions = asked)

    assertTrue(batch.questions.isEmpty())
    assertTrue(batch.done)
  }

  @Test
  fun `generateQuestions reports done when the last of the remaining pool is served`() = runTest {
    val generator = HardcodedPlanningEngine(count = 9)
    val opening = generate(phase = BuiltInPhase.ScopeGoals)

    val batch = generator.generateQuestions(
      title = "title",
      synopsis = "synopsis",
      previousQuestions = opening.questions,
      roundId = "ctx",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(9, batch.questions.size)
    assertTrue(batch.done)
  }

  @Test
  fun `generateQuestions respects the configured count`() = runTest {
    val generator = HardcodedPlanningEngine(count = 4)
    val opening = generate(phase = BuiltInPhase.ScopeGoals)

    val batch = generator.generateQuestions(
      title = "title",
      synopsis = "synopsis",
      previousQuestions = opening.questions,
      roundId = "ctx",
      phase = BuiltInPhase.ScopeGoals,
      activityId = "test-activity",
    )

    assertEquals(4, batch.questions.size)
  }

  @Test
  fun `generation is stateless - same inputs yield same texts`() = runTest {
    val first = generate(phase = BuiltInPhase.ScopeGoals)
    val second = generate(phase = BuiltInPhase.ScopeGoals)

    assertEquals(first.questions.map { it.text }, second.questions.map { it.text })
  }

  // ---------- done is the pool's exhaustion signal ----------

  @Test
  fun `done is false while the phase pool still has unasked questions`() = runTest {
    val first = generate(phase = BuiltInPhase.ScopeGoals)

    assertFalse(first.done)
    assertTrue(first.questions.isNotEmpty())
    assertTrue(poolOf(BuiltInPhase.ScopeGoals).size > first.questions.size)
  }

  @Test
  fun `done is true once the phase pool is exhausted even when other phases were asked in`() = runTest {
    val research = generate(phase = BuiltInPhase.Research)
    assertFalse(research.done)

    // A batch that drains ValidationPlan ends the phase, even though the engine
    // was asked about ScopeGoals and the caller mixes in another phase's history.
    val asked = poolOf(BuiltInPhase.ValidationPlan)
      .mapIndexed { index, text -> question(id = "q$index", text = text) } + research.questions
    val batch = generate(phase = BuiltInPhase.ValidationPlan, previousQuestions = asked)

    assertTrue(batch.done)
    assertEquals(emptyList(), batch.questions)
  }

  @Test
  fun `a batch that drains the pool mid-phase is done while still answering`() = runTest {
    val pool = poolOf(BuiltInPhase.ValidationPlan)
    // Leave exactly one question unasked; the batch that hands it over ends the phase.
    val asked = pool.dropLast(1).mapIndexed { index, text -> question(id = "q$index", text = text) }

    val batch = generate(phase = BuiltInPhase.ValidationPlan, previousQuestions = asked)

    assertTrue(batch.done)
    assertEquals(listOf(pool.last()), batch.questions.map { it.text })
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
      "each phase needs enough questions to serve a round",
    )
  }

  @Test
  fun `pool texts are unique across phases`() {
    val flattened = HardcodedPlanningEngine.questionPoolByPhase.values.flatten()

    assertEquals(flattened.size, flattened.toSet().size)
  }

  /** One round for [phase], with [previousQuestions] standing in for the project so far. */
  private suspend fun generate(
    phase: BuiltInPhase,
    previousQuestions: List<Question> = emptyList(),
    engine: HardcodedPlanningEngine = generator,
  ) = engine.generateQuestions(
    title = "title",
    synopsis = "synopsis",
    previousQuestions = previousQuestions,
    roundId = "ctx",
    phase = phase,
    activityId = "test-activity",
  )

  private fun poolOf(phase: BuiltInPhase): List<String> =
    requireNotNull(HardcodedPlanningEngine.questionPoolByPhase[phase]) {
      "no pool for $phase"
    }
}