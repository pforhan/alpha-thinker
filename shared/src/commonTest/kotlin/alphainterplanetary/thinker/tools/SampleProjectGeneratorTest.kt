package alphainterplanetary.thinker.tools

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.FakeStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleProjectGeneratorTest {

  @Test
  fun `generate creates all eight sample projects with the expected ids`() = runTest {
    val storage = FakeStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    assertEquals(
      setOf(
        "sample-scope",
        "sample-research",
        "sample-design",
        "sample-execution",
        "sample-validation",
        "sample-dod",
        "sample-done",
        "sample-stress",
      ),
      storage.projects.keys,
    )
  }

  @Test
  fun `scope project has a short synopsis with a couple answered and five unanswered questions`() =
    runTest {
      val storage = FakeStorage()
      val generator = SampleProjectGenerator(storage)

      generator.generate()

      val scope = storage.projects.getValue("sample-scope")
      assertTrue(scope.synopsis.length < 200)
      assertEquals(BuiltInPhase.ScopeGoals, scope.currentPhase)
      assertEquals(7, scope.questions.size)
      assertEquals(2, scope.questions.count { it.isAnswered })
      assertEquals(5, scope.questions.count { it.isUnanswered })
    }

  @Test
  fun `phase sample projects each land on a distinct phase in library order`() = runTest {
    val storage = FakeStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    val byId = listOf(
      "sample-scope" to BuiltInPhase.ScopeGoals,
      "sample-research" to BuiltInPhase.Research,
      "sample-design" to BuiltInPhase.Design,
      "sample-execution" to BuiltInPhase.ExecutionPlan,
      "sample-validation" to BuiltInPhase.ValidationPlan,
      "sample-dod" to BuiltInPhase.DefinitionOfDone,
    ).associate { (id, _) -> id to storage.projects.getValue(id) }

    BuiltInPhase.entries.forEach { phase ->
      val project = byId.entries.first { it.value.currentPhase == phase }
      assertEquals(phase, project.value.currentPhase)
    }

    byId.forEach { (id, project) ->
      val completedRounds = project.rounds.count { it.isCompleted }
      assertTrue(
        completedRounds == project.rounds.size - 1,
        "$id should have exactly one open round (the current phase)",
      )
    }
  }

  @Test
  fun `done project has wrapped up every phase and resolved all of its questions`() = runTest {
    val storage = FakeStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    val done = storage.projects.getValue("sample-done")
    assertEquals(BuiltInPhase.DefinitionOfDone, done.currentPhase)
    assertEquals(6, done.rounds.size)
    assertEquals(5, done.rounds.count { it.isCompleted })
    assertTrue(done.questions.isNotEmpty())
    assertTrue(
      done.questions.all { it.isAnswered || it.isIgnored },
      "done project should have no open questions"
    )
    assertTrue(done.questions.any { it.isAnswered })
    assertTrue(done.questions.any { it.isIgnored })
  }

  @Test
  fun `stress project uses very long text in every field`() = runTest {
    val storage = FakeStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    val stress = storage.projects.getValue("sample-stress")
    assertTrue(stress.editableTitle.length > 100, "stress title should be very long")
    assertTrue(stress.synopsis.length > 1000, "stress synopsis should be very long")
    assertTrue(
      stress.questions.all { it.text.length > 100 },
      "stress question texts should be long"
    )
    assertTrue(
      stress.questions.any { q -> q.isAnswered && q.currentAnswer!!.text.length > 500 },
      "stress project should contain very long complete answers",
    )
  }

  @Test
  fun `generating twice does not grow the project count`() = runTest {
    val storage = FakeStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()
    generator.generate()

    assertEquals(8, storage.projects.size)
  }

  @Test
  fun `every answer id is unique across all projects`() = runTest {
    val storage = FakeStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    val answerIds = storage.projects.values
      .flatMap { it.questions }
      .flatMap { it.answers }
      .map { it.id }
    assertEquals(answerIds.size, answerIds.toSet().size)
  }
}