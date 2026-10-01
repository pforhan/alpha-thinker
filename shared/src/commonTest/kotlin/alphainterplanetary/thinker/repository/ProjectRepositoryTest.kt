package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.PlanningEngineSelector
import alphainterplanetary.thinker.engine.QuestionBatch
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.tasks.TaskFailed
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectRepositoryTest {

  private val now: Instant = Clock.System.now()

  private fun TestScope.repo(
    storage: FakeStorage = FakeStorage(),
    generator: PlanningEngine = FakePlanningEngine(),
    runner: TaskRunner = TaskRunner(CoroutineScope(coroutineContext)),
    settings: SettingsRepository = SettingsRepository(storage, CoroutineScope(coroutineContext)),
    activityLogger: ActivityLogger = RecordingActivityLogger(),
  ): ProjectRepository =
    ProjectRepository(storage, { generator }, runner, settings, activityLogger)

  // ---------- createProject ----------

  @Test
  fun `createProject with explicit title truncates to 30 chars`() = runTest {
    val generator = FakePlanningEngine()
    val repository = repo(generator = generator)
    val longTitle = "x".repeat(50)

    val project = repository.createProject("My synopsis", title = longTitle)
    testScheduler.advanceUntilIdle()

    assertEquals(longTitle.take(30), project.editableTitle)
    assertEquals("My synopsis", project.synopsis)
    assertEquals("Draft", project.status)
  }

  @Test
  fun `createProject without title shells an empty title and fills it from the recommender`() =
    runTest {
      val generator = FakePlanningEngine().apply { recommendedTitle = "From Generator" }
      val storage = FakeStorage()
      val repository = repo(storage = storage, generator = generator)

      val project = repository.createProject("My synopsis")
      assertEquals("", project.editableTitle, "the shell returns before the title lands")

      testScheduler.advanceUntilIdle()

      val persisted = storage.getProject(project.id)
      assertNotNull(persisted)
      assertEquals("From Generator", persisted.editableTitle)
      assertEquals("My synopsis", persisted.synopsis)
      assertEquals("Draft", persisted.status)
    }

  @Test
  fun `createProject trims synopsis and title`() = runTest {
    val repository = repo(generator = FakePlanningEngine().apply { recommendedTitle = "Fallback" })

    val project = repository.createProject("  leading and trailing  ", title = "  My Title  ")
    testScheduler.advanceUntilIdle()

    assertEquals("leading and trailing", project.synopsis)
    assertEquals("My Title", project.editableTitle)
  }

  @Test
  fun `createProject passes editable title and synopsis to initial generation`() = runTest {
    val generator = FakePlanningEngine().apply {
      recommendedTitle = "Recommended Title"
      questions += question("q1", "First?")
      questions += question("q2", "Second?")
    }
    val repository = repo(generator = generator)

    repository.createProject("My synopsis")
    testScheduler.advanceUntilIdle()

    assertEquals(1, generator.calls.size)
    val call = generator.calls.single()
    assertEquals("Recommended Title", call.editableTitle)
    assertEquals("My synopsis", call.synopsis)
  }

  @Test
  fun `createProject saves generated questions onto the project via the task`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("q1")
      questions += question("q2")
    }
    val storage = FakeStorage()
    val repository = repo(storage = storage, generator = generator)

    val project = repository.createProject("My synopsis")
    assertTrue(
      project.questions.isEmpty(),
      "creation persists the shell and returns; generation runs on the task runner",
    )

    testScheduler.advanceUntilIdle()

    val persisted = storage.getProject(project.id)
    assertNotNull(persisted)
    assertEquals(setOf("q1", "q2"), persisted.questions.map { it.id }.toSet())
  }

  @Test
  fun `createProject creates round 1 as an Initial round in the first phase`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("q1")
      questions += question("q2")
    }
    val storage = FakeStorage()
    val repository = repo(storage = storage, generator = generator)

    val project = repository.createProject("My synopsis")
    testScheduler.advanceUntilIdle()

    val round = project.rounds.single()
    assertEquals(1, round.roundNumber)
    assertEquals(RoundOrigin.Initial, round.origin)
    assertEquals(Phase.first, round.phase)
    assertEquals(project.id, round.projectId)
    assertEquals(round.id, storage.getProject(project.id)?.rounds?.single()?.id)
    assertEquals(round.id, generator.calls.single().roundId)
    assertEquals(Phase.first, generator.calls.single().phase)
  }

  @Test
  fun `createProject keeps the shell when initial generation fails`() = runTest {
    val failing = object : PlanningEngine {
      override val source: LogSource = LogSource.Lite
      override val contextWindowTokens: Int? = 8192

      override suspend fun recommendTitle(synopsis: String, activityId: String): String = "Title"

      override suspend fun generateQuestions(
        title: String,
        synopsis: String,
        previousQuestions: List<Question>,
        roundId: String,
        phase: Phase,
        activityId: String,
        priorSummaries: List<PlanningContext.PhaseSummary>,
      ): QuestionBatch {
        throw PlanningEngine.AnalysisFailure("no model")
      }
    }
    val storage = FakeStorage()
    val repository = repo(storage = storage, generator = failing)

    val project = repository.createProject("My synopsis")
    testScheduler.advanceUntilIdle()

    val persisted = storage.getProject(project.id)
    assertNotNull(persisted)
    assertTrue(persisted.questions.isEmpty(), "the shell persists even when generation fails")
    assertEquals(listOf(project.id), storage.getAllProjects().map { it.id })
  }

  @Test
  fun `a queued task runs the engine frozen when it was enqueued`() =
    runTest {
      val enqueued = FakePlanningEngine().apply {
        recommendedTitle = "Enqueued Engine"
        questions += question("qa", "From the enqueued engine?")
      }
      val later = FakePlanningEngine().apply { recommendedTitle = "Latest Engine" }
      var current: PlanningEngine = enqueued
      val runner = TaskRunner(CoroutineScope(coroutineContext))
      val storage = FakeStorage()
      val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
      val repository = ProjectRepository(
        storage,
        { current },
        runner,
        settings,
        RecordingActivityLogger(),
      )

      // createProject enqueues the title + initial batch under `current` (enqueued).
      val project = repository.createProject("My synopsis")
      // The engine setting changes before the queue drains.
      current = later
      testScheduler.advanceUntilIdle()

      val persisted = storage.getProject(project.id)
      assertNotNull(persisted)
      assertEquals("Enqueued Engine", persisted.editableTitle)
      assertEquals(listOf("qa"), persisted.questions.map { it.id })
      assertEquals(1, enqueued.calls.size)
      assertTrue(later.calls.isEmpty(), "the later engine never touches the locked task")
    }

  // ---------- recommendTitle (retry) ----------

  @Test
  fun `recommendTitle re-runs the recommender for a project that never got one`() = runTest {
    val generator = FakePlanningEngine().apply { recommendedTitle = "Second Try" }
    val storage = FakeStorage(mutableMapOf("p1" to untitledProject()))
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = repo(storage = storage, generator = generator, runner = runner)

    repository.recommendTitle("p1")
    testScheduler.advanceUntilIdle()

    assertEquals("Second Try", storage.getProject("p1")?.editableTitle)
    assertEquals(TaskStatus.Succeeded, runner.tasks.value.single().status)
  }

  @Test
  fun `recommendTitle never overwrites a title the project already has`() = runTest {
    val generator = FakePlanningEngine().apply { recommendedTitle = "Should Not Land" }
    val storage = FakeStorage(mutableMapOf("p1" to untitledProject().copy(editableTitle = "Mine")))
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = repo(storage = storage, generator = generator, runner = runner)

    repository.recommendTitle("p1")
    testScheduler.advanceUntilIdle()

    assertEquals("Mine", storage.getProject("p1")?.editableTitle)
  }

  // ---------- updateProject ----------

  @Test
  fun `updateProject KEEP preserves answers and ignore state`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "old synopsis",
      editableTitle = "old title",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "1"))),
        question("q2", ignoredAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.updateProject(
      id = "p1",
      title = "New Title",
      synopsis = "New Synopsis",
      mode = ProjectUpdateMode.KEEP,
    )

    assertNotNull(updated)
    assertEquals("New Title", updated.editableTitle)
    assertEquals("New Synopsis", updated.synopsis)
    assertEquals(1, updated.questions[0].answers.size)
    assertTrue(updated.questions[0].isAnswered)
    assertNotNull(updated.questions[1].ignoredAt)
  }

  @Test
  fun `updateProject CLEAR resets answers drafts and ignore state`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "synopsis",
      editableTitle = "title",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "7"))),
        question("q2", ignoredAt = now),
        question("q3", draftText = "draft", draftUpdatedAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.updateProject(
      id = "p1",
      title = "Title",
      synopsis = "synopsis",
      mode = ProjectUpdateMode.CLEAR,
    )

    assertNotNull(updated)
    assertNull(updated.questions[0].currentAnswer)
    assertFalse(updated.questions[0].isAnswered)
    assertNull(updated.questions[1].ignoredAt)
    assertFalse(updated.questions[2].isDraft)
    assertNull(updated.questions[2].draftText)
  }

  @Test
  fun `updateProject returns null for missing project`() = runTest {
    val repository = repo()

    val result = repository.updateProject(
      id = "missing",
      title = "T",
      synopsis = "S",
      mode = ProjectUpdateMode.KEEP,
    )

    assertNull(result)
  }

  // ---------- getUnansweredQuestions ----------

  @Test
  fun `getUnansweredQuestions excludes answered and ignored`() = runTest {
    val project = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("answered", answers = listOf(answer("answered", "A", id = "1"))),
        question("ignored", ignoredAt = now),
        question("open"),
        question("draft", draftText = "d", draftUpdatedAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val unanswered = project.unansweredQuestions

    assertEquals(listOf("open", "draft"), unanswered.map { it.id })
  }

  // ---------- saveAnswer ----------

  @Test
  fun `saveAnswer commits a completed answer`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "My answer",
      completed = true,
    )

    assertNotNull(updated)
    val current = updated.questions.single().currentAnswer
    assertNotNull(current)
    assertEquals("My answer", current.text)
    assertTrue(updated.questions.single().isAnswered)
  }

  @Test
  fun `saveAnswer stores a draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Draft text",
      completed = false,
    )

    assertNotNull(updated)
    assertEquals("Draft text", updated.questions.single().draftText)
    assertTrue(updated.questions.single().isDraft)
    assertFalse(updated.questions.single().isAnswered)
  }

  @Test
  fun `saveAnswer blank text clears the draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", draftText = "in progress", draftUpdatedAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "",
      completed = false,
    )

    assertNotNull(updated)
    assertNull(updated.questions.single().draftText)
    assertNull(updated.questions.single().draftUpdatedAt)
    assertFalse(updated.questions.single().isDraft)
  }

  @Test
  fun `saveAnswer editing a committed answer demotes it to a draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "1"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Edited",
      completed = false,
    )

    assertNotNull(updated)
    val q = updated.questions.single()
    assertNull(q.currentAnswer)
    assertFalse(q.isAnswered)
    assertTrue(q.isDraft)
    assertEquals("Edited", q.draftText)
    assertEquals(1, q.answers.size, "the committed version stays in immutable history")
  }

  @Test
  fun `saveAnswer committing unchanged text is a no-op`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "1"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val unchanged = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    assertNotNull(unchanged)
    val q = unchanged.questions.single()
    assertEquals(1, q.answers.size, "no new version for identical committed text")
    assertTrue(q.isAnswered)
    assertEquals("Answer", q.currentAnswer?.text)
  }

  @Test
  fun `saveAnswer commits a new version when text changes`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "First", id = "1"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Second",
      completed = true,
    )

    assertNotNull(updated)
    val q = updated.questions.single()
    assertEquals(2, q.answers.size, "an edit appends an immutable version")
    assertEquals("Second", q.currentAnswer?.text)
    assertEquals(listOf("First", "Second"), q.answers.map { it.text })
  }

  @Test
  fun `saveAnswer generating a draft for a question with no text leaves it unanswered`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "   ",
      completed = false,
    )

    assertNotNull(updated)
    val q = updated.questions.single()
    assertFalse(q.isDraft)
    assertFalse(q.isAnswered)
  }

  @Test
  fun `saveAnswer does not generate follow-ups when all active questions are answered`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("f1")
      questions += question("f2")
    }
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    assertNotNull(updated)
    assertTrue(generator.calls.isEmpty())
    assertEquals(listOf("q1"), updated.questions.map { it.id })
    assertTrue(updated.rounds.isEmpty())
  }

  @Test
  fun `saveAnswer does not generate follow-ups when not all answered`() = runTest {
    val generator = FakePlanningEngine()
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1"), question("q2")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    assertNotNull(updated)
    assertTrue(generator.calls.isEmpty())
    assertEquals(listOf("q1", "q2"), updated.questions.map { it.id })
  }

  @Test
  fun `saveAnswer does not generate follow-ups when only ignored questions remain`() = runTest {
    val generator = FakePlanningEngine()
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", ignoredAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    // The question being answered is ignored; with no active questions left the
    // all-answered check should not trigger follow-ups.
    assertTrue(generator.calls.isEmpty())
    assertNotNull(updated)
  }

  @Test
  fun `saveAnswer returns null when question not found`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val result = repository.saveAnswer("p1", "missing", "text", completed = true)

    assertNull(result)
  }

  // ---------- generateMoreQuestions ----------

  @Test
  fun `generateMoreQuestions starts a UserRequested round and enqueues follow-up generation`() =
    runTest {
      val generator = FakePlanningEngine().apply {
        questions += question("f1")
      }
      val storage = FakeStorage(
        mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
      )
      val repository = repo(storage = storage, generator = generator)

      val updated = repository.generateMoreQuestions("p1")

      assertNotNull(updated)
      val round = updated.rounds.last()
      assertEquals(2, round.roundNumber)
      assertEquals(RoundOrigin.UserRequested, round.origin)
      assertEquals(BuiltInPhase.Design, round.phase)
      assertEquals("p1", round.projectId)
      // The round lands immediately; the question arrives via the enqueued task.
      assertEquals(listOf("q1"), updated.questions.map { it.id })
      assertEquals(2, storage.getProject("p1")?.rounds?.size)

      testScheduler.advanceUntilIdle()

      val persisted = storage.getProject("p1")
      assertNotNull(persisted)
      assertEquals(listOf("q1", "f1"), persisted.questions.map { it.id })
      assertEquals(round.id, generator.calls.single().roundId)
      assertEquals(BuiltInPhase.Design, generator.calls.single().phase)
      assertEquals(listOf("q1"), generator.calls.single().previousQuestions.map { it.id })
    }

  /**
   * The engine is budget-ignorant by design, so the trim is the repository's:
   * what it hands over is the same interview minus the earlier phases' answers,
   * and the project on disk keeps them. The task's activity also records that a
   * compaction happened — the prompt the engine received says nothing about it,
   * so the log is the only place it surfaces.
   */
  @Test
  fun `generation is handed the project trimmed to the configured context budget`() = runTest {
    val generator = FakePlanningEngine()
    val log = RecordingActivityLogger()
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to Project(
          id = "p1",
          synopsis = "s",
          editableTitle = "t",
          status = "Draft",
          questions = listOf(
            question(
              "earlier",
              "An early question?",
              answers = listOf(answer("earlier", "word ".repeat(500), id = "a1")),
              roundId = "r1",
            ),
            question(
              "live",
              "A live question?",
              answers = listOf(answer("live", "short", id = "a2")),
              roundId = "r2",
            ),
          ),
          rounds = listOf(
            round("r1", phase = BuiltInPhase.ScopeGoals, completedAt = now),
            round("r2", phase = BuiltInPhase.Design),
          ),
          createdAt = now,
          updatedAt = now,
        ),
      ),
    )
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 500)
    val runner = TaskRunner(CoroutineScope(coroutineContext), log)
    val repository = repo(
      storage = storage,
      generator = generator,
      runner = runner,
      settings = settings,
      activityLogger = log,
    )

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val handedOver = generator.calls.single().previousQuestions.associateBy { it.id }
    assertEquals(setOf("earlier", "live"), handedOver.keys)
    assertFalse(handedOver.getValue("earlier").isAnswered, "the earlier phase's answer is dropped")
    assertTrue(handedOver.getValue("live").isAnswered, "the current phase's answer is kept")
    assertTrue(
      handedOver.getValue("earlier").compacted,
      "the dropped answer is marked omitted, so the prompt never calls it unanswered",
    )
    assertTrue(
      storage.getProject("p1")!!.questions.all { it.isAnswered },
      "the trim is a rendering concern; the project keeps its answers",
    )

    val note = log.entries.single { it.log.startsWith("context compacted") }
    assertEquals("context compacted: 1 answer dropped to fit budget", note.log)
    val task = runner.tasks.value.single()
    assertEquals(task.id, note.activityId, "the note rides the generation task's activity")
    assertEquals(LogCategory.TaskRun, note.category)
    assertEquals(LogSource.TaskRunner, note.source)
    assertEquals("p1", note.projectId)
  }

  /** Nothing dropped, nothing logged: the note is about compaction, not every send. */
  @Test
  fun `no compaction note is logged when the project fits the budget`() = runTest {
    val log = RecordingActivityLogger()
    val generator = FakePlanningEngine()
    val runner = TaskRunner(CoroutineScope(coroutineContext), log)
    val repository = repo(
      generator = generator,
      runner = runner,
      activityLogger = log,
    )

    repository.createProject("A short synopsis")
    testScheduler.advanceUntilIdle()

    assertTrue(
      log.entries.none { it.log.startsWith("context compacted") },
      "a transcript that fit is not a compaction",
    )
  }

  /** A failing run still reports its compaction — the note files before the engine call. */
  @Test
  fun `a failed generation still logs the compaction that preceded it`() = runTest {
    val log = RecordingActivityLogger()
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to Project(
          id = "p1",
          synopsis = "s",
          editableTitle = "t",
          status = "Draft",
          questions = listOf(
            question(
              "earlier",
              "An early question?",
              answers = listOf(answer("earlier", "word ".repeat(500), id = "a1")),
              roundId = "r1",
            ),
            question(
              "live",
              "A live question?",
              answers = listOf(answer("live", "short", id = "a2")),
              roundId = "r2",
            ),
          ),
          rounds = listOf(
            round("r1", phase = BuiltInPhase.ScopeGoals, completedAt = now),
            round("r2", phase = BuiltInPhase.Design),
          ),
          createdAt = now,
          updatedAt = now,
        ),
      ),
    )
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    val generator = FakePlanningEngine().apply {
      generationFailure = RuntimeException("model exploded")
    }
    budgetedFor(generator, 500)
    val runner = TaskRunner(CoroutineScope(coroutineContext), log)
    val repository = repo(
      storage = storage,
      generator = generator,
      runner = runner,
      settings = settings,
      activityLogger = log,
    )

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val task = runner.tasks.value.single()
    assertEquals(TaskStatus.Failed, task.status)
    val activityId = log.entries.first { it.log.startsWith("started:") }.activityId
    assertEquals(
      listOf(
        "started: QuestionGeneration",
        "context compacted: 1 answer dropped to fit budget",
        "failed: model exploded",
      ),
      log.entries.map { it.log },
      "the note files before the engine call, so it survives a failed run",
    )
    assertTrue(
      log.entries.take(3).all { it.activityId == activityId },
      "the note joins the failed task's activity",
    )
  }

  // ---------- the near-limit check and the summarize choice ----------

  /**
   * The check is what the dialog is built from, so it has to describe the
   * project the user is looking at: what it would cost to send, and what each
   * of the choices would give up.
   */
  @Test
  fun `checkContext describes the project against its budget`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    val generator = FakePlanningEngine()
    budgetedFor(generator, 500)
    val repository = repo(storage = storage, generator = generator, settings = settings)

    val check = repository.checkContext("p1")

    assertNotNull(check)
    assertEquals(556, check.windowTokens, "the window is the model's, as configured")
    assertEquals(500, check.budgetTokens, "the budget is the transcript's share of it")
    assertTrue(check.estimatedTokens > 500, "the fixture overruns the budget it is given")
    assertTrue(check.nearLimit, "over the budget is always worth asking about")
    assertEquals(2, check.droppableAnswers, "both past phases' answers have to go")
    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research),
      check.summarizablePhases,
      "the past phases, oldest first — the units a summary would be written from",
    )
  }

  /** A small project is nobody's problem, and nobody gets interrupted for it. */
  @Test
  fun `checkContext leaves a small project under the budget`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to Project(
          id = "p1",
          synopsis = "s",
          editableTitle = "t",
          status = "Draft",
          questions = listOf(question("q1", "A short question?")),
          rounds = listOf(round("r1", phase = BuiltInPhase.ScopeGoals)),
          createdAt = now,
          updatedAt = now,
        ),
      )
    )
    val repository = repo(storage = storage)

    val check = repository.checkContext("p1")

    assertNotNull(check)
    assertFalse(check.nearLimit)
    assertFalse(check.canSummarize, "there is no past phase to summarize")
  }

  /**
   * The summarize choice is only offered to an engine that can carry it out:
   * a Lite engine draws fixed strings from a pool and has no way to condense
   * anything, so offering it would be offering a failure.
   */
  @Test
  fun `checkContext only offers summarizing to an engine that can do it`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))

    val lite = repo(storage = storage).checkContext("p1")
    assertNotNull(lite)
    assertEquals(listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research), lite.summarizablePhases)
    assertFalse(lite.canSummarize)

    val llm = repo(storage = storage, generator = FakePlanningEngine().apply {
      canSummarize = true
    }).checkContext("p1")
    assertNotNull(llm)
    assertTrue(llm.canSummarize)
  }

  @Test
  fun `checkContext is null for a project that is gone`() = runTest {
    assertNull(repo().checkContext("nope"))
  }

  /**
   * The summarize loop: the oldest full phase first, re-measured after each
   * one, and only as many as it takes. A project that fits after one summary
   * spends exactly one model call, and the summary — not the verbatim answer —
   * is what the question prompt receives for that phase.
   */
  @Test
  fun `the summarize choice condenses the oldest phase and stops once it fits`() = runTest {
    val generator = FakePlanningEngine().apply {
      canSummarize = true
      summaries[BuiltInPhase.ScopeGoals] = "A menu planner for home cooks."
    }
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 1000)
    val repository = repo(storage = storage, generator = generator, settings = settings)

    repository.generateMoreQuestions("p1", ContextCompaction.SummarizeEarlierPhases)
    testScheduler.advanceUntilIdle()

    assertEquals(
      listOf(BuiltInPhase.ScopeGoals),
      generator.summarizeCalls.map { it.phase },
      "the oldest phase goes first, and one summary is enough to fit here",
    )
    val summary = generator.summarizeCalls.single()
    assertEquals("t", summary.editableTitle)
    assertEquals("s", summary.synopsis)
    assertTrue(
      summary.transcript.contains(PhasedAnswerText),
      "the phase is summarized from the transcript the model would have been sent",
    )
    assertEquals(
      1,
      generator.calls.size,
      "one batch — the summarize request is not a question round",
    )
  }

  /**
   * The summary replaces the phase's answers in the prompt rather than being
   * appended to them: the point is to stop paying for the verbatim text.
   */
  @Test
  fun `a summarized phase's answers are compacted away for the prompt`() = runTest {
    val generator = FakePlanningEngine().apply { canSummarize = true }
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 2000)
    val repository = repo(storage = storage, generator = generator, settings = settings)

    repository.generateMoreQuestions("p1", ContextCompaction.SummarizeEarlierPhases)
    testScheduler.advanceUntilIdle()

    val call = generator.calls.single()
    val handedOver = call.previousQuestions.associateBy { it.id }
    assertTrue(handedOver.getValue("q1").compacted, "the summarized phase's answer is gone")
    assertTrue(handedOver.getValue("q2").isAnswered, "the next phase is still verbatim")
    assertEquals(
      listOf(BuiltInPhase.ScopeGoals),
      call.priorSummaries.map { it.phase },
    )
    assertEquals(setOf("q1"), call.priorSummaries.single().coversQuestionIds)
    assertEquals("Summary of Scope & Goals", call.priorSummaries.single().summary)
    assertTrue(
      storage.getProject("p1")!!.questions.all { it.isAnswered },
      "compaction is a rendering concern; the project keeps its answers",
    )
  }

  /** A project far over budget keeps going: one phase per request, oldest first. */
  @Test
  fun `the summarize choice keeps going until the transcript fits`() = runTest {
    val generator = FakePlanningEngine().apply { canSummarize = true }
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 500)
    val repository = repo(storage = storage, generator = generator, settings = settings)

    repository.generateMoreQuestions("p1", ContextCompaction.SummarizeEarlierPhases)
    testScheduler.advanceUntilIdle()

    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research),
      generator.summarizeCalls.map { it.phase },
      "both past phases, oldest first — one summary still wasn't enough",
    )
    assertEquals(2, generator.calls.single().priorSummaries.size)
  }

  /**
   * The current phase is never summarized: it is what the round is being asked
   * about, and there is no question after this one to protect it from.
   */
  @Test
  fun `the summarize choice never reaches the phase in progress`() = runTest {
    val generator = FakePlanningEngine().apply { canSummarize = true }
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 500)
    val repository = repo(storage = storage, generator = generator, settings = settings)

    repository.generateMoreQuestions("p1", ContextCompaction.SummarizeEarlierPhases)
    testScheduler.advanceUntilIdle()

    assertFalse(
      generator.summarizeCalls.any { it.phase == BuiltInPhase.Design },
      "the live phase's answers are not a candidate",
    )
    assertTrue(
      generator.calls.single().previousQuestions.first { it.id == "q3" }.isAnswered,
      "and it survives a budget nothing could fit",
    )
  }

  /**
   * Every model request files its own rows under the generation's activity, so
   * a compaction that took three requests is three prompt/response pairs —
   * visible in the log, and with the batch last, because that is the pair the
   * activity's headline reads.
   */
  @Test
  fun `each summarized phase is recorded on the task's activity`() = runTest {
    val generator = FakePlanningEngine().apply { canSummarize = true }
    val log = RecordingActivityLogger()
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 500)
    val runner = TaskRunner(CoroutineScope(coroutineContext), log)
    val repository = repo(
      storage = storage,
      generator = generator,
      runner = runner,
      settings = settings,
      activityLogger = log,
    )

    repository.generateMoreQuestions("p1", ContextCompaction.SummarizeEarlierPhases)
    testScheduler.advanceUntilIdle()

    val notes = log.entries.filter { it.log.startsWith("context compacted") }
    assertEquals(2, notes.size, "one note per phase, not one per generation")
    assertTrue(notes.all { it.log.contains("Scope & Goals summarized") || it.log.contains("Research summarized") })
    assertTrue(notes.all { it.log.contains("1 answer replaced") }, notes.joinToString(" | "))
    val activityId = log.entries.first { it.log.startsWith("started:") }.activityId
    assertTrue(notes.all { it.activityId == activityId }, "the notes join the task's activity")
  }

  /**
   * "Keep everything" is a real answer, not a no-op: the budget is a guard rail
   * and the user is allowed to say no to it.
   */
  @Test
  fun `keeping everything hands the engine the whole interview`() = runTest {
    val generator = FakePlanningEngine()
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 500)
    val repository = repo(storage = storage, generator = generator, settings = settings)

    repository.generateMoreQuestions("p1", ContextCompaction.KeepEverything)
    testScheduler.advanceUntilIdle()

    val call = generator.calls.single()
    assertEquals(listOf("q1", "q2", "q3"), call.previousQuestions.map { it.id })
    assertTrue(call.previousQuestions.all { it.isAnswered })
    assertEquals(emptyList(), call.priorSummaries)
  }

  /**
   * A wrap-up advance carries the same choice as "Get more questions" — and it
   * is the moment the phase being left behind joins the summarizable ones, so
   * the choice reaches the generation rather than the check.
   */
  @Test
  fun `the summarize choice follows a phase advance`() = runTest {
    val generator = FakePlanningEngine().apply { canSummarize = true }
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    budgetedFor(generator, 500)
    val repository = repo(storage = storage, generator = generator, settings = settings)

    repository.advanceToPhase(
      "p1",
      BuiltInPhase.ExecutionPlan,
      ContextCompaction.SummarizeEarlierPhases,
    )
    testScheduler.advanceUntilIdle()

    val call = generator.calls.single()
    assertEquals(BuiltInPhase.ExecutionPlan, call.phase)
    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research),
      call.priorSummaries.map { it.phase },
      "the past phases are condensed on the way into the new one",
    )
    assertEquals(
      BuiltInPhase.ScopeGoals,
      repository.checkContext("p1")?.summarizablePhases?.first(),
    )
  }

  @Test
  fun `generateMoreQuestions does not start a round when the phase is exhausted`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to phaseProject(
          round = round(id = "r1", phase = BuiltInPhase.ScopeGoals, outcome = RoundOutcome.Exhausted),
        )
      )
    )
    val generator = FakePlanningEngine()
    val repository = repo(storage = storage, generator = generator)

    val result = repository.generateMoreQuestions("p1")

    assertNotNull(result)
    assertEquals(1, result.rounds.size)
    assertEquals(listOf("q1"), result.questions.map { it.id })
    testScheduler.advanceUntilIdle()
    assertEquals(0, generator.calls.size, "an exhausted phase never calls the engine")
  }

  // ---------- awaiting variants ----------

  @Test
  fun `createProjectAndWait returns once the opening round has landed`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("q1", "First?")
    }
    val storage = FakeStorage()
    val repository = repo(storage = storage, generator = generator)

    val project = repository.createProjectAndWait("My synopsis")

    // No advanceUntilIdle: the questions are already there on return, which is
    // the whole difference from createProject.
    assertEquals(
      listOf("First?"),
      storage.getProject(project.id)?.questions?.map { it.text },
    )
    assertEquals(RoundOutcome.MoreAvailable, storage.getProject(project.id)?.rounds?.single()?.outcome)
  }

  @Test
  fun `createProjectAndWait throws rather than returning a project with no questions`() = runTest {
    val generator = FakePlanningEngine().apply {
      generationFailure = IllegalStateException("engine is down")
    }
    val storage = FakeStorage()
    val repository = repo(storage = storage, generator = generator)

    val thrown = assertFailsWith<TaskFailed> {
      repository.createProjectAndWait("My synopsis")
    }

    assertEquals(TaskStatus.Failed, thrown.task.status)
    assertEquals(1, storage.projects.size, "the project shell is still left behind")
  }

  @Test
  fun `advanceToPhaseAndWait returns once the new phase's round has landed`() = runTest {
    val generator = FakePlanningEngine().apply {
      batchFor = { _, roundId -> listOf(question("q9", "Fresh?", roundId = roundId)) }
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.ScopeGoals)))
    )
    val repository = repo(storage = storage, generator = generator)

    repository.advanceToPhaseAndWait("p1", BuiltInPhase.Design)

    assertEquals(
      listOf("q1", "Fresh?"),
      storage.getProject("p1")?.questions?.map { it.text },
      "the new phase's questions are there on return, alongside the ones already asked",
    )
    assertEquals(BuiltInPhase.Design, storage.getProject("p1")?.rounds?.last()?.phase)
  }

  @Test
  fun `advanceToPhaseAndWait throws when the round fails`() = runTest {
    val generator = FakePlanningEngine().apply {
      generationFailure = IllegalStateException("engine is down")
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.ScopeGoals)))
    )
    val repository = repo(storage = storage, generator = generator)

    assertFailsWith<TaskFailed> {
      repository.advanceToPhaseAndWait("p1", BuiltInPhase.Design)
    }

    // The round is still there, latched failed — the phase swap landed, only its
    // questions did not.
    assertEquals(BuiltInPhase.Design, storage.getProject("p1")?.rounds?.last()?.phase)
    assertEquals(RoundOutcome.Failed, storage.getProject("p1")?.rounds?.last()?.outcome)
  }

  @Test
  fun `generateMoreQuestionsAndWait returns once the round has landed`() = runTest {
    val generator = FakePlanningEngine().apply {
      batchFor = { _, roundId -> listOf(question("q9", "Fresh?", roundId = roundId)) }
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.ScopeGoals)))
    )
    val repository = repo(storage = storage, generator = generator)

    repository.generateMoreQuestionsAndWait("p1")

    assertEquals(
      listOf("q1", "Fresh?"),
      storage.getProject("p1")?.questions?.map { it.text },
    )
  }

  @Test
  fun `generateMoreQuestionsAndWait still short-circuits an exhausted phase`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to phaseProject(
          round = round(id = "r1", phase = BuiltInPhase.ScopeGoals, outcome = RoundOutcome.Exhausted),
        )
      )
    )
    val generator = FakePlanningEngine()
    val repository = repo(storage = storage, generator = generator)

    val result = repository.generateMoreQuestionsAndWait("p1")

    assertNotNull(result)
    assertEquals(1, result.rounds.size, "no round was opened, so none was waited on")
    assertEquals(0, generator.calls.size)
  }

  // ---------- round outcomes ----------

  private fun storageWith(project: Project): FakeStorage =
    FakeStorage(mutableMapOf(project.id to project))

  /**
   * A project whose only round is [round], which already produced `q1`, so
   * follow-up generation has a round to latch its outcome onto.
   */
  private fun phaseProject(round: Round): Project = Project(
    id = "p1",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = listOf(question("q1")),
    rounds = listOf(round),
    createdAt = now,
    updatedAt = now,
  )

  private fun untitledProject(): Project = Project(
    id = "p1",
    synopsis = "s",
    editableTitle = "",
    status = "Draft",
    questions = emptyList(),
    rounds = emptyList(),
    createdAt = now,
    updatedAt = now,
  )

  /**
   * A project three phases in, each with a long answer, so the near-limit paths
   * have something to bite on. The two long answers are 2000 characters — a
   * little over 500 tokens each — so the smallest budget the settings offer
   * (500) has to drop both, and the current phase's short answer is the one
   * thing no budget can reach.
   */
  private fun phasedProject(): Project = Project(
    id = "p1",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = listOf(
      question("q1", "Q1?", answers = listOf(answer("q1", PhasedAnswerText, id = "a1")), roundId = "r1"),
      question("q2", "Q2?", answers = listOf(answer("q2", PhasedAnswerText, id = "a2")), roundId = "r2"),
      question("q3", "Q3?", answers = listOf(answer("q3", "short", id = "a3")), roundId = "r3"),
    ),
    rounds = listOf(
      round("r1", phase = BuiltInPhase.ScopeGoals, completedAt = now),
      round("r2", phase = BuiltInPhase.Research, completedAt = now),
      round("r3", phase = BuiltInPhase.Design),
    ),
    createdAt = now,
    updatedAt = now,
  )

  /** 2000 characters, so each of the two long answers renders to about 500 tokens. */
  private val PhasedAnswerText: String = "word ".repeat(400)

  /**
   * Budget plumbing for the compaction tests: the window [generator] reports is
   * the only input, and the budget is a fixed [PlanningContext.TranscriptSharePercent]
   * of it — so a window a hundred tokens over the wanted budget gives a budget a
   * hair under it, which keeps the assertions written in token counts.
   */
  private fun budgetedFor(generator: FakePlanningEngine, budgetTokens: Int) {
    val share = PlanningContext.TranscriptSharePercent
    // Rounded up, so the window resolves to at least the budget asked for
    // rather than a token short of it.
    generator.contextWindowTokens = (budgetTokens * 100 + share - 1) / share
  }

  @Test
  fun `questions with done false latch MoreAvailable and leave the phase open`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("f1")
      done = false
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
    )
    val repository = repo(storage = storage, generator = generator)

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject("p1"))
    val outcomeRound = persisted.rounds.last()
    assertEquals(RoundOutcome.MoreAvailable, outcomeRound.outcome)
    assertNull(outcomeRound.outcomeDetail)
    assertFalse(persisted.currentPhaseExhausted)

    // And the affordance still opens a round, because only the engine's `done` closes a phase.
    assertNotNull(repository.generateMoreQuestions("p1"))
  }

  @Test
  fun `questions with done true latch Exhausted and close the phase`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("f1")
      done = true
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
    )
    val repository = repo(storage = storage, generator = generator)

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject("p1"))
    val outcomeRound = persisted.rounds.last()
    assertEquals(
      RoundOutcome.Exhausted,
      outcomeRound.outcome,
      "the engine's stop condition wins even when it answered with questions",
    )
    assertTrue(persisted.currentPhaseExhausted)
    assertEquals(listOf("q1", "f1"), persisted.questions.map { it.id })
  }

  @Test
  fun `an empty batch with done true latches Exhausted and not Failed`() = runTest {
    val generator = FakePlanningEngine().apply { done = true }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
    )
    val repository = repo(storage = storage, generator = generator)

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject("p1"))
    assertEquals(RoundOutcome.Exhausted, persisted.rounds.last().outcome)
    assertTrue(persisted.currentPhaseExhausted)
    assertNull(persisted.currentPhaseFailure)
  }

  @Test
  fun `an empty batch with done false fails the task and keeps the phase retryable`() = runTest {
    val generator = FakePlanningEngine()
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
    )
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = repo(storage = storage, generator = generator, runner = runner)

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject("p1"))
    val outcomeRound = persisted.rounds.last()
    assertEquals(RoundOutcome.Failed, outcomeRound.outcome)
    assertFalse(persisted.currentPhaseExhausted, "a failure must not close the phase")
    assertNotNull(persisted.currentPhaseFailure)
    assertEquals(outcomeRound.outcomeDetail, persisted.currentPhaseFailure)
    assertEquals(listOf("q1"), persisted.questions.map { it.id })

    val task = runner.tasks.value.single { it.kind == TaskKind.QuestionGeneration }
    assertEquals(TaskStatus.Failed, task.status)
    assertEquals(persisted.currentPhaseFailure, task.error)
  }

  @Test
  fun `a batch of only already-asked questions with done false fails rather than quietly adding nothing`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("q1")
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
    )
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = repo(storage = storage, generator = generator, runner = runner)

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject("p1"))
    assertEquals(RoundOutcome.Failed, persisted.rounds.last().outcome)
    assertEquals(listOf("q1"), persisted.questions.map { it.id })
    assertEquals(
      TaskStatus.Failed,
      runner.tasks.value.single { it.kind == TaskKind.QuestionGeneration }.status,
    )
  }

  @Test
  fun `an engine failure latches Failed with its message and rethrows to the task`() = runTest {
    val generator = FakePlanningEngine().apply {
      generationFailure = PlanningEngine.AnalysisFailure("the model refused")
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
    )
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = repo(storage = storage, generator = generator, runner = runner)

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject("p1"))
    val outcomeRound = persisted.rounds.last()
    assertEquals(RoundOutcome.Failed, outcomeRound.outcome)
    assertEquals("the model refused", outcomeRound.outcomeDetail)
    assertEquals(
      "the model refused",
      runner.tasks.value.single { it.kind == TaskKind.QuestionGeneration }.error,
    )
  }

  @Test
  fun `a cancelled generation leaves the round Pending rather than Failed`() = runTest {
    val generator = FakePlanningEngine().apply {
      generationFailure = CancellationException("cancelled")
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(round("r1", phase = BuiltInPhase.Design)))
    )
    val repository = repo(storage = storage, generator = generator)

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject("p1"))
    assertEquals(
      RoundOutcome.Pending,
      persisted.rounds.last().outcome,
      "a cancelled attempt is not a dead end, so it must not read as one",
    )
    assertNull(persisted.currentPhaseFailure)
  }

  @Test
  fun `the newest round in the phase decides exhaustion and nothing older overrides it`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to phaseProject(
          round = round(id = "r1", phase = BuiltInPhase.Design, roundNumber = 1, outcome = RoundOutcome.Exhausted),
        ).copy(
          rounds = listOf(
            round(id = "r1", phase = BuiltInPhase.Design, roundNumber = 1, outcome = RoundOutcome.Exhausted),
            round(
              id = "r2",
              phase = BuiltInPhase.Design,
              roundNumber = 2,
              origin = RoundOrigin.UserRequested,
              outcome = RoundOutcome.Failed,
              outcomeDetail = "boom",
            ),
          )
        )
      )
    )
    val repository = repo(storage = storage)

    // The newest round is the failed one, so the phase is open again.
    val persisted = assertNotNull(storage.getProject("p1"))
    assertFalse(persisted.currentPhaseExhausted)
    assertEquals("boom", persisted.currentPhaseFailure)
    assertNotNull(repository.generateMoreQuestions("p1"))
  }

  @Test
  fun `a round in an older phase never closes the current phase`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to Project(
          id = "p1",
          synopsis = "s",
          editableTitle = "t",
          status = "Draft",
          questions = listOf(question("q1")),
          rounds = listOf(
            round(
              id = "r1",
              phase = BuiltInPhase.ScopeGoals,
              roundNumber = 1,
              outcome = RoundOutcome.Exhausted,
            ),
            round(id = "r2", phase = BuiltInPhase.Design, roundNumber = 2, origin = RoundOrigin.Initial),
          ),
          createdAt = now,
          updatedAt = now,
        )
      )
    )
    val repository = repo(storage = storage)

    // ScopeGoals is exhausted, but Design (the current phase) has only a pending round.
    val persisted = assertNotNull(storage.getProject("p1"))
    assertFalse(persisted.currentPhaseExhausted)
    assertNotNull(repository.generateMoreQuestions("p1"))
  }

  @Test
  fun `initial generation latches its outcome on the shell round`() = runTest {
    val generator = FakePlanningEngine().apply { done = true }
    val storage = FakeStorage()
    val repository = repo(storage = storage, generator = generator)

    val project = repository.createProject("s")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject(project.id))
    assertEquals(RoundOutcome.Exhausted, persisted.rounds.single().outcome)
    assertTrue(persisted.currentPhaseExhausted)
  }

  @Test
  fun `an initial batch with nothing new is a failure on the shell round`() = runTest {
    val storage = FakeStorage()
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = repo(storage = storage, runner = runner)

    val project = repository.createProject("s")
    testScheduler.advanceUntilIdle()

    val persisted = assertNotNull(storage.getProject(project.id))
    assertEquals(RoundOutcome.Failed, persisted.rounds.single().outcome)
    assertTrue(persisted.questions.isEmpty())
    assertEquals(
      TaskStatus.Failed,
      runner.tasks.value.single { it.kind == TaskKind.QuestionGeneration }.status,
    )
  }

  // ---------- advanceToPhase ----------

  @Test
  fun `advanceToPhase completes in-progress rounds and opens an Initial round in the target phase`() =
    runTest {
      val generator = FakePlanningEngine().apply {
        questions += question("n1", "Next?")
        questions += question("n2", "After?")
      }
      val storage = storageWith(
        phaseProject(round(id = "r1", phase = BuiltInPhase.ScopeGoals))
          .copy(questions = listOf(question("q1")))
      )
      val repository = repo(storage = storage, generator = generator)

      val updated = repository.advanceToPhase("p1", BuiltInPhase.Research)

      assertNotNull(updated)
      // The phase swap is immediate — wrapped-up rounds, fresh Initial round...
      assertEquals(listOf("q1"), updated.questions.map { it.id })
      assertEquals(2, updated.rounds.size)
      assertTrue(updated.rounds[0].isCompleted, "the wrapped-up round is marked complete")
      val newRound = updated.rounds.last()
      assertEquals(BuiltInPhase.Research, newRound.phase)
      assertEquals(RoundOrigin.Initial, newRound.origin)
      assertEquals(2, newRound.roundNumber)
      assertEquals("p1", newRound.projectId)
      assertEquals(BuiltInPhase.Research, updated.currentPhase)

      // ...while the fresh batch streams in via the enqueued task.
      testScheduler.advanceUntilIdle()

      val persisted = storage.getProject("p1")
      assertNotNull(persisted)
      assertEquals(setOf("q1", "n1", "n2"), persisted.questions.map { it.id }.toSet())
      assertEquals(2, persisted.rounds.size)
      assertTrue(persisted.rounds[0].isCompleted)
      assertEquals(BuiltInPhase.Research, persisted.rounds.last().phase)
      assertEquals(
        RoundOutcome.MoreAvailable,
        persisted.rounds.last().outcome,
        "moving into a phase opens it: the new round's outcome, not the old phase's, decides",
      )

      val call = generator.calls.single()
      assertEquals(newRound.id, call.roundId)
      assertEquals(BuiltInPhase.Research, call.phase)
      assertEquals("t", call.editableTitle)
      assertEquals("s", call.synopsis)
    }

  @Test
  fun `advanceToPhase completes every in-progress round`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = emptyList(),
      rounds = listOf(
        round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals, roundNumber = 1),
        round(id = "r2", projectId = "p1", phase = BuiltInPhase.ScopeGoals, roundNumber = 2),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val repository = repo(storage = storageWith(original))

    val updated = repository.advanceToPhase("p1", BuiltInPhase.Design)

    assertNotNull(updated)
    assertTrue(updated.rounds.take(2).all { it.isCompleted })
  }

  @Test
  fun `advanceToPhase dedupes new questions against the ones already asked`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("dup", "Already asked?")
      questions += question("n1", "Fresh?")
    }
    val storage = storageWith(
      Project(
        id = "p1",
        synopsis = "s",
        editableTitle = "t",
        status = "Draft",
        questions = listOf(question("q1", "Already asked?")),
        rounds = listOf(
          round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals),
        ),
        createdAt = now,
        updatedAt = now,
      ),
    )
    val repository = repo(storage = storage, generator = generator)

    repository.advanceToPhase("p1", BuiltInPhase.Research)
    testScheduler.advanceUntilIdle()

    val updated = storage.getProject("p1")
    assertNotNull(updated)
    assertEquals(setOf("q1", "n1"), updated.questions.map { it.id }.toSet())
  }

  @Test
  fun `advanceToPhase returns null for a missing project`() = runTest {
    val repository = repo()

    assertNull(repository.advanceToPhase("missing", BuiltInPhase.Research))
  }

  // ---------- revisiting a phase ----------

  private fun revisitedProject(): Project = Project(
    id = "p1",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = listOf(
      question("oldOpen"),
      question("oldAnswered", answers = listOf(answer("oldAnswered", "A", id = "a1"))),
      question("oldIgnored", ignoredAt = now),
    ),
    rounds = listOf(
      round(
        id = "r1",
        projectId = "p1",
        phase = BuiltInPhase.ScopeGoals,
        roundNumber = 1,
        completedAt = now,
      ),
      round(
        id = "r2",
        projectId = "p1",
        phase = BuiltInPhase.Research,
        roundNumber = 2,
        completedAt = now,
      ),
    ),
    createdAt = now,
    updatedAt = now,
  )

  @Test
  fun `advanceToPhase back to a visited phase appends without disturbing existing order`() =
    runTest {
      val generator = FakePlanningEngine().apply {
        questions += question("n1", "Fresh 1?")
        questions += question("n2", "Fresh 2?")
      }
      val storage = storageWith(revisitedProject())
      val repository = repo(storage = storage, generator = generator)

      val updated = repository.advanceToPhase("p1", BuiltInPhase.ScopeGoals)

      assertNotNull(updated)
      assertEquals(BuiltInPhase.ScopeGoals, updated.currentPhase)
      // existing questions keep their slots; the round lands before the batch streams in
      assertEquals(
        listOf("oldOpen", "oldAnswered", "oldIgnored"),
        updated.questions.map { it.id },
      )
      val newRound = updated.rounds.last()
      assertEquals(3, newRound.roundNumber)
      assertEquals(RoundOrigin.Initial, newRound.origin)
      assertEquals(BuiltInPhase.ScopeGoals, newRound.phase)
      assertFalse(newRound.isCompleted)

      testScheduler.advanceUntilIdle()

      val persisted = storage.getProject("p1")
      assertNotNull(persisted)
      // old questions keep their slots (answered/ignored included); new ones simply append
      assertEquals(
        listOf("oldOpen", "oldAnswered", "oldIgnored"),
        persisted.questions.take(3).map { it.id },
      )
      assertEquals(setOf("n1", "n2"), persisted.questions.drop(3).map { it.id }.toSet())
      assertEquals(5, persisted.questions.size)
      assertTrue(persisted.questions[1].isAnswered)
      assertTrue(persisted.questions[2].isIgnored)
      assertEquals(newRound.id, persisted.rounds.last().id)
    }

  @Test
  fun `advanceToPhase into a phase already exhausted by an earlier visit stays exhausted`() =
    runTest {
      val generator = FakePlanningEngine() // no initial questions -> the revisit can't produce anything
      val repositoryStorage = storageWith(revisitedProject())
      val repository = repo(storage = repositoryStorage, generator = generator)

      val updated = repository.advanceToPhase("p1", BuiltInPhase.ScopeGoals)

      assertNotNull(updated)
      assertEquals(listOf("oldOpen", "oldAnswered", "oldIgnored"), updated.questions.map { it.id })
      assertEquals(BuiltInPhase.ScopeGoals, updated.currentPhase)
      assertEquals(3, updated.rounds.size)
      assertTrue(updated.rounds.take(2).all { it.isCompleted })
      assertEquals(RoundOrigin.Initial, updated.rounds.last().origin)

      testScheduler.advanceUntilIdle()
      val persisted = assertNotNull(repositoryStorage.getProject("p1"))
      val revisitRound = persisted.rounds.last()
      // Nothing new to ask while claiming more is available is a dead end, not an exhausted phase.
      // The revisit is the case that motivates one generation method: the engine is
      // handed the whole transcript, so it can see it has nothing new to offer.
      assertEquals(RoundOutcome.Failed, revisitRound.outcome)
      assertFalse(persisted.currentPhaseExhausted)
      assertEquals(BuiltInPhase.ScopeGoals, generator.calls.last().phase)
      // Still retryable, and the retry sees the whole project history, earlier visit included.
      assertNotNull(repository.generateMoreQuestions("p1"))
      testScheduler.advanceUntilIdle()
      assertEquals(
        listOf("oldOpen", "oldAnswered", "oldIgnored"),
        generator.calls.last().previousQuestions.map { it.id },
      )
    }

  @Test
  fun `saveAnswer does not generate a follow-up round in the revisited current phase`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("f1")
    }
    val original = revisitedProject().copy(
      questions = listOf(question("revisitedOpen", roundId = "r3")),
      rounds = revisitedProject().rounds + round(
        id = "r3",
        projectId = "p1",
        phase = BuiltInPhase.ScopeGoals,
        roundNumber = 3,
      ),
    )
    val repository = repo(storage = storageWith(original), generator = generator)

    val updated = repository.saveAnswer("p1", "revisitedOpen", "Answer", completed = true)

    assertNotNull(updated)
    assertTrue(generator.calls.isEmpty())
    assertEquals(3, updated.rounds.size)
    assertEquals(listOf("revisitedOpen"), updated.questions.map { it.id })
  }

  // ---------- ignore / unignore ----------

  @Test
  fun `ignoreQuestion sets ignoredAt`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.ignoreQuestion("p1", "q1")

    assertNotNull(updated)
    assertTrue(updated.questions.single().isIgnored)
  }

  @Test
  fun `unignoreQuestion clears ignoredAt`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", ignoredAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.unignoreQuestion("p1", "q1")

    assertNotNull(updated)
    assertNull(updated.questions.single().ignoredAt)
  }

  // ---------- deleting an answer (a blank draft) ----------

  @Test
  fun `saving a blank draft removes the current answer but keeps history`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "A", id = "7"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer("p1", "q1", "", completed = false)

    assertNotNull(updated)
    val q = updated.questions.single()
    assertNull(q.currentAnswer)
    assertFalse(q.isAnswered)
    assertTrue(q.isUnanswered)
    assertEquals(1, q.answers.size, "the version row stays in immutable history")
  }

  @Test
  fun `saving a blank draft clears a pending draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", draftText = "in progress", draftUpdatedAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer("p1", "q1", "", completed = false)

    assertNotNull(updated)
    val q = updated.questions.single()
    assertNull(q.draftText)
    assertNull(q.draftUpdatedAt)
    assertFalse(q.isDraft)
  }

  // ---------- restoreProject (undo) ----------

  @Test
  fun `restoreProject re-persists a pre-mutation snapshot`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    repository.ignoreQuestion("p1", "q1")
    assertTrue(storage.getProject("p1")!!.questions.single().isIgnored)

    repository.restoreProject(original)

    val restored = storage.getProject("p1")
    assertNotNull(restored)
    assertNull(restored.questions.single().ignoredAt)
    assertEquals(listOf("q1"), restored.questions.map { it.id })
  }

  @Test
  fun `restoreProject re-links a deleted answer`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "A", id = "7"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    repository.saveAnswer("p1", "q1", "", completed = false)
    assertNull(storage.getProject("p1")!!.questions.single().currentAnswer)

    repository.restoreProject(original)

    val restored = storage.getProject("p1")
    assertNotNull(restored)
    assertEquals("A", restored.questions.single().currentAnswer?.text)
  }

  @Test
  fun `restoreProject inserts a project that was missing`() = runTest {
    val project = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val repository = repo()

    repository.restoreProject(project)

    assertEquals(project, repository.getProject(project.id))
  }

  // ---------- exportProject ----------

  @Test
  fun `exportProject renders answered and unanswered questions`() = runTest {
    val project = Project(
      id = "p1",
      synopsis = "Build a thing",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", "What is it?", answers = listOf(answer("q1", "A thing"))),
        question("q2", "When done?"),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val repository = repo()

    val markdown = repository.exportProject(project)

    assertTrue(markdown.contains("# Build a thing"))
    assertTrue(markdown.contains("### Q: What is it?"))
    assertTrue(markdown.contains("| **Answer:** | A thing |"))
    assertTrue(markdown.contains("|**Status:** | unanswered |"))
  }

  // ---------- engines that never read the transcript ----------

  /**
   * The Lite engine serves fixed strings from a pool and composes no prompt, so
   * it has no window to fill: an over-long project is not near any limit, and
   * none of the three choices are on the table.
   */
  @Test
  fun `an engine with no context window is never near the limit`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val repository = repo(
      storage = storage,
      generator = FakePlanningEngine().apply { contextWindowTokens = null },
    )

    val check = repository.checkContext("p1")

    assertNotNull(check)
    assertFalse(check.nearLimit, "there is no window to fill, so there is nothing to ask about")
    assertEquals(0, check.droppableAnswers)
    assertEquals(emptyList(), check.summarizablePhases)
    assertFalse(check.canSummarize, "nothing to summarize, since nothing is read to summarize")
    assertTrue(check.estimatedTokens > 500, "the transcript is still measured; it just doesn't bind")
  }

  /**
   * The other half of the same fact: the generation runs with the project
   * whole. Compacting here would drop answers the engine discards, and would
   * file a "context compacted" note about a loss that never reached a model.
   */
  @Test
  fun `an engine with no context window is handed the project whole`() = runTest {
    val log = RecordingActivityLogger()
    val generator = FakePlanningEngine().apply { contextWindowTokens = null }
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    val repository = repo(
      storage = storage,
      generator = generator,
      settings = settings,
      activityLogger = log,
    )

    repository.generateMoreQuestions("p1")
    testScheduler.advanceUntilIdle()

    val call = generator.calls.single()
    assertTrue(call.previousQuestions.all { it.isAnswered }, "no answer was dropped for nothing")
    assertEquals(emptyList(), call.priorSummaries)
    assertTrue(
      log.entries.none { it.log.startsWith("context compacted") },
      "a compaction the engine never saw is not a compaction",
    )
  }

  /**
   * Even an explicit summarize request falls flat: the engine is handed the
   * project untouched and spends no model call on a summary nobody would read.
   */
  @Test
  fun `an engine with no context window is never asked to summarize`() = runTest {
    val generator = FakePlanningEngine().apply { contextWindowTokens = null }
    val storage = FakeStorage(mutableMapOf("p1" to phasedProject()))
    val settings = SettingsRepository(storage, CoroutineScope(coroutineContext))
    val repository = repo(storage = storage, generator = generator, settings = settings)

    repository.generateMoreQuestions("p1", ContextCompaction.SummarizeEarlierPhases)
    testScheduler.advanceUntilIdle()

    assertEquals(emptyList(), generator.summarizeCalls)
    assertTrue(generator.calls.single().previousQuestions.all { it.isAnswered })
  }
}