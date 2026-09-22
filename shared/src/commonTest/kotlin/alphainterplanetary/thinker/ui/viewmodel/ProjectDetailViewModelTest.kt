package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.SlowDownPlanningEngine
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
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
import kotlin.test.assertNotNull
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
    val repository = ProjectRepository(storage, generator, runner)
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
          secondsByInteraction = mapOf(EngineInteraction.FollowUpQuestions to holdSeconds),
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
  fun `Success exposes whether the generator can produce more questions`() = runTest {
    val generator = FakePlanningEngine().apply { canProduceMore = true }
    withViewModel(FakeStorage(mutableMapOf("p1" to project())), generator) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      val state = vm.uiState.value as ProjectDetailUiState.Success
      assertTrue(state.canGenerateMoreInPhase)
    }

    withViewModel(FakeStorage(mutableMapOf("p1" to project())), FakePlanningEngine()) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      assertTrue(!(vm.uiState.value as ProjectDetailUiState.Success).canGenerateMoreInPhase)
    }
  }

  @Test
  fun `advanceToPhase moves the project into the chosen phase with a new round`() = runTest {
    val generator = FakePlanningEngine().apply {
      initialQuestions += question("n1", "Next?")
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

      vm.advanceToPhase("p1", BuiltInPhase.Research)
      testScheduler.advanceUntilIdle()

      val state = vm.uiState.value as ProjectDetailUiState.Success
      assertEquals(BuiltInPhase.Research, state.project.currentPhase)
      assertEquals(listOf("q1", "n1"), state.project.questions.map { it.id })
      assertTrue(state.project.rounds.first().isCompleted)
      assertEquals(BuiltInPhase.Research, state.project.rounds.last().phase)
      assertEquals(generator.initialCalls.single().phase, BuiltInPhase.Research)
    }
  }

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
        initialQuestions += question("g1", "Generated?")
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
    val generator = FakePlanningEngine()
    withViewModel(generator = generator) { context ->
      val vm = context.vm
      val created = context.repository.createProject("My synopsis")
      vm.loadProject(created.id)
      testScheduler.advanceUntilIdle()

      val active = vm.tasks.value
      assertEquals(
        listOf(
          TaskKind.TitleRecommendation,
          TaskKind.InitialQuestions,
          TaskKind.RemainingInPhase,
        ),
        active.map { it.kind },
      )
      assertTrue(
        active.all { it.status == TaskStatus.Succeeded },
        "the title, the batch, and the remaining-in-phase check all complete",
      )
    }
  }

  // ---------- in-flight generation is surfaced ----------

  @Test
  fun `generateMoreQuestions runs on the task runner and reloads when it completes`() = runTest {
    val fake = FakePlanningEngine().apply {
      canProduceMore = true
      followUpQuestions += question("n1", "Fresh?")
    }
    withViewModel(
      storage = FakeStorage(mutableMapOf("p1" to project(questions = listOf(question("q1"))))),
      generator = slowFollowUpGenerator(fake, holdSeconds = 5),
    ) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      vm.generateMoreQuestions("p1")
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
        canProduceMore = true
        followUpQuestions += question("n1", "Fresh?")
      }
      withViewModel(
        storage = FakeStorage(mutableMapOf("p1" to project(questions = listOf(question("q1"))))),
        generator = slowFollowUpGenerator(fake, holdSeconds = 5),
      ) { context ->
        val vm = context.vm
        // Availability gates the affordance; establish it before generating.
        context.repository.ensureFreshRemainingInPhase("p1")
        testScheduler.advanceUntilIdle()
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