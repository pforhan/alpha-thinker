package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.engine.core.PlanningEngineSelector
import alphainterplanetary.thinker.export.PlatformExportActions
import alphainterplanetary.thinker.model.ExportConfig
import alphainterplanetary.thinker.model.ExportStatus
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.FakeExportFileStore
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class ExportRepositoryTest {
  private val now = Clock.System.now()

  private class FakePlatformExportActions : PlatformExportActions {
    var saved: List<String>? = null
    var shared: List<String>? = null
    var opened: List<String>? = null
    var saveResult: String? = "dest:/tmp/Plan.md"

    override suspend fun save(
      filename: String,
      mimeType: String,
      internalPath: String,
      content: String,
    ): String? {
      saved = listOf(filename, mimeType, internalPath, content)
      return saveResult
    }

    override suspend fun share(
      filename: String,
      mimeType: String,
      internalPath: String,
      content: String,
    ): Boolean {
      shared = listOf(filename, mimeType, internalPath, content)
      return true
    }

    override suspend fun open(
      filename: String,
      mimeType: String,
      internalPath: String,
      content: String,
    ): Boolean {
      opened = listOf(filename, mimeType, internalPath, content)
      return true
    }
  }

  private data class Harness(
    val repository: ExportRepository,
    val storage: FakeStorage,
    val fileStore: FakeExportFileStore,
    val platform: FakePlatformExportActions,
    val engine: FakePlanningEngine,
    val runner: TaskRunner,
  )

  private fun TestScope.harness(
    storage: FakeStorage = FakeStorage(),
    engine: FakePlanningEngine = FakePlanningEngine(),
    runner: TaskRunner = TaskRunner(CoroutineScope(coroutineContext)),
    activityLogger: ActivityLogger = RecordingActivityLogger(),
    platform: FakePlatformExportActions = FakePlatformExportActions(),
    fileStore: FakeExportFileStore = FakeExportFileStore(),
  ): Harness = Harness(
    repository = ExportRepository(
      storage = storage,
      engineSelector = PlanningEngineSelector { engine },
      taskRunner = runner,
      activityLogger = activityLogger,
      platformExportActions = platform,
      exportFileStore = fileStore,
      engineRepository = EngineRepository(storage, CoroutineScope(coroutineContext)),
    ),
    storage = storage,
    fileStore = fileStore,
    platform = platform,
    engine = engine,
    runner = runner,
  )

  private fun project(id: String = "p1", title: String = "My Project"): Project =
    Project(
      id = id,
      synopsis = "Synopsis",
      title = title,
      status = "Draft",
      rounds = listOf(round(id = "r1", projectId = id, phase = BuiltInPhase.ScopeGoals)),
      questions = listOf(
        question(
          id = "q1",
          text = "Question?",
          roundId = "r1",
          answers = listOf(answer("q1", "Answer", id = "a1")),
        ),
      ),
      createdAt = now,
      updatedAt = now,
    )

  @Test
  fun generateExport_persistsPendingThenCompletedArtifactWithManagedFile() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project()))
    val h = harness(storage = storage)

    val pending = h.repository.generateExport("p1", ExportConfig(outputName = "Plan", includeSummaries = false))

    assertEquals(ExportStatus.Pending, pending.status)
    assertEquals("Plan.md", pending.filename)
    assertNull(pending.storageRef)

    testScheduler.advanceUntilIdle()

    val completed = storage.getExportArtifact(pending.id)
    assertNotNull(completed)
    assertEquals(ExportStatus.Completed, completed.status)
    assertEquals(h.fileStore.filenameFor(pending.id, "md"), completed.storageRef)
    assertNull(completed.error)
    val content = h.fileStore.readText(completed.storageRef!!)
    assertNotNull(content)
    assertTrue(content.contains("## Scope & Goals"))
    assertTrue(content.contains("Question?"))
    assertEquals(TaskStatus.Succeeded, h.runner.tasks.value.single().status)
  }

  @Test
  fun generateExport_usesUniqueFilenameForCollisions() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project()))
    val h = harness(storage = storage)

    val first = h.repository.generateExport("p1", ExportConfig(outputName = "Plan", includeSummaries = false))
    val second = h.repository.generateExport("p1", ExportConfig(outputName = "Plan", includeSummaries = false))
    testScheduler.advanceUntilIdle()

    assertEquals("Plan.md", first.filename)
    assertEquals("Plan (2).md", second.filename)
    assertNotEquals(first.id, second.id)
    assertNotEquals(
      storage.getExportArtifact(first.id)?.storageRef,
      storage.getExportArtifact(second.id)?.storageRef,
    )
  }

  @Test
  fun generateExport_marksFailedOnRenderFailure() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project(id = "p1")))
    val h = harness(storage = storage)

    val pending = h.repository.generateExport("p1", ExportConfig(outputName = "Broken", includeSummaries = false))
    storage.projects.remove("p1")
    testScheduler.advanceUntilIdle()

    val failed = storage.getExportArtifact(pending.id)
    assertNotNull(failed)
    assertEquals(ExportStatus.Failed, failed.status)
    assertNotNull(failed.error)
    assertNull(failed.storageRef)
    assertTrue(h.fileStore.files.isEmpty())
  }

  @Test
  fun generateExport_succeedsWithoutSummariesWhenEngineCannotSummarize() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project()))
    val engine = FakePlanningEngine(canSummarize = false)
    val h = harness(storage = storage, engine = engine)

    val pending = h.repository.generateExport("p1", ExportConfig(outputName = "NoSummary", includeSummaries = true))
    testScheduler.advanceUntilIdle()

    val completed = storage.getExportArtifact(pending.id)
    assertNotNull(completed)
    assertEquals(ExportStatus.Completed, completed.status)
    assertTrue(engine.summarizeCalls.isEmpty())
    assertTrue(h.repository.readExportContent(pending.id).getOrThrow().isNotBlank())
  }

  @Test
  fun readExportContent_marksCompletedArtifactFailedWhenFileMissing() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project()))
    val h = harness(storage = storage)

    val pending = h.repository.generateExport("p1", ExportConfig(outputName = "Plan", includeSummaries = false))
    testScheduler.advanceUntilIdle()
    val ref = storage.getExportArtifact(pending.id)!!.storageRef!!
    h.fileStore.delete(ref)

    val result = h.repository.readExportContent(pending.id)
    assertTrue(result.isFailure)
    val updated = storage.getExportArtifact(pending.id)
    assertNotNull(updated)
    assertEquals(ExportStatus.Failed, updated.status)
    assertNotNull(updated.error)
  }

  @Test
  fun saveShareOpen_delegateToPlatformWithoutChangingCanonicalRef() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project()))
    val h = harness(storage = storage)

    val pending = h.repository.generateExport("p1", ExportConfig(outputName = "Plan", includeSummaries = false))
    testScheduler.advanceUntilIdle()
    val canonical = storage.getExportArtifact(pending.id)!!.storageRef

    val dest = h.repository.saveExport(pending.id)
    val shared = h.repository.shareExport(pending.id)
    val opened = h.repository.openExport(pending.id)

    assertEquals("dest:/tmp/Plan.md", dest)
    assertTrue(shared)
    assertTrue(opened)
    assertEquals(canonical, storage.getExportArtifact(pending.id)?.storageRef)
    assertEquals("Plan.md", h.platform.saved?.get(0))
    assertEquals("text/markdown", h.platform.saved?.get(1))
    assertEquals(h.fileStore.resolve(canonical!!), h.platform.saved?.get(2))
    assertEquals(h.platform.saved?.get(3), h.platform.shared?.get(3))
  }

  @Test
  fun deleteExport_removesManagedFileAndDbRecord() = runTest {
    val storage = FakeStorage(mutableMapOf("p1" to project()))
    val h = harness(storage = storage)

    val pending = h.repository.generateExport("p1", ExportConfig(outputName = "Plan", includeSummaries = false))
    testScheduler.advanceUntilIdle()
    val ref = storage.getExportArtifact(pending.id)!!.storageRef!!

    val result = h.repository.deleteExport(pending.id)

    assertTrue(result.isSuccess)
    assertNull(storage.getExportArtifact(pending.id))
    assertNull(h.fileStore.readText(ref))
  }

  @Test
  fun generateExport_throwsForMissingProject() = runTest {
    val h = harness(storage = FakeStorage())

    assertFailsWith<IllegalArgumentException> {
      h.repository.generateExport("missing", ExportConfig(outputName = "X"))
    }
  }
}
