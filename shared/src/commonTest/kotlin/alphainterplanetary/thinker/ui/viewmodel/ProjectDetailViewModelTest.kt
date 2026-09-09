package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.database.InMemoryStorage
import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.testutil.FakeGenerator
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.defaultTestInstant
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectDetailViewModelTest {

  private val testInstant: Instant = defaultTestInstant

  private fun TestScope.viewModel(
    storage: InMemoryStorage = InMemoryStorage(),
    generator: FakeGenerator = FakeGenerator(),
  ): ProjectDetailViewModel {
    val repository = ProjectRepository(storage, generator)
    val thinker = ThinkerRepository(
      repository = repository,
      sampleProjectGenerator = SampleProjectGenerator(storage),
      scope = CoroutineScope(coroutineContext),
    )
    return ProjectDetailViewModel(thinker)
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
      questions = listOf(question(questionId, answers = listOf(answer(questionId, "A", id = "a1")))),
    )

  // ---------- delete answer ----------

  @Test
  fun `deleting an answer applies the optimistic unlink immediately and records a snapshot`() = runTest {
    val storage = InMemoryStorage(mutableMapOf("p1" to answeredProject()))
    val vm = viewModel(storage)
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

  @Test
  fun `undoing a deleted answer restores the pre-delete project in state and storage`() = runTest {
    val storage = InMemoryStorage(mutableMapOf("p1" to answeredProject()))
    val vm = viewModel(storage)
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

  // ---------- ignore / unignore ----------

  @Test
  fun `ignoring a question applies the optimistic state and undo restores it`() = runTest {
    val storage = InMemoryStorage(mutableMapOf("p1" to project(questions = listOf(question("q1")))))
    val vm = viewModel(storage)
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

  @Test
  fun `unignoring a question applies the optimistic state and undo restores it`() = runTest {
    val storage = InMemoryStorage(
      mutableMapOf("p1" to project(questions = listOf(question("q1", ignoredAt = testInstant)))),
    )
    val vm = viewModel(storage)
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

  // ---------- token semantics ----------

  @Test
  fun `a newer undoable action supersedes the previously pending undo`() = runTest {
    val storage = InMemoryStorage(
      mutableMapOf(
        "p1" to project(
          questions = listOf(
            question("q1"),
            question("q2"),
          ),
        ),
      ),
    )
    val vm = viewModel(storage)
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

  @Test
  fun `undoing after an already-undone action is a no-op`() = runTest {
    val storage = InMemoryStorage(mutableMapOf("p1" to project(questions = listOf(question("q1")))))
    val vm = viewModel(storage)
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