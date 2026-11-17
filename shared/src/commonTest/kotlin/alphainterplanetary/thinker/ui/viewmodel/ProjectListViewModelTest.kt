package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.engine.config.EngineInstance
import alphainterplanetary.thinker.engine.config.EngineRef
import alphainterplanetary.thinker.engine.config.EngineSelectionState
import alphainterplanetary.thinker.engine.core.PlanningEngineSelector
import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.repository.ProjectWrites
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.testutil.FakeExportFileStore
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.testutil.defaultTestInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class ProjectListViewModelTest {

  /** Builds a VM on the test scheduler and guarantees [ProjectListViewModel.close]. */
  private suspend fun TestScope.withViewModel(
    storage: Storage = FakeStorage(),
    projectWrites: ProjectWrites = ProjectWrites(),
    block: suspend (ProjectListViewModel) -> Unit,
  ) {
    val scope = CoroutineScope(coroutineContext)
    val runner = TaskRunner(scope)
    val repository = ProjectRepository(
      storage,
      PlanningEngineSelector { FakePlanningEngine() },
      runner,
      RecordingActivityLogger(),
      FakeExportFileStore(),
      EngineRepository(storage, scope),
    )
    val vm = ProjectListViewModel(
      repository,
      runner,
      projectWrites,
      CoroutineScope(coroutineContext),
    )
    try {
      block(vm)
    } finally {
      vm.close()
    }
  }

  private fun project(id: String = "p1"): Project = Project(
    id = id,
    synopsis = "Synopsis for $id",
    title = "Title $id",
    status = "Draft",
    questions = emptyList(),
    createdAt = defaultTestInstant,
    updatedAt = defaultTestInstant,
  )

  @Test
  fun `loadProjects surfaces stored projects`() = runTest {
    val stored = project("p1")
    withViewModel(FakeStorage(mutableMapOf(stored.id to stored))) { vm ->
      vm.loadProjects()
      testScheduler.advanceUntilIdle()

      assertEquals(listOf(stored), (vm.uiState.value as ProjectListUiState.Success).projects)
    }
  }

  @Test
  fun `createProject surfaces the created project and reloads the list`() = runTest {
    withViewModel { vm ->
      vm.createProject("My synopsis", title = null)
      testScheduler.advanceUntilIdle()

      val created = vm.createdProject.value
      assertEquals("My synopsis", created?.synopsis)
      val list = (vm.uiState.value as ProjectListUiState.Success).projects
      assertEquals(listOf(created?.id), list.map { it.id })
    }
  }

  @Test
  fun `createProject failure surfaces an error state`() = runTest {
    withViewModel(FailingStorage) { vm ->
      vm.createProject("My synopsis", title = null)
      testScheduler.advanceUntilIdle()

      val state = vm.uiState.value as ProjectListUiState.Error
      assertTrue(state.message.contains("Failed to create project"))
    }
  }

  @Test
  fun `deleteProject removes the project from the list`() = runTest {
    val stored = project("p1")
    withViewModel(FakeStorage(mutableMapOf(stored.id to stored))) { vm ->
      vm.loadProjects()
      testScheduler.advanceUntilIdle()
      assertEquals(1, (vm.uiState.value as ProjectListUiState.Success).projects.size)

      vm.deleteProject(stored.id)
      testScheduler.advanceUntilIdle()

      assertEquals(0, (vm.uiState.value as ProjectListUiState.Success).projects.size)
    }
  }

  @Test
  fun `a project write reloads the list already on screen`() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project("p1")))
    val projectWrites = ProjectWrites()
    withViewModel(storage, projectWrites) { vm ->
      vm.loadProjects()
      testScheduler.advanceUntilIdle()
      assertEquals(1, (vm.uiState.value as ProjectListUiState.Success).projects.size)

      // The write is made by something the list cannot see — the header flyout,
      // say — so the list learns of it only from the bus. It must be able to
      // react without naming the writer.
      storage.projects["p2"] = project("p2")
      projectWrites.announce()
      testScheduler.advanceUntilIdle()

      val ids = (vm.uiState.value as ProjectListUiState.Success).projects.map { it.id }
      assertEquals(setOf("p1", "p2"), ids.toSet())
    }
  }

  @Test
  fun `tasks exposes finished tasks so the list can mark failures`() = runTest {
    withViewModel { vm ->
      vm.createProject("My synopsis", title = null)
      testScheduler.advanceUntilIdle()

      // The list chip marks a failure, so finished tasks have to reach the screen
      // too — an active-only feed could never show one.
      assertTrue(
        vm.tasks.value.any { it.kind == TaskKind.TitleRecommendation && it.isFinished },
      )
    }
  }

  private object FailingStorage : Storage {
    override suspend fun saveProject(project: Project) {
      throw IllegalStateException("boom")
    }

    override suspend fun getProject(id: String): Project =
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

    override suspend fun getSetting(key: SettingsKey, default: String): String = default

    override suspend fun saveSetting(key: SettingsKey, value: String) {}

    override suspend fun getEngineInstances(): List<EngineInstance> = emptyList()

    override suspend fun upsertEngineInstance(instance: EngineInstance) {}

    override suspend fun deleteEngineInstance(id: String) {}

    override suspend fun getEngineSelectionStates(): List<EngineSelectionState> = emptyList()

    override suspend fun upsertEngineSelectionState(state: EngineSelectionState) {}

    override suspend fun deleteEngineSelectionState(ref: EngineRef) {}

    override suspend fun setSelectedEngine(ref: EngineRef) {}

    override suspend fun saveExportArtifact(artifact: ExportArtifact) {}

    override suspend fun getExportArtifact(id: String): ExportArtifact? = null

    override fun observeExportArtifacts(projectId: String): Flow<List<ExportArtifact>> =
      kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun getExportArtifacts(projectId: String): List<ExportArtifact> = emptyList()

    override suspend fun deleteExportArtifact(id: String) {}

    override suspend fun saveCachedPhaseSummary(entry: alphainterplanetary.thinker.engine.core.CachedPhaseSummary) {}

    override suspend fun getCachedPhaseSummary(
      projectId: String,
      projectRevisionEpochMs: Long,
      phaseKey: String,
      intent: alphainterplanetary.thinker.engine.core.PhaseSummaryIntent,
      engineIdentity: String,
      coveredContentHash: String,
    ): alphainterplanetary.thinker.engine.core.CachedPhaseSummary? = null

    override suspend fun getCachedPhaseSummariesForRevision(
      projectId: String,
      projectRevisionEpochMs: Long,
      intent: alphainterplanetary.thinker.engine.core.PhaseSummaryIntent,
      engineIdentity: String,
    ): List<alphainterplanetary.thinker.engine.core.CachedPhaseSummary> = emptyList()

    override suspend fun deleteStalePhaseSummaryCache(projectId: String, currentRevisionEpochMs: Long) {}

    override suspend fun deletePhaseSummaryCacheForProject(projectId: String) {}
  }
}
