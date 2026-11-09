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
  fun `logs progress only the first time overall progress passes each milestone`() = runTest {
    val runner = TaskRunner(backgroundScope, RecordingActivityLogger())
    val log = RecordingActivityLogger()
    val downloader = downloader(
      runner = runner,
      pullEvents = flowOf(
        OllamaPullEvent(digest = "sha256:a", total = 100, completed = 10),
        OllamaPullEvent(digest = "sha256:a", total = 100, completed = 30),
        OllamaPullEvent(digest = "sha256:a", total = 100, completed = 30),
        OllamaPullEvent(digest = "sha256:a", total = 100, completed = 80), // jumps past 50% and 75%
        OllamaPullEvent(digest = "sha256:a", total = 100, completed = 100),
      ),
      activityLogger = log,
    )

    downloader.pullOllama("qwen2.5:7b", "http://localhost:11434")
    testScheduler.runCurrent()

    assertEquals(
      listOf("progress: 25%", "progress: 50%", "progress: 75%"),
      log.entries.map { it.log },
    )
  }

  @Test
  fun `a pull reports smoothed throughput for eta`() = runTest {
    val times = ArrayDeque(listOf(0L, 1_000_000_000L, 2_000_000_000L))
    val runner = TaskRunner(backgroundScope, RecordingActivityLogger())
    val downloader = TaskBackedModelDownloader(
      taskRunner = runner,
      ollamaPullClient = FakeOllamaPullClient(
        flowOf(
          OllamaPullEvent(digest = "sha256:a", total = 400, completed = 0),
          OllamaPullEvent(digest = "sha256:a", total = 400, completed = 100),
          OllamaPullEvent(digest = "sha256:a", total = 400, completed = 400),
        ),
      ),
      activityLogger = RecordingActivityLogger(),
      scope = backgroundScope,
      nanoClock = { times.removeFirst() },
    )

    val handle = downloader.pullOllama("qwen2.5:7b", "http://localhost:11434")
    testScheduler.runCurrent()

    val state = downloader.downloads().value.single { it.handle.id == handle.id }
    assertEquals(400L, state.bytesDone)
    // EMA(alpha=0.3): first usable rate 100 B/s, second instant 300 B/s -> 160 B/s.
    assertEquals(160L, state.throughputBps)
  }

  private fun TestScope.downloader(
    runner: TaskRunner,
    pullEvents: Flow<OllamaPullEvent> = emptyFlow(),
    activityLogger: RecordingActivityLogger = RecordingActivityLogger(),
  ): TaskBackedModelDownloader = TaskBackedModelDownloader(
    taskRunner = runner,
    ollamaPullClient = FakeOllamaPullClient(pullEvents),
    activityLogger = activityLogger,
    scope = backgroundScope,
  )

  private class FakeOllamaPullClient(
    private val events: Flow<OllamaPullEvent>,
  ) : OllamaPullClient {
    override fun pull(baseUrl: String, modelId: String): Flow<OllamaPullEvent> = events
  }
}
