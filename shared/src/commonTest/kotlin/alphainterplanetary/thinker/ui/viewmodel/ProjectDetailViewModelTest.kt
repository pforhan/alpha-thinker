package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.FakeGenerator
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.defaultTestInstant
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlinx.coroutines.CoroutineScope
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
    generator: FakeGenerator = FakeGenerator(),
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
    val generator = FakeGenerator().apply { remaining = 3 }
    withViewModel(FakeStorage(mutableMapOf("p1" to project())), generator) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      val state = vm.uiState.value as ProjectDetailUiState.Success
      assertTrue(state.canGenerateMoreQuestions)
    }

    withViewModel(FakeStorage(mutableMapOf("p1" to project())), FakeGenerator()) { context ->
      val vm = context.vm
      vm.loadProject("p1")
      testScheduler.advanceUntilIdle()

      assertTrue(!(vm.uiState.value as ProjectDetailUiState.Success).canGenerateMoreQuestions)
    }
  }

  @Test
  fun `advanceToPhase moves the project into the chosen phase with a new round`() = runTest {
    val generator = FakeGenerator().apply {
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
      val generator = FakeGenerator().apply {
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
    val generator = FakeGenerator()
    withViewModel(generator = generator) { context ->
      val vm = context.vm
      val created = context.repository.createProject("My synopsis")
      vm.loadProject(created.id)
      testScheduler.advanceUntilIdle()

      val active = vm.tasks.value
      assertEquals(1, active.size)
      assertEquals(TaskKind.InitialQuestions, active.single().kind)
      assertEquals(TaskStatus.Succeeded, active.single().status)
    }
  }
}