package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.PlanningEngineSelector
import alphainterplanetary.thinker.engine.SlowDownPlanningEngine
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.repository.ContextCompaction
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.defaultTestInstant
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectDetailViewModelTest {

  private val testInstant: Instant = defaultTestInstant

  private class VmContext(
    val vm: ProjectDetailViewModel,
    val repository: ProjectRepository,
  )

  /** Builds a VM on the test scheduler and guarantees [ProjectDetailViewModel.close]. */
  private suspend fun TestScope.withViewModel(
    storage: FakeStorage = FakeStorage(),
    generator: PlanningEngine = FakePlanningEngine(),
    block: suspend (VmContext) -> Unit,
  ) {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val repository = ProjectRepository(
      storage,
      PlanningEngineSelector { generator },
      runner,
      SettingsRepository(storage, CoroutineScope(coroutineContext)),
      RecordingActivityLogger(),
    )
    val vm = ProjectDetailViewModel(
      repository = repository,
      taskRunner = runner,
      scope = CoroutineScope(coroutineContext),
    )
    try {
      block(VmContext(vm, repository))
    } finally {
      vm.close()
    }
  }

  /** A [PlanningEngine] that holds its follow-up generation for [holdSeconds]. */
  private fun slowFollowUpGenerator(delegate: FakePlanningEngine, holdSeconds: Int): PlanningEngine =
    SlowDownPlanningEngine(
      delegate = delegate,
      config = MutableStateFlow(
        EngineDelayConfig(
          enabled = true,
          secondsByInteraction = mapOf(EngineInteraction.QuestionGeneration to holdSeconds),
        )
      ),
    )

  private fun project(
    id: String = "p1",
    questions: List<alphainterplanetary.thinker.model.Question> = emptyList(),
  ): Project = Project(
    id = id,
    synopsis = "synopsis",
    editableTitle = "Title",
    status = "Draft",
    questions = questions,
    createdAt = testInstant,
    updatedAt = testInstant,
  )

  private fun answeredProject(questionId: String = "q1"): Project =
    project(
      questions = listOf(
        question(
          questionId,
          answers = listOf(answer(questionId, "A", id = "a1"))
        )
      ),
    )

  // ---------- delete answer ----------

  @Test
  fun `deleting an answer applies the optimistic unlink immediately and records a snapshot`() =
    runTest {
      val storage = FakeStorage(mutableMapOf("p1" to answeredProject()))
      withViewModel(storage) { context ->
        val vm = context.vm
        vm.loadProject("p1")
        testScheduler.advanceUntilIdle()

        vm.saveAnswer("p1", "q1", "", completed = false)

        val state = (vm.uiState.value as ProjectDetailUiState.Success)
        assertNull(state.project.questions.single().currentAnswer)
        val pending = vm.pendingUndo.value
        assertNotNull(pending)
        assertEquals("Answer deleted", pending.message)
        assertNotNull(pending.snapshot.questions.single().currentAnswer)

        testScheduler.advanceUntilIdle()
        assertNull((vm.uiState.value as ProjectDetailUiState.Success).project.questions.single().currentAnswer)
      }
    }

  @Test
  fun `undoing a deleted answer restores the pre-delete project in state and storage`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to answeredProject()))
    withViewModel(storage) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.saveAnswer("p1", "q1", "", completed = false)
      testScheduler.advanceUntilIdle()

      val pending = vm.pendingUndo.value
      assertNotNull(pending)
      vm.undo(pending)
      testScheduler.advanceUntilIdle()

      val state = (vm.uiState.value as ProjectDetailUiState.Success)
      assertNotNull(state.project.questions.single().currentAnswer)
      assertNull(state.project.questions.single().draftText)
      assertNull(vm.pendingUndo.value)

      val persisted = storage.projects.getValue("p1")
      assertNotNull(persisted.questions.single().currentAnswer)
    }
  }

  // ---------- ignore / unignore ----------

  @Test
  fun `ignoring a question applies the optimistic state and undo restores it`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project(questions = listOf(question("q1")))))
    withViewModel(storage) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.ignoreQuestion("p1", "q1")

      val ignored = (vm.uiState.value as ProjectDetailUiState.Success).project.questions.single()
      assertNotNull(ignored.ignoredAt)
      val pending = vm.pendingUndo.value
      assertNotNull(pending)
      assertEquals("Question ignored", pending.message)

      testScheduler.advanceUntilIdle()

      vm.undo(pending)
      testScheduler.advanceUntilIdle()

      val restored = (vm.uiState.value as ProjectDetailUiState.Success).project.questions.single()
      assertNull(restored.ignoredAt)
    }
  }

  @Test
  fun `unignoring a question applies the optimistic state and undo restores it`() = runTest {
    val storage = FakeStorage(
      mutableMapOf("p1" to project(questions = listOf(question("q1", ignoredAt = testInstant)))),
    )
    withViewModel(storage) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.unignoreQuestion("p1", "q1")

      val unignored = (vm.uiState.value as ProjectDetailUiState.Success).project.questions.single()
      assertNull(unignored.ignoredAt)
      val pending = vm.pendingUndo.value
      assertNotNull(pending)
      assertEquals("Question restored", pending.message)

      testScheduler.advanceUntilIdle()

      vm.undo(pending)
      testScheduler.advanceUntilIdle()

      val restored = (vm.uiState.value as ProjectDetailUiState.Success).project.questions.single()
      assertNotNull(restored.ignoredAt)
    }
  }

  // ---------- can generate more / advance to phase ----------

  @Test
  fun `Success reports more questions available until a round latches Exhausted`() = runTest {
    val open = project().copy(
      rounds = listOf(
        round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals, outcome = RoundOutcome.MoreAvailable),
      )
    )
    withViewModel(FakeStorage(mutableMapOf("p1" to open))) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      assertTrue((vm.uiState.value as ProjectDetailUiState.Success).canGenerateMoreInPhase)
    }

    val exhausted = open.copy(
      rounds = listOf(
        round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals, outcome = RoundOutcome.Exhausted),
      )
    )
    withViewModel(FakeStorage(mutableMapOf("p1" to exhausted))) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      assertFalse((vm.uiState.value as ProjectDetailUiState.Success).canGenerateMoreInPhase)
    }
  }

  @Test
  fun `a round that could not produce anything new keeps more questions available`() = runTest {
    val failed = project().copy(
      rounds = listOf(
        round(
          id = "r1",
          projectId = "p1",
          phase = BuiltInPhase.ScopeGoals,
          outcome = RoundOutcome.Failed,
          outcomeDetail = "no new questions",
        ),
      )
    )
    withViewModel(FakeStorage(mutableMapOf("p1" to failed))) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      assertTrue((vm.uiState.value as ProjectDetailUiState.Success).canGenerateMoreInPhase)
    }
  }

  @Test
  fun `advanceToPhase moves the project into the chosen phase with a new round`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("n1", "Next?")
    }
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to project(questions = listOf(question("q1"))).copy(
          rounds = listOf(
            round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals),
          ),
        ),
      ),
    )
    withViewModel(storage, generator) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.requestPhaseAdvance("p1", BuiltInPhase.Research)
      testScheduler.advanceUntilIdle()

      val state = vm.uiState.value as ProjectDetailUiState.Success
      assertEquals(BuiltInPhase.Research, state.project.currentPhase)
      assertEquals(listOf("q1", "n1"), state.project.questions.map { it.id })
      assertTrue(state.project.rounds.first().isCompleted)
      assertEquals(BuiltInPhase.Research, state.project.rounds.last().phase)
      assertEquals(generator.calls.single().phase, BuiltInPhase.Research)
    }
  }

  // ---------- the near-limit question ----------

  /**
   * A project well inside its budget is nobody's problem: the request goes
   * straight through and the user is never interrupted.
   */
  @Test
  fun `a project inside the budget asks nothing`() = runTest {
    val storage = FakeStorage(
      mutableMapOf("p1" to project(questions = listOf(question("q1"))).copy(rounds = listOf(round("r1"))))
    )
    withViewModel(storage) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.requestMoreQuestions("p1")
      testScheduler.advanceUntilIdle()

      assertNull(vm.contextPrompt.value, "no dialog for a project that fits")
      assertEquals(2, (vm.uiState.value as ProjectDetailUiState.Success).project.rounds.size)
    }
  }

  /**
   * The point of parking the request: nothing is enqueued until the user has
   * answered, so the choice is made before the round exists rather than after.
   */
  @Test
  fun `an over-long project parks the request until the user answers`() = runTest {
    val generator = budgetedGenerator().apply { canSummarize = true }
    val storage = FakeStorage(mutableMapOf("p1" to overlongProject()))
    withViewModel(storage, generator) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.requestMoreQuestions("p1")
      testScheduler.advanceUntilIdle()

      val prompt = vm.contextPrompt.value
      assertIs<ContextPrompt.MoreQuestions>(prompt)
      assertTrue(prompt.check.nearLimit)
      assertTrue(prompt.check.canSummarize, "the engine can summarize, so the option is offered")
      assertEquals(2, (vm.uiState.value as ProjectDetailUiState.Success).project.rounds.size, "no round yet")
      assertTrue(generator.calls.isEmpty(), "and no generation yet")

      vm.resolveContextPrompt(ContextCompaction.SummarizeEarlierPhases)
      testScheduler.advanceUntilIdle()

      assertNull(vm.contextPrompt.value)
      assertEquals(3, (vm.uiState.value as ProjectDetailUiState.Success).project.rounds.size)
      assertEquals(
        listOf(BuiltInPhase.ScopeGoals),
        generator.summarizeCalls.map { it.phase },
        "the answer is carried all the way into the generation",
      )
    }
  }

  /** Backing out of the dialog is not a decision; nothing happens. */
  @Test
  fun `dismissing the parked request changes nothing`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to overlongProject()))
    withViewModel(storage, budgetedGenerator()) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.requestMoreQuestions("p1")
      testScheduler.advanceUntilIdle()
      vm.dismissContextPrompt()
      testScheduler.advanceUntilIdle()

      assertNull(vm.contextPrompt.value)
      assertEquals(2, (vm.uiState.value as ProjectDetailUiState.Success).project.rounds.size)
    }
  }

  /** The wrap-up is the other door into the same question, and carries the phase. */
  @Test
  fun `a phase advance parks the chosen phase with the request`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to overlongProject()))
    withViewModel(storage, budgetedGenerator()) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.requestPhaseAdvance("p1", BuiltInPhase.Research)
      testScheduler.advanceUntilIdle()

      val prompt = vm.contextPrompt.value
      assertIs<ContextPrompt.PhaseAdvance>(prompt)
      assertEquals(BuiltInPhase.Research, prompt.phase)
      assertEquals(BuiltInPhase.Design, (vm.uiState.value as ProjectDetailUiState.Success).project.currentPhase)

      vm.resolveContextPrompt(ContextCompaction.KeepEverything)
      testScheduler.advanceUntilIdle()

      assertEquals(BuiltInPhase.Research, (vm.uiState.value as ProjectDetailUiState.Success).project.currentPhase)
    }
  }

  /**
   * The Lite engine draws from a fixed pool and has nothing to summarize with,
   * so the dialog offers two ways forward rather than a third that would fail.
   */
  @Test
  fun `the summarize option is withheld from an engine that cannot write summaries`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to overlongProject()))
    withViewModel(storage, budgetedGenerator()) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.requestMoreQuestions("p1")
      testScheduler.advanceUntilIdle()

      val prompt = vm.contextPrompt.value
      assertIs<ContextPrompt.MoreQuestions>(prompt)
      assertTrue(prompt.check.nearLimit)
      assertFalse(prompt.check.canSummarize)
      assertTrue(prompt.check.summarizablePhases.isNotEmpty(), "there is a phase; the engine just can't write it up")
    }
  }

  /**
   * Two phases deep with one very long answer — more than [budgetedGenerator]'s
   * window allows, and short enough that the current phase's answer is still
   * what a trim would keep.
   */
  /**
   * The engine behind the near-limit tests: a window small enough that
   * [overlongProject]'s transcript doesn't fit in the share of it the app fills,
   * which is what puts the project over the budget.
   */
  private fun budgetedGenerator(): FakePlanningEngine =
    FakePlanningEngine().apply { contextWindowTokens = 2000 }

  private fun overlongProject(): Project = Project(
    id = "p1",
    synopsis = "synopsis",
    editableTitle = "Title",
    status = "Draft",
    questions = listOf(
      question(
        "q1",
        "An early question?",
        answers = listOf(answer("q1", "word ".repeat(2000), id = "a1")),
        roundId = "r1",
      ),
      question(
        "q2",
        "A live question?",
        answers = listOf(answer("q2", "short", id = "a2")),
        roundId = "r2",
      ),
    ),
    rounds = listOf(
      round("r1", phase = BuiltInPhase.ScopeGoals, completedAt = testInstant),
      round("r2", phase = BuiltInPhase.Design),
    ),
    createdAt = testInstant,
    updatedAt = testInstant,
  )

  // ---------- token semantics ----------

  @Test
  fun `a newer undoable action supersedes the previously pending undo`() = runTest {
    val storage = FakeStorage(
      mutableMapOf(
        "p1" to project(
          questions = listOf(
            question("q1"),
            question("q2"),
          ),
        ),
      ),
    )
    withViewModel(storage) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.ignoreQuestion("p1", "q1")
      val first = vm.pendingUndo.value
      assertNotNull(first)

      vm.unignoreQuestion("p1", "q2")
      val second = vm.pendingUndo.value
      assertNotNull(second)
      assertEquals(first.token + 1, second.token)

      vm.undo(first)
      testScheduler.advanceUntilIdle()

      val questions = (vm.uiState.value as ProjectDetailUiState.Success).project.questions
      assertNotNull(questions.find { it.id == "q1" }?.ignoredAt)
      assertNull(questions.find { it.id == "q2" }?.ignoredAt)
      assertNotNull(vm.pendingUndo.value)
    }
  }

  @Test
  fun `undoing after an already-undone action is a no-op`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project(questions = listOf(question("q1")))))
    withViewModel(storage) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.ignoreQuestion("p1", "q1")
      val pending = vm.pendingUndo.value
      assertNotNull(pending)

      vm.undo(pending)
      testScheduler.advanceUntilIdle()

      vm.undo(pending)
      testScheduler.advanceUntilIdle()

      val restored = (vm.uiState.value as ProjectDetailUiState.Success).project.questions.single()
      assertNull(restored.ignoredAt)
      assertNull(vm.pendingUndo.value)
    }
  }

  // ---------- generation task completion ----------

  @Test
  fun `a completed initial-generation task reloads the loaded project with its questions`() =
    runTest {
      val generator = FakePlanningEngine().apply {
        questions += question("g1", "Generated?")
      }
      withViewModel(generator = generator) { context ->
        val vm = context.vm
        val created = context.repository.createProject("My synopsis")
        vm.loadProject(created.id)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value as ProjectDetailUiState.Success
        assertEquals(listOf("g1"), state.project.questions.map { it.id })
      }
    }

  @Test
  fun `tasks exposes the current project's generation tasks`() = runTest {
    val generator = FakePlanningEngine().apply {
      questions += question("q1", "What?")
    }
    withViewModel(generator = generator) { context ->
      val vm = context.vm
      val created = context.repository.createProject("My synopsis")
      vm.loadProject(created.id)
      testScheduler.advanceUntilIdle()

      val active = vm.tasks.value
      assertEquals(
        listOf(
          TaskKind.TitleRecommendation,
          TaskKind.QuestionGeneration,
        ),
        active.map { it.kind },
      )
      assertTrue(
        active.all { it.status == TaskStatus.Succeeded },
        "the title and the batch both complete",
      )
    }
  }

  // ---------- in-flight generation is surfaced ----------

  @Test
  fun `retryTitle re-runs the recommendation for a project that never got one`() = runTest {
    val fake = FakePlanningEngine().apply { recommendedTitle = "Second Try" }
    val untitled = Project(
      id = "p1",
      synopsis = "synopsis",
      editableTitle = "",
      status = "Draft",
      questions = emptyList(),
      createdAt = testInstant,
      updatedAt = testInstant,
    )
    withViewModel(
      storage = FakeStorage(mutableMapOf("p1" to untitled)),
      generator = SlowDownPlanningEngine(
        delegate = fake,
        config = MutableStateFlow(
          EngineDelayConfig(
            enabled = true,
            secondsByInteraction = mapOf(EngineInteraction.RecommendTitle to 5),
          )
        ),
      ),
    ) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.retryTitle("p1")
      testScheduler.runCurrent()
      assertTrue(
        vm.tasks.value.any { it.kind == TaskKind.TitleRecommendation && !it.isFinished },
        "the retry is an ordinary generation task, so the screen shows it as in flight",
      )

      testScheduler.advanceUntilIdle()

      val state = vm.uiState.value as ProjectDetailUiState.Success
      assertEquals("Second Try", state.project.editableTitle)
    }
  }

  @Test
  fun `generateMoreQuestions runs on the task runner and reloads when it completes`() = runTest {
    val fake = FakePlanningEngine().apply {
      questions += question("n1", "Fresh?")
    }
    withViewModel(
      storage = FakeStorage(mutableMapOf("p1" to project(questions = listOf(question("q1"))))),
      generator = slowFollowUpGenerator(fake, holdSeconds = 5),
    ) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.requestMoreQuestions("p1")
      testScheduler.runCurrent()

      // The round is in place immediately and the task stays observable while it lingers.
      val state = vm.uiState.value as ProjectDetailUiState.Success
      assertEquals(1, state.project.rounds.size)
      assertEquals(listOf("q1"), state.project.questions.map { it.id })
      assertTrue(vm.tasks.value.any { !it.isFinished })

      testScheduler.advanceUntilIdle()

      val after = vm.uiState.value as ProjectDetailUiState.Success
      assertEquals(listOf("q1", "n1"), after.project.questions.map { it.id })
      assertTrue(vm.tasks.value.all { it.isFinished })
    }
  }

  @Test
  fun `entering a project with an extant running task reconnects and reloads on completion`() =
    runTest {
      val fake = FakePlanningEngine().apply {
        questions += question("n1", "Fresh?")
      }
      withViewModel(
        storage = FakeStorage(mutableMapOf("p1" to project(questions = listOf(question("q1"))))),
        generator = slowFollowUpGenerator(fake, holdSeconds = 5),
      ) { context ->
        val vm = context.vm
        // A generation task is already in flight before the screen enters.
        context.repository.generateMoreQuestions("p1")
        testScheduler.runCurrent()
        assertTrue(context.repository.getProject("p1")!!.rounds.size == 1)

        // Entering the project replays the running task instead of waiting on a reload.
        vm.loadProject("p1")
        testScheduler.runCurrent()
        assertEquals(BuiltInPhase.ScopeGoals, (vm.uiState.value as ProjectDetailUiState.Success).project.currentPhase)
        assertTrue(vm.tasks.value.any { it.isActive })

        // Completing the task streams the generated batch into the loaded project.
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value as ProjectDetailUiState.Success
        assertEquals(listOf("q1", "n1"), state.project.questions.map { it.id })
        assertTrue(vm.tasks.value.all { it.isFinished })
      }
    }
}