package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.database.InMemoryStorage
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.testutil.FakeGenerator
import alphainterplanetary.thinker.testutil.defaultTestInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectListViewModelTest {

  private fun TestScope.viewModel(
    storage: Storage = InMemoryStorage(),
  ): ProjectListViewModel {
    val repository = ProjectRepository(storage, FakeGenerator())
    return ProjectListViewModel(repository, scope = CoroutineScope(coroutineContext))
  }

  private fun project(id: String = "p1"): Project = Project(
    id = id,
    synopsis = "Synopsis for $id",
    editableTitle = "Title $id",
    status = "Draft",
    questions = emptyList(),
    createdAt = defaultTestInstant,
    updatedAt = defaultTestInstant,
  )

  @Test
  fun `loadProjects surfaces stored projects`() = runTest {
    val stored = project("p1")
    val vm = viewModel(InMemoryStorage(mutableMapOf(stored.id to stored)))

    vm.loadProjects()
    testScheduler.advanceUntilIdle()

    assertEquals(listOf(stored), (vm.uiState.value as ProjectListUiState.Success).projects)
  }

  @Test
  fun `createProject surfaces the created project and reloads the list`() = runTest {
    val vm = viewModel()

    vm.createProject("My synopsis", title = null)
    testScheduler.advanceUntilIdle()

    val created = vm.createdProject.value
    assertEquals("My synopsis", created?.synopsis)
    val list = (vm.uiState.value as ProjectListUiState.Success).projects
    assertEquals(listOf(created?.id), list.map { it.id })
  }

  @Test
  fun `createProject failure surfaces an error state`() = runTest {
    val vm = viewModel(FailingStorage)

    vm.createProject("My synopsis", title = null)
    testScheduler.advanceUntilIdle()

    val state = vm.uiState.value as ProjectListUiState.Error
    assertTrue(state.message.contains("Failed to create project"))
  }

  @Test
  fun `deleteProject removes the project from the list`() = runTest {
    val stored = project("p1")
    val vm = viewModel(InMemoryStorage(mutableMapOf(stored.id to stored)))

    vm.loadProjects()
    testScheduler.advanceUntilIdle()
    assertEquals(1, (vm.uiState.value as ProjectListUiState.Success).projects.size)

    vm.deleteProject(stored.id)
    testScheduler.advanceUntilIdle()

    assertEquals(0, (vm.uiState.value as ProjectListUiState.Success).projects.size)
  }

  private object FailingStorage : Storage {
    override suspend fun saveProject(project: Project): Project =
      throw IllegalStateException("boom")

    override suspend fun getProject(id: String): Project? =
      throw IllegalStateException("boom")

    override suspend fun getAllProjects(): List<Project> =
      throw IllegalStateException("boom")

    override suspend fun deleteProject(id: String) {
      throw IllegalStateException("boom")
    }

    override suspend fun deleteAllProjects() {
      throw IllegalStateException("boom")
    }

    override suspend fun saveQuestionOrder(projectId: String, order: List<String>) {
      throw IllegalStateException("boom")
    }
  }
}