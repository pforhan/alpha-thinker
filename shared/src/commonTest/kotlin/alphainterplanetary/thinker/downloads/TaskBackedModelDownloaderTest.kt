package alphainterplanetary.thinker.downloads

import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class TaskBackedModelDownloaderTest {

  @Test
  fun `a pull reports progress and succeeds with its task`() = runTest {
    val runner = TaskRunner(backgroundScope, RecordingActivityLogger())
    val downloader = downloader(
      runner = runner,
      pullEvents = flowOf(
        OllamaPullEvent(status = "pulling", total = 100, completed = 25),
        OllamaPullEvent(status = "success", total = 100, completed = 100),
      ),
    )

    val handle = downloader.pullOllama("qwen2.5:7b", "http://localhost:11434")
    testScheduler.runCurrent()

    val state = downloader.downloads().value.single { it.handle.id == handle.id }
    assertEquals(DownloadStatus.Succeeded, state.status)
    assertEquals(DownloadKind.OllamaPull, state.kind)
    assertEquals(100L, state.bytesDone)
    assertEquals(100L, state.bytesTotal)
    assertEquals(1f, state.progress)
    assertEquals("qwen2.5:7b", state.modelId)
    assertEquals("ollama", state.source)
    assertTrue(state.hasProgress())

    // The task carries the same identity, so the task bar and the dialog agree
    // about which model is downloading.
    val task = runner.tasks.value.single { it.id == handle.taskId }
    assertEquals("qwen2.5:7b", task.modelId)
    assertEquals("ollama", task.source)
  }

  @Test
  fun `a pull sums per-layer byte counts into one progress figure`() = runTest {
    // Ollama reports each layer separately and restarts at zero for the next
    // one. Reporting those raw would make the ring snap back to the beginning
    // mid-pull, so the downloader keeps one running total instead.
    val runner = TaskRunner(backgroundScope, RecordingActivityLogger())
    val downloader = downloader(
      runner = runner,
      pullEvents = flowOf(
        OllamaPullEvent(status = "pulling manifest"),
        OllamaPullEvent(digest = "sha256:a", total = 100, completed = 50),
        OllamaPullEvent(digest = "sha256:b", total = 300, completed = 0),
        OllamaPullEvent(digest = "sha256:b", total = 300, completed = 300),
      ),
    )

    val handle = downloader.pullOllama("qwen2.5:7b", "http://localhost:11434")
    testScheduler.runCurrent()

    val state = downloader.downloads().value.single { it.handle.id == handle.id }
    assertEquals(350L, state.bytesDone)
    assertEquals(400L, state.bytesTotal)
    assertEquals(0.875f, state.progress)
  }

  @Test
  fun `a failed pull keeps the servers message on the download`() = runTest {
    val runner = TaskRunner(backgroundScope, RecordingActivityLogger())
    val downloader = downloader(
      runner = runner,
      pullEvents = flow {
        emit(OllamaPullEvent(status = "pulling"))
        throw IllegalStateException("manifest missing")
      },
    )

    val handle = downloader.pullOllama("qwen2.5:7b", "http://localhost:11434")
    testScheduler.runCurrent()

    val state = downloader.downloads().value.single { it.handle.id == handle.id }
    assertEquals(DownloadStatus.Failed, state.status)
    assertEquals("manifest missing", state.message)
    assertEquals(null, state.progress)
  }

  @Test
  fun `a local download reports its bytes and throughput`() = runTest {
    val runner = TaskRunner(backgroundScope, RecordingActivityLogger())
    val downloader = downloader(
      runner = runner,
      transferEvents = flowOf(
        LocalDownloadEvent(bytesDone = 512, bytesTotal = 1024, throughputBps = 2048),
        // A bare terminal phase carries no byte counts; it must not wipe the
        // totals the UI is already showing.
        LocalDownloadEvent(phase = LocalDownloadPhase.Succeeded),
      ),
    )

    val handle = downloader.downloadModel(
      ModelDownloadSpec(modelId = "litert:1b", url = "https://example.test/model.bin"),
    )
    testScheduler.runCurrent()

    val state = downloader.downloads().value.single { it.handle.id == handle.id }
    assertEquals(DownloadStatus.Succeeded, state.status)
    assertEquals(DownloadKind.LocalFile, state.kind)
    assertEquals(512L, state.bytesDone)
    assertEquals(1024L, state.bytesTotal)
    assertEquals(2048L, state.throughputBps)
    assertEquals(0.5f, state.progress)
    assertEquals("litert:1b", state.modelId)
    assertEquals("litert", state.source)
  }

  private fun TestScope.downloader(
    runner: TaskRunner,
    pullEvents: Flow<OllamaPullEvent> = emptyFlow(),
    transferEvents: Flow<LocalDownloadEvent> = emptyFlow(),
  ): TaskBackedModelDownloader = TaskBackedModelDownloader(
    taskRunner = runner,
    ollamaPullClient = FakeOllamaPullClient(pullEvents),
    modelFileTransferClient = FakeModelFileTransferClient(transferEvents),
    activityLogger = RecordingActivityLogger(),
    scope = backgroundScope,
  )

  private class FakeOllamaPullClient(
    private val events: Flow<OllamaPullEvent>,
  ) : OllamaPullClient {
    override fun pull(baseUrl: String, modelId: String): Flow<OllamaPullEvent> = events
  }

  /** Local file downloads are not exercised here; the DI stub is not a client. */
  private class FakeModelFileTransferClient(
    private val events: Flow<LocalDownloadEvent>,
  ) : ModelFileTransferClient {
    override fun download(spec: ModelDownloadSpec): Flow<LocalDownloadEvent> = events
  }
}
