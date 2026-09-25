package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.activitylog.LogSource
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
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
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
  ): ProjectRepository =
    ProjectRepository(storage, PlanningEngineSelector { generator }, runner)

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
      initialQuestions += question("q1", "First?")
      initialQuestions += question("q2", "Second?")
    }
    val repository = repo(generator = generator)

    repository.createProject("My synopsis")
    testScheduler.advanceUntilIdle()

    assertEquals(1, generator.initialCalls.size)
    val call = generator.initialCalls.single()
    assertEquals("Recommended Title", call.editableTitle)
    assertEquals("My synopsis", call.synopsis)
  }

  @Test
  fun `createProject saves generated questions onto the project via the task`() = runTest {
    val generator = FakePlanningEngine().apply {
      initialQuestions += question("q1")
      initialQuestions += question("q2")
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
      initialQuestions += question("q1")
      initialQuestions += question("q2")
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
    assertEquals(round.id, generator.initialCalls.single().roundId)
    assertEquals(Phase.first, generator.initialCalls.single().phase)
  }

  @Test
  fun `createProject keeps the shell when initial generation fails`() = runTest {
    val failing = object : PlanningEngine {
      override val source: LogSource = LogSource.Lite

      override suspend fun recommendTitle(synopsis: String, activityId: String): String = "Title"

      override suspend fun generateInitialQuestions(
        editableTitle: String,
        synopsis: String,
        roundId: String,
        phase: Phase,
        activityId: String,
      ): QuestionBatch {
        throw PlanningEngine.AnalysisFailure("no model")
      }

      override suspend fun generateFollowUpQuestions(
        synopsis: String,
        previousQuestions: List<Question>,
        roundId: String,
        phase: Phase,
        activityId: String,
      ): QuestionBatch = QuestionBatch(emptyList(), done = true)
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
        initialQuestions += question("qa", "From the enqueued engine?")
      }
      val later = FakePlanningEngine().apply { recommendedTitle = "Latest Engine" }
      var current: PlanningEngine = enqueued
      val runner = TaskRunner(CoroutineScope(coroutineContext))
      val storage = FakeStorage()
      val repository = ProjectRepository(storage, PlanningEngineSelector { current }, runner)

      // createProject enqueues the title + initial batch under `current` (enqueued).
      val project = repository.createProject("My synopsis")
      // The engine setting changes before the queue drains.
      current = later
      testScheduler.advanceUntilIdle()

      val persisted = storage.getProject(project.id)
      assertNotNull(persisted)
      assertEquals("Enqueued Engine", persisted.editableTitle)
      assertEquals(listOf("qa"), persisted.questions.map { it.id })
      assertEquals(1, enqueued.initialCalls.size)
      assertTrue(later.initialCalls.isEmpty(), "the later engine never touches the locked task")
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
    val repository = repo()

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
      followUpQuestions += question("f1")
      followUpQuestions += question("f2")
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
    assertTrue(generator.followUpCalls.isEmpty())
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
    assertTrue(generator.followUpCalls.isEmpty())
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
    assertTrue(generator.followUpCalls.isEmpty())
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
        followUpQuestions += question("f1")
      }
      val storage = FakeStorage(
        mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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
      assertEquals(round.id, generator.followUpCalls.single().roundId)
      assertEquals(BuiltInPhase.Design, generator.followUpCalls.single().phase)
      assertEquals(listOf("q1"), generator.followUpCalls.single().previousQuestions.map { it.id })
    }

  @Test
  fun `generateMoreQuestions does not start a round when the phase is exhausted`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to phaseProject(
          phase = BuiltInPhase.ScopeGoals,
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
    assertEquals(0, generator.followUpCalls.size, "an exhausted phase never calls the engine")
  }

  // ---------- round outcomes ----------

  private fun storageWith(project: Project): FakeStorage =
    FakeStorage(mutableMapOf(project.id to project))

  /**
   * A project sitting in [phase] after one round that already produced
   * [q1], so follow-up generation has a round to latch its outcome onto.
   */
  private fun phaseProject(phase: BuiltInPhase, round: Round): Project = Project(
    id = "p1",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = listOf(question("q1")),
    rounds = listOf(round),
    createdAt = now,
    updatedAt = now,
  )

  @Test
  fun `questions with done false latch MoreAvailable and leave the phase open`() = runTest {
    val generator = FakePlanningEngine().apply {
      followUpQuestions += question("f1")
      followUpDone = false
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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
      followUpQuestions += question("f1")
      followUpDone = true
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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
  fun `an empty batch with done true latches Exhausted, not Failed`() = runTest {
    val generator = FakePlanningEngine().apply { followUpDone = true }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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
      mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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

    val task = runner.tasks.value.single { it.kind == TaskKind.FollowUpQuestions }
    assertEquals(TaskStatus.Failed, task.status)
    assertEquals(persisted.currentPhaseFailure, task.error)
  }

  @Test
  fun `a batch of only already-asked questions with done false is a failure, not a no-op`() = runTest {
    val generator = FakePlanningEngine().apply {
      followUpQuestions += question("q1")
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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
      runner.tasks.value.single { it.kind == TaskKind.FollowUpQuestions }.status,
    )
  }

  @Test
  fun `an engine failure latches Failed with its message and rethrows to the task`() = runTest {
    val generator = FakePlanningEngine().apply {
      generationFailure = PlanningEngine.AnalysisFailure("the model refused")
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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
      runner.tasks.value.single { it.kind == TaskKind.FollowUpQuestions }.error,
    )
  }

  @Test
  fun `a cancelled generation leaves the round Pending rather than Failed`() = runTest {
    val generator = FakePlanningEngine().apply {
      generationFailure = CancellationException("cancelled")
    }
    val storage = FakeStorage(
      mutableMapOf("p1" to phaseProject(BuiltInPhase.Design, round("r1", phase = BuiltInPhase.Design)))
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
  fun `the newest round in the phase decides exhaustion, and only that round`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to phaseProject(
          phase = BuiltInPhase.Design,
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
    assertEquals("boom", persisted.currentPhaseFailure?.detail)
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
            round(id = "r2", phase = BuiltInPhase.Design, roundNumber = 1, origin = RoundOrigin.Initial),
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
    val generator = FakePlanningEngine().apply { initialDone = true }
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
      runner.tasks.value.single { it.kind == TaskKind.InitialQuestions }.status,
    )
  }

  // ---------- advanceToPhase ----------

  @Test
  fun `advanceToPhase completes in-progress rounds and opens an Initial round in the target phase`() =
    runTest {
      val generator = FakePlanningEngine().apply {
        initialQuestions += question("n1", "Next?")
        initialQuestions += question("n2", "After?")
      }
      val storage = storageWith(
        phaseProject(BuiltInPhase.ScopeGoals, round(id = "r1", phase = BuiltInPhase.ScopeGoals))
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

      val call = generator.initialCalls.single()
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
      initialQuestions += question("dup", "Already asked?")
      initialQuestions += question("n1", "Fresh?")
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
        initialQuestions += question("n1", "Fresh 1?")
        initialQuestions += question("n2", "Fresh 2?")
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
      assertEquals(RoundOutcome.Failed, revisitRound.outcome)
      assertFalse(persisted.currentPhaseExhausted)
      assertEquals(BuiltInPhase.ScopeGoals, generator.initialCalls.single().phase)
      // Still retryable, and the retry sees the whole project history, earlier visit included.
      assertNotNull(repository.generateMoreQuestions("p1"))
      testScheduler.advanceUntilIdle()
      assertEquals(
        listOf("oldOpen", "oldAnswered", "oldIgnored"),
        generator.followUpCalls.single().previousQuestions.map { it.id },
      )
    }

  @Test
  fun `saveAnswer does not generate a follow-up round in the revisited current phase`() = runTest {
    val generator = FakePlanningEngine().apply {
      followUpQuestions += question("f1")
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
    assertTrue(generator.followUpCalls.isEmpty())
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
}